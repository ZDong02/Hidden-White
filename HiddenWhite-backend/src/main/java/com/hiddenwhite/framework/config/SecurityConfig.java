package com.hiddenwhite.framework.config;

import com.hiddenwhite.framework.security.JwtAuthenticationFilter;
import com.hiddenwhite.framework.security.RestAccessDeniedHandler;
import com.hiddenwhite.framework.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 安全配置。
 *
 * <p><b>鉴权模型</b>：
 * <ul>
 *   <li>管理端 {@code /api/admin/**}：JWT 登录 + 权限码校验（{@code @RequiresPermission}）；</li>
 *   <li>点单端：<b>浏览类公开</b>（菜单、规格、门店、权益），<b>订单与会员资料需登录</b>；</li>
 *   <li>兜底改为 {@code denyAll()} —— 新增路径若忘记配置，默认拒绝而非默认放行。</li>
 * </ul>
 *
 * <p>会员归属一律由 token 判定（subject 前缀 {@code member:}），不接受客户端传入的 memberId。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(registry -> registry
                        // 探活：点单端用它做后端地址自动探测，必须免登录
                        .requestMatchers(antMatcher("/api/health"), antMatcher("/api/admin/health")).permitAll()
                        // 认证入口：登录 / 注册需免登录；注销需登录才能定位 token
                        .requestMatchers(antMatcher("/api/auth/login"), antMatcher("/api/auth/register")).permitAll()
                        .requestMatchers(antMatcher("/api/admin/auth/captcha"), antMatcher("/api/admin/auth/login")).permitAll()
                        .requestMatchers(antMatcher("/api/auth/logout")).authenticated()

                        // 点单端公开浏览：菜单 / 规格 / 门店 / 权益 / 首页推荐位
                        .requestMatchers(
                                antMatcher("/api/products"),
                                antMatcher("/api/products/*"),
                                antMatcher("/api/products/options"),
                                antMatcher("/api/recommendations"),
                                antMatcher("/api/store"),
                                antMatcher("/api/members/benefits")
                        ).permitAll()

                        // 点单端登录后才能访问：报价（首单减免需按本人订单判定）、订单、会员资料、积分兑换
                        .requestMatchers(
                                antMatcher("/api/cart/**"),
                                antMatcher("/api/orders/**"),
                                antMatcher("/api/redeem/**"),
                                antMatcher("/api/members/**")
                        ).authenticated()

                        // 管理端统一要求登录，细粒度权限码由 @RequiresPermission 把关
                        .requestMatchers(antMatcher("/api/admin/**")).authenticated()

                        // 上传的商品图片：点单端（小程序 / H5）跨源读取，无需登录
                        .requestMatchers(antMatcher("/uploads/**")).permitAll()

                        // API 文档
                        .requestMatchers(
                                antMatcher("/doc.html"),
                                antMatcher("/webjars/**"),
                                antMatcher("/v3/api-docs/**"),
                                antMatcher("/swagger-ui/**"),
                                antMatcher("/swagger-ui.html")
                        ).permitAll()

                        // 兜底拒绝：新增路径忘记配置时不会默认变成公开接口
                        .anyRequest().denyAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
