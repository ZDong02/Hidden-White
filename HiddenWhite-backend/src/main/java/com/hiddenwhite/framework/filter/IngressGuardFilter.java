package com.hiddenwhite.framework.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 入口守卫：只接受「网关转发进来」的请求。
 *
 * <p><b>要解决的问题</b>：后端此前监听 {@code 0.0.0.0:8080}，局域网里任何设备
 * （也包括手机里的点单端）都能直接连上它。即便前面放了反向代理，只要端口还活着，
 * 绕过代理直连后端就没有任何限流、没有边缘的路径白名单、也不需要 TLS ——
 * 代理层的所有加固都可以被「换个地址」跳过。
 *
 * <p><b>两层防护</b>：
 * <ol>
 *   <li>网络层：生产 profile 把 {@code server.address} 绑到 {@code 127.0.0.1}
 *       （见 {@code application-prod.yml}），外部连不上；</li>
 *   <li>应用层：本过滤器校验共享密钥头 {@code X-HW-Gateway}，值来自
 *       {@code hiddenwhite.gateway.secret}，由反向代理注入。</li>
 * </ol>
 * 第二层是必要的冗余：反代与应用不同机、容器网络互通、或有人手滑把 bind 改回
 * {@code 0.0.0.0} 时，第一层就失效了，而密钥还在。
 *
 * <p><b>默认不启用</b>：{@code hiddenwhite.gateway.secret} 为空时本过滤器直接放行。
 * 本地开发（H5 经 vite 代理、微信开发者工具直连 8080）照旧，不需要额外配置；
 * 生产环境必须注入该变量，否则等同于没有这一层。
 *
 * <p><b>为什么放行 OPTIONS</b>：CORS 预检不承载业务数据，且浏览器发它时不会带
 * 自定义头。拦掉预检只会让跨域场景整体失败，安全性没有任何收益。
 *
 * <p>Order 取 {@code HIGHEST_PRECEDENCE + 5}：晚于 {@link TraceIdFilter}（{@code + 0}，
 * 保证 403 响应里带 traceId），早于 {@code RateLimitFilter}（{@code + 10}，
 * 非网关流量不该占用限流窗口）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class IngressGuardFilter extends OncePerRequestFilter {

    /** 反向代理注入的密钥头名；nginx 侧配置见 deploy/nginx/hiddenwhite.conf。 */
    public static final String GATEWAY_HEADER = "X-HW-Gateway";

    private final byte[] expected;

    public IngressGuardFilter(@Value("${hiddenwhite.gateway.secret:}") String secret) {
        String trimmed = secret == null ? "" : secret.trim();
        // 空配置 = 不启用。这里存成字节数组，比较时走 MessageDigest.isEqual（定长、不提前返回），
        // 避免用 String.equals 做逐字符比较而泄露密钥前缀。
        this.expected = trimmed.isEmpty() ? null : trimmed.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expected == null || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(GATEWAY_HEADER);
        if (provided != null && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 不暴露「缺头」与「头不对」的差别，也不回显任何期望值：这两者都能帮攻击者确认猜到了东西
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(forbiddenBody());
    }

    /**
     * 403 响应体，与 {@code R} 同构，便于两端统一处理。
     * 手写而非注入 ObjectMapper：过滤器在 MVC 之前执行，不依赖消息转换器是否就绪。
     */
    private String forbiddenBody() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        String safeTraceId = traceId == null ? "" : traceId;
        return "{\"code\":403,\"message\":\"请通过统一入口访问\",\"data\":null,\"traceId\":\""
                + safeTraceId + "\"}";
    }
}
