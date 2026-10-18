package com.hiddenwhite.shop.pay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 支付开发模式的启动期守卫。
 *
 * <p>存在的唯一理由：{@link PayDevModeProperties#resolve(String)} 只有在
 * {@link OrderService} 真正下单时才会被调用，那是<b>太晚</b>的时机——
 * 服务已经起来、端口已经在监听、可能已经被外部访问，此时才抛异常已经无济于事。
 *
 * <p>因此在应用就绪的瞬间再独立校验一次，把风险拦在「流量进来之前」。
 * 校验失败直接抛异常终止启动进程。
 */
@Component
public class PayDevModeGuard {

    private static final Logger log = LoggerFactory.getLogger(PayDevModeGuard.class);

    private final PayDevModeProperties devMode;
    private final String activeProfile;

    public PayDevModeGuard(PayDevModeProperties devMode,
                           @Value("${spring.profiles.active:default}") String activeProfile) {
        this.devMode = devMode;
        this.activeProfile = activeProfile;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (!devMode.isDevMode()) {
            return;
        }
        // resolve() 在非 dev profile 下会抛出 IllegalStateException，
        // 让 Spring 的启动流程失败，进程直接退出——宁可起不来，也不能带着假支付上线。
        devMode.resolve(activeProfile);
        log.warn("当前激活 profile={}，支付开发模式生效中：下单将直接置为 PAID，不产生真实交易。", activeProfile);
    }
}