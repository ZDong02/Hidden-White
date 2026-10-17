package com.hiddenwhite.shop.pay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 微信支付接入配置。
 *
 * <p><b>当前状态</b>：项目使用沙箱环境对接，商户号与密钥尚未持有，
 * 因此 {@link #enabled()} 默认为 false。此时点单端会收到明确的
 * 「支付尚未启用」提示，而不是静默假装下单成功 —— 后者会让顾客
 * 以为付了钱却没收到货，是比报错严重得多的问题。
 *
 * <p>凭据一律从环境变量注入，禁止写入仓库。拿到沙箱凭据后设置：
 * <pre>
 * WECHAT_PAY_ENABLED=true
 * WECHAT_PAY_SANDBOX=true
 * WECHAT_PAY_MCH_ID=1900000109
 * WECHAT_PAY_API_KEY=...
 * WECHAT_PAY_APP_ID=wx...            # 沙箱专用小程序 appid
 * WECHAT_PAY_NOTIFY_URL=https://...   # 必须是 https 且已在小程序后台配置
 * </pre>
 *
 * <p>沙箱与正式环境的差异仅在于 gateway 地址与三组凭据，代码路径完全一致，
 * 因此切环境不需要改动任何业务代码。
 */
@Component
@ConfigurationProperties(prefix = "hiddenwhite.pay")
public class WechatPayProperties {

    /** 是否启用微信支付接入。凭据未就绪时保持 false。 */
    private boolean enabled;

    /** true 走沙箱环境，false 走正式环境。 */
    private boolean sandbox = true;

    /** 商户号。沙箱与正式各自不同。 */
    private String mchId;

    /** API 密钥（沙箱为沙箱密钥）。 */
    private String apiKey;

    /** 小程序 appId。沙箱需使用沙箱专用的小程序。 */
    private String appId;

    /** 支付结果回调地址。必须是 https，且已在小程序后台「业务域名」配置。 */
    private String notifyUrl;

    /** 统一下单的商户订单号前缀。 */
    private String orderPrefix = "HW";

    /**
     * 是否具备可发起支付的完整凭据。
     *
     * <p>只判断 enabled 而不校验凭据完整性是不够的：enabled=true 但密钥为空时，
     * 会在真正下单那一刻才炸出难以定位的异常。这里提前到启动期判断。
     */
    public boolean isUsable() {
        return enabled
                && Optional.ofNullable(mchId).filter(s -> !s.isBlank()).isPresent()
                && Optional.ofNullable(apiKey).filter(s -> !s.isBlank()).isPresent()
                && Optional.ofNullable(appId).filter(s -> !s.isBlank()).isPresent();
    }

    /** 沙箱网关。与正式网关仅地址不同。 */
    public String gateway() {
        return sandbox
                ? "https://api.mch.weixin.qq.com/sandboxnew/pay/unifiedorder"
                : "https://api.mch.weixin.qq.com/pay/unifiedorder";
    }

    /** 商户号。缺凭据时给出可操作的提示而非 NPE。 */
    public String requireMchId() {
        return require(mchId, "WECHAT_PAY_MCH_ID");
    }

    public String requireApiKey() {
        return require(apiKey, "WECHAT_PAY_API_KEY");
    }

    public String requireAppId() {
        return require(appId, "WECHAT_PAY_APP_ID");
    }

    private static String require(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("微信支付配置缺失：" + envName + "（请检查环境变量与 hiddenwhite.pay 配置）");
        }
        return value.trim();
    }

    // ---- getter / setter（Spring Boot 配置绑定需要） ----

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public String getMchId() {
        return mchId;
    }

    public void setMchId(String mchId) {
        this.mchId = mchId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getOrderPrefix() {
        return orderPrefix;
    }

    public void setOrderPrefix(String orderPrefix) {
        this.orderPrefix = orderPrefix;
    }
}