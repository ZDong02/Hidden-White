package com.hiddenwhite.framework.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图形验证码服务（内存实现，替代 Redis）。
 * 验证码 5 分钟有效，验证一次后立即失效。
 */
@Service
public class CaptchaService {

    private record Entry(String code, long expiresAtMillis) { }

    private static final long EXPIRE_SECONDS = 300;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ConcurrentHashMap<String, Entry> captchas = new ConcurrentHashMap<>();

    public Map<String, String> generate() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        StringBuilder code = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            code.append(CHARS.charAt(ThreadLocalRandom.current().nextInt(CHARS.length())));
        }
        captchas.put(uuid, new Entry(code.toString(), System.currentTimeMillis() + EXPIRE_SECONDS * 1000));
        return Map.of("uuid", uuid, "code", code.toString());
    }

    public boolean verify(String uuid, String code) {
        if (uuid == null || code == null) return false;
        Entry entry = captchas.remove(uuid);
        return entry != null
                && entry.expiresAtMillis() >= System.currentTimeMillis()
                && entry.code().equalsIgnoreCase(code);
    }
}
