package com.hiddenwhite.framework.security;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录 token 内存存储（替代 Redis 实现）。
 * token 有效期与 JWT 一致，应用重启后需重新登录。
 */
@Service
public class TokenStore {

    private record Entry(String username, long expiresAtMillis) { }

    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    private final ConcurrentHashMap<String, Entry> tokens = new ConcurrentHashMap<>();

    public void store(String token, String username, long expiresSeconds) {
        tokens.put(TOKEN_KEY_PREFIX + token, new Entry(username, System.currentTimeMillis() + expiresSeconds * 1000));
    }

    public Optional<String> usernameOf(String token) {
        Entry entry = tokens.get(TOKEN_KEY_PREFIX + token);
        if (entry == null) return Optional.empty();
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            tokens.remove(TOKEN_KEY_PREFIX + token);
            return Optional.empty();
        }
        return Optional.of(entry.username());
    }

    public void revoke(String token) {
        tokens.remove(TOKEN_KEY_PREFIX + token);
    }
}
