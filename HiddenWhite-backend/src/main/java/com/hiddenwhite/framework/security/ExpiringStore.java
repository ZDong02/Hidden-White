package com.hiddenwhite.framework.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带有效期与容量上限的内存键值存储。
 *
 * <p><b>为什么需要它</b>：{@link TokenStore} 与 {@link CaptchaService} 原先各自用裸
 * {@link ConcurrentHashMap} 实现，而且只在「键被再次访问到」时才顺手删过期项 ——
 * 换句话说，<b>没人再来访问的键永远不会被删</b>。
 * 验证码这条路径尤其危险：攻击者批量请求 {@code /api/admin/auth/captcha} 却从不提交，
 * 条目只增不减，进程最终 OOM；登录 token 同理（成员注销后 token 仍留到期）。
 *
 * <p>本类把两处重复的过期判定收敛成一份实现，并补上两个原本缺失的边界：
 * <ul>
 *   <li><b>定时清理</b>：使用方通过 {@code @Scheduled} 调用 {@link #purgeExpired()}；</li>
 *   <li><b>容量硬上界</b>：写入前若已达上限，先清过期，仍超限则按到期时间淘汰
 *       最早到期的一批。淘汰的一定是「最接近过期」的条目，对正常用户影响最小，
 *       同时保证内存占用不会无界增长。</li>
 * </ul>
 *
 * <p>并发模型：所有操作落在 {@link ConcurrentHashMap} 上，单键读写的原子性由它保证；
 * {@link #evictEarliest()} 可能误删刚被替换的条目，因此用
 * {@code remove(key, value)} 的「值匹配才删」语义避免删掉新值。
 */
final class ExpiringStore<V> {

    private record Slot<V>(V value, long expiresAtMillis) { }

    /** 单次淘汰比例：每次腾出 1/10，避免每写入一条就做一次全量排序。 */
    private static final int EVICT_FRACTION = 10;

    private final ConcurrentHashMap<String, Slot<V>> slots = new ConcurrentHashMap<>();
    private final int maxEntries;

    ExpiringStore(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    void put(String key, V value, long ttlMillis) {
        if (key == null) {
            return;
        }
        if (slots.size() >= maxEntries) {
            purgeExpired();
            if (slots.size() >= maxEntries) {
                evictEarliest();
            }
        }
        slots.put(key, new Slot<>(value, System.currentTimeMillis() + ttlMillis));
    }

    /** 读取；键不存在或已过期均返回 {@code null}，过期项顺手移除。 */
    V get(String key) {
        Slot<V> slot = slots.get(key);
        if (slot == null) {
            return null;
        }
        if (slot.expiresAtMillis() < System.currentTimeMillis()) {
            slots.remove(key, slot);
            return null;
        }
        return slot.value();
    }

    /** 取出并移除；已过期的键返回 {@code null}（语义与「一次性使用」的验证码一致）。 */
    V remove(String key) {
        Slot<V> slot = slots.remove(key);
        if (slot == null || slot.expiresAtMillis() < System.currentTimeMillis()) {
            return null;
        }
        return slot.value();
    }

    /**
     * 清掉所有已过期条目。
     *
     * @return 实际清理的条数，便于定时任务按需打日志
     */
    int purgeExpired() {
        long now = System.currentTimeMillis();
        int before = slots.size();
        slots.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
        return before - slots.size();
    }

    int size() {
        return slots.size();
    }

    private void evictEarliest() {
        List<Map.Entry<String, Slot<V>>> ordered = new ArrayList<>(slots.entrySet());
        ordered.sort(Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()));
        int target = Math.max(1, maxEntries / EVICT_FRACTION);
        for (int i = 0; i < target && i < ordered.size(); i++) {
            Map.Entry<String, Slot<V>> entry = ordered.get(i);
            // 值匹配才删：期间若已被新值覆盖，说明刚续过期，不能误删
            slots.remove(entry.getKey(), entry.getValue());
        }
    }
}
