package com.hiddenwhite.framework.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExpiringStore} 的过期与容量边界。
 *
 * <p>这两条正是改造前缺失的：裸 {@code ConcurrentHashMap} 只在访问时删过期项，
 * 无人访问的键永久驻留；也没有任何容量上界。
 */
class ExpiringStoreTest {

    @Test
    void expiredEntryIsInvisibleAndPurged() throws InterruptedException {
        ExpiringStore<String> store = new ExpiringStore<>(10);
        store.put("k", "v", 1);
        Thread.sleep(20);

        assertThat(store.get("k")).isNull();
        // 已过期但尚未被读到的键同样要能被定时任务回收
        store.put("k2", "v2", 1);
        Thread.sleep(20);
        assertThat(store.purgeExpired()).isEqualTo(1);
        assertThat(store.size()).isZero();
    }

    @Test
    void removeIsOneShotAndExpiredRemoveReturnsNull() throws InterruptedException {
        ExpiringStore<String> store = new ExpiringStore<>(10);
        store.put("captcha", "AB12", 60_000);

        assertThat(store.remove("captcha")).isEqualTo("AB12");
        assertThat(store.remove("captcha")).isNull();

        store.put("stale", "X", 1);
        Thread.sleep(20);
        assertThat(store.remove("stale")).isNull();
    }

    @Test
    void exceedingCapacityEvictsEarliestExpiringFirst() {
        ExpiringStore<String> store = new ExpiringStore<>(10);
        // 到期时间递增：k0 最接近过期，理应最先被淘汰
        for (int i = 0; i < 10; i++) {
            store.put("k" + i, "v" + i, 60_000 + i * 1_000L);
        }
        assertThat(store.size()).isEqualTo(10);

        store.put("newest", "v", 120_000);

        assertThat(store.size()).isEqualTo(10);
        assertThat(store.get("k0")).isNull();
        assertThat(store.get("newest")).isEqualTo("v");
    }
}
