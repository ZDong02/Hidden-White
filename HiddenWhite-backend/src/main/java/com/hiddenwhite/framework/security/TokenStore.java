package com.hiddenwhite.framework.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 登录 token 内存存储（替代 Redis 实现）。
 * token 有效期与 JWT 一致，应用重启后需重新登录。
 *
 * <p>过期判定与容量边界统一由 {@link ExpiringStore} 负责（定时清理 + 容量上界），
 * 本类只保留 token 前缀这一层业务语义。
 */
@Service
public class TokenStore {

    private record Entry(String username) { }

    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    /**
     * 同时在线的 token 上限。
     *
     * <p>按「单店管理端 + 点单端会员」的规模，一万个并发会话已远超实际需要；
     * 设上限是为了防止 token 无限累积把堆吃满，而不是限制业务。
     * 超出时淘汰最接近过期的会话，等价于让最早登录的人重新登录。
     */
    private static final int MAX_TOKENS = 10_000;

    private final ExpiringStore<Entry> tokens = new ExpiringStore<>(MAX_TOKENS);

    public void store(String token, String username, long expiresSeconds) {
        tokens.put(TOKEN_KEY_PREFIX + token, new Entry(username), expiresSeconds * 1000);
    }

    public Optional<String> usernameOf(String token) {
        Entry entry = tokens.get(TOKEN_KEY_PREFIX + token);
        return entry == null ? Optional.empty() : Optional.of(entry.username());
    }

    public void revoke(String token) {
        tokens.remove(TOKEN_KEY_PREFIX + token);
    }

    /**
     * 定时清理过期 token。
     *
     * <p>只靠 {@link #usernameOf} 顺手删是不够的：用户关闭页面后再不回来，
     * 该 token 就再也不会被读到，也就永远不会被删。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void purgeExpired() {
        tokens.purgeExpired();
    }
}
