package com.hiddenwhite.framework.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证入口的 IP 限流（固定窗口计数）。
 *
 * <p>四个免登录入口（两端登录、注册、验证码）此前无任何频率约束：
 * 密码可以无限爆破，验证码接口可以无限索取。这里按「IP + 滑动窗口」放行正常运营、
 * 卡死脚本流量；命中上限返回 429，响应体与 {@code R} 同构以便两端统一弹错。
 *
 * <p>客户端 IP 取 {@code getRemoteAddr()}：<b>不解析 X-Forwarded-For</b>——该头可伪造，
 * 直接暴露时攻击者可以随意轮换绕过。部署在反代之后时应配置
 * {@code server.forward-headers-strategy}，让容器代为解析受信的转发头。
 *
 * <p>窗口与上限按「同一 IP 每分钟 30 次」设定：登录页一次冷启动最多 1 次验证码 +
 * 若干次失败重试，余量充足；内存上界 {@value #MAX_TRACKED_IPS} 条，触顶后对陌生 IP
 * 放行（fail-open）——限流是防御加固，不能反过来把正常门店打挂。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    /** 免登录即可抵达、且能被脚本滥用的入口。 */
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/admin/auth/login",
            "/api/admin/auth/captcha"
    );

    private static final int DEFAULT_LIMIT = 30;
    private static final long DEFAULT_WINDOW_MILLIS = 60_000L;

    /** 同一窗口内并存的 IP 数上界；超出即进入 fail-open。 */
    private static final int MAX_TRACKED_IPS = 10_000;

    private final int limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Deque<Long>> hitsByIp = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        this(DEFAULT_LIMIT, DEFAULT_WINDOW_MILLIS);
    }

    RateLimitFilter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!tryAcquire(request.getRemoteAddr(), System.currentTimeMillis())) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(tooManyRequestsBody());
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 滑动窗口计数：窗口内的第 {@code limit + 1} 次请求被拒。包级可见以便单测。 */
    boolean tryAcquire(String ip, long now) {
        if (hitsByIp.size() >= MAX_TRACKED_IPS && !hitsByIp.containsKey(ip)) {
            return true;
        }
        Deque<Long> window = hitsByIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /**
     * 定时回收整窗静默的 IP，避免长期运行下键集只增不减。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void purgeIdleWindows() {
        long cutoff = System.currentTimeMillis() - windowMillis;
        hitsByIp.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast() < cutoff;
            }
        });
    }

    private String tooManyRequestsBody() {
        String traceId = MDC.get("traceId");
        // traceId 由 TraceIdFilter（更早的 order）写入；防御性兜底避免 JSON 里出现字面 null
        String safeTraceId = traceId == null ? "" : traceId;
        return "{\"code\":429,\"message\":\"尝试过于频繁，请稍后再试\",\"data\":null,\"traceId\":\"" + safeTraceId + "\"}";
    }
}
