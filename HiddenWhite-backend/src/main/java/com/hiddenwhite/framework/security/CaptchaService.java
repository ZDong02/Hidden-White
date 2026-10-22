package com.hiddenwhite.framework.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图形验证码服务（内存实现，替代 Redis）。
 * 验证码 5 分钟有效，验证一次后立即失效。
 *
 * <p>过期判定与容量边界统一由 {@link ExpiringStore} 负责。这里的容量上界尤其重要：
 * 验证码是<b>未登录即可申请</b>的资源，条目若可无界增长，伪造请求就能把进程内存打满。
 */
@Service
public class CaptchaService {

    private record Entry(String code) { }

    private static final long EXPIRE_SECONDS = 300;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * 未验证验证码的上限。
     *
     * <p>5 分钟有效期 × 1 万条，需要持续约 33 次/秒 的申请才能触顶，
     * 正常门店运营远达不到；触顶即淘汰最接近过期的条目，保证内存有硬上界。
     */
    private static final int MAX_CAPTCHAS = 10_000;

    private final ExpiringStore<Entry> captchas = new ExpiringStore<>(MAX_CAPTCHAS);

    public Map<String, String> generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        StringBuilder code = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            code.append(CHARS.charAt(ThreadLocalRandom.current().nextInt(CHARS.length())));
        }
        captchas.put(uuid, new Entry(code.toString()), EXPIRE_SECONDS * 1000);
        return Map.of("uuid", uuid, "code", code.toString());
    }

    public boolean verify(String uuid, String code) {
        if (uuid == null || code == null) {
            return false;
        }
        // 取出即失效：同一 uuid 无论校验成功与否都不能二次使用，避免暴力枚举
        Entry entry = captchas.remove(uuid);
        return entry != null && entry.code().equalsIgnoreCase(code);
    }

    /**
     * 定时清理过期验证码。
     *
     * <p>用户拉取验证码后关掉登录页很常见，这些条目不会再被 {@link #verify} 碰到，
     * 只能靠定时任务回收。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void purgeExpired() {
        captchas.purgeExpired();
    }
}
