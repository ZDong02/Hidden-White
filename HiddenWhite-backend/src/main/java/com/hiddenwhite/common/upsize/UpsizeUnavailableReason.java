package com.hiddenwhite.common.upsize;

/**
 * 本次不能用升杯的原因。
 *
 * <p>用枚举而不是把中文句子塞进接口：点单端要按原因决定卡片文案与置灰样式，
 * 靠字符串匹配文案的话，运营改一个字前端就失效了。
 * 文案由各端自己映射，服务端只给「为什么不行」。
 */
public enum UpsizeUnavailableReason {

    /** 该会员的等级在策略表里查不到（含空等级、被手工改成阶梯外的等级）。 */
    NO_POLICY,

    /** 策略存在但被运营关闭。 */
    DISABLED,

    /** 本月可用次数已用完。 */
    LIMIT_REACHED,

    /** 免费额度用完了，且该等级不支持用积分补足（points_cost = 0）。 */
    NO_POINTS_COST,

    /** 积分余额不足以支付本次升杯。 */
    INSUFFICIENT_POINTS,

    /** 策略里的杯型 label 在 product_options 里找不到（配置漂移）。 */
    SIZE_MISSING,

    /** 本单没有任何一行处在可升的起始杯型（都已经是最大杯）。 */
    NO_ELIGIBLE_ITEM,

    /** 顾客没有勾选使用升杯（结算页开关默认关闭）。 */
    NOT_REQUESTED;

    /** 拒绝对外的稳定标识：前端按它选文案，不解析中文。 */
    public String code() {
        return name();
    }
}
