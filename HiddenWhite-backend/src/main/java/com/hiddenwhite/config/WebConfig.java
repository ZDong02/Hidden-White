package com.hiddenwhite.config;

import com.hiddenwhite.common.image.ImageStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC 配置：跨域白名单 + 上传图片的静态资源映射。
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

    /** 上传图片的静态资源映射路径。 */
    private static final String UPLOAD_PATTERN = "/uploads/**";

    /** 上传目录的唯一解析入口：读写两侧共用，避免各算一套路径。 */
    private final ImageStorageService imageStorageService;

    public WebConfig(@Value("${hiddenwhite.cors.allowed-origins:}") String allowedOrigins,
                      ImageStorageService imageStorageService) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        this.imageStorageService = imageStorageService;
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
        // 上传图片由管理端与点单端跨源读取，单独放行同样的来源白名单
        registry.addMapping(UPLOAD_PATTERN)
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * 把 {@code /uploads/**} 映射到本地上传目录。
     *
     * <p>点单端（小程序 / H5）需要直接以 HTTP 地址读取商品图，
     * 因此图片走静态资源而非需要鉴权的业务接口。
     *
     * <p>目录取 {@link ImageStorageService#root()}（已是绝对路径且已规范化），
     * 不再自行拼接配置值：拼接相对路径时 {@code file:./data/uploads/} 会以进程工作目录为基准，
     * 与落盘侧解析出的目录不一定相同，表现为「同一进程里能上传、却读不到」。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(UPLOAD_PATTERN)
                .addResourceLocations(imageStorageService.root().toUri().toString());
    }
}
