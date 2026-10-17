package com.hiddenwhite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC 配置：跨域白名单。
 *
 * <p><b>dev / prod 差异</b>：
 * <ul>
 *   <li>dev 默认放行本地开发端口（5173 / 8080），便于前后端分离调试；</li>
 *   <li>prod 通过 {@code CORS_ALLOWED_ORIGINS} 显式指定域名白名单，
 *       未配置时退化为「不放行任何跨域来源」，而不是像原先那样无条件放行 {@code *}。</li>
 * </ul>
 * 生产环境请务必设置该环境变量，例如：
 * {@code CORS_ALLOWED_ORIGINS=https://admin.example.com}
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 允许的来源，逗号分隔；为空表示不启用跨域。 */
    private final String[] allowedOrigins;

    public WebConfig(@Value("${hiddenwhite.cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 允许携带凭证时不能使用通配来源，这里按白名单精确匹配，因此可以安全开启
                .allowCredentials(true)
                .maxAge(3600);
    }
}
