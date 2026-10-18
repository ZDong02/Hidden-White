package com.hiddenwhite.shop.pay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 开发模式配置：跳过支付，把订单直接置为已支付。
 *
 * <p><b>用途</b>：项目尚未持有微信支付凭据（见 {@link WechatPayProperties}），
 * 开发阶段却需要跑通「下单 → 制作中 → 已取餐」完整流程。
 * 开启后下单即视为支付成功，不必配置沙箱凭据、不必等超时取消。
 *
 * <p><b>绝不允许进入生产</b>：一旦上线，等于咖啡店免费出杯，且所有订单都会
 * 带着不存在的交易号进入对账与退款流程。因此这里用三重锁：
 * <ol>
 *   <li>开关仅放在 {@code application-dev.yml}，生产配置文件里根本没有这个键；</li>
 *   <li>{@link #resolve()} 在非 dev profile 下<b>启动即抛异常</b>，
 *       宁可服务起不来，也不能带着假支付上生产；</li>
 *   <li>{@link FakeTransactionIds} 生成的交易号有固定的 {@code DEV-} 前缀，
 *       对账时一眼可辨，不会与真实微信交易号混淆。</li>
 * </ol>
 *
 * <p>用法：{@code SPRING_PROFILES_ACTIVE=dev}（默认值）下设
 * {@code hiddenwhite.pay.dev-mode=true}，或直接用环境变量
 * {@code HW_PAY_DEV_MODE=true}。转正式支付时把它关掉即可。
 */
@Component
@ConfigurationProperties(prefix = "hiddenwhite.pay")
public class PayDevModeProperties {

    private static final Logger log = LoggerFactory.getLogger(PayDevModeProperties.class);

    /** 开发模式：跳过真实支付，下单直接置为已支付。生产环境禁止开启。 */
    private boolean devMode;

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    /**
     * 开发模式交易号的固定前缀。
     *
     * <p>用 {@code DEV-} 而非数字，是为了让对账人员一眼识别出「这笔单没走真实支付」，
     * 避免误当作真实微信交易号去发起退款。
     */
    public static final String DEV_TX_PREFIX = "DEV-FAKE-";

    /**
     * 解析当前是否应当以开发模式下单。
     *
     * @param activeProfile 当前激活的 Spring profile
     * @return true 表示应跳过真实支付
     * @throws IllegalStateException非 dev profile 下开启时抛出，直接阻断启动
     */
    public boolean resolve(String activeProfile) {
        if (!devMode) {
            return false;
        }
        if (!isDevOnly(activeProfile)) {
            throw new IllegalStateException(
                    "支付开发模式（hiddenwhite.pay.dev-mode=true）只允许在 dev profile 下开启，"
                            + "当前 profile 为 '" + activeProfile + "'。"
                            + "生产环境开启它会导致订单免费出杯并污染对账数据，已阻断启动。"
                            + "请设置 hiddenwhite.pay.dev-mode=false 或切换到 dev profile。");
        }
        log.warn("⚠️ 支付开发模式已开启：下单将直接标记为已支付，不产生任何真实交易。"
                + "严禁用于生产环境！");
        return true;
    }

    /**
     * 判断激活的 profile 是否<b>恰好</b>只有 dev。
     *
     * <p>刻意不接受 {@code "dev,prod"} 这类混合：一旦 prod 在列表里，
     * 就说明这次启动是要跑生产的，绝不能因为dev 也在就放行。
     * 默认 profile 为 {@code default}（既非 dev 也非 prod），同样拒绝。
     */
    private boolean isDevOnly(String activeProfile) {
        if (activeProfile == null || activeProfile.isBlank()) {
            return false;
        }
        String[] parts = activeProfile.split(",");
        return parts.length == 1 && "dev".equals(parts[0].trim());
    }
}