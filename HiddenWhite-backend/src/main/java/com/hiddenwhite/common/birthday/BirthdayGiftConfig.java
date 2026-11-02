package com.hiddenwhite.common.birthday;

/**
 * 生日礼遇配置（对应 {@code birthday_gift_config} 单行表）。
 *
 * @param giftType        {@code POINTS} 发放积分（V1）/ {@code DRINK} 生成 0 元兑换单（V2）
 * @param pointsAmount    {@code giftType=POINTS} 时发放的积分
 * @param redeemProductId {@code giftType=DRINK} 时对应的兑换品
 * @param windowMode      {@code MONTH} 生日当月 / {@code AROUND} 生日前后 N 天
 * @param windowDays      {@code AROUND} 模式的半径天数
 */
public record BirthdayGiftConfig(
        boolean enabled,
        String giftType,
        int pointsAmount,
        String redeemProductId,
        String windowMode,
        int windowDays,
        String title,
        String subtitle,
        String description) {

    /** 表还没初始化时的兜底：宁可发 200 积分，也不要因为缺一行配置就整块报错。 */
    public static final BirthdayGiftConfig DEFAULT = new BirthdayGiftConfig(
            true, "POINTS", 200, null, "MONTH", 3,
            "生日礼遇", "生日专属惊喜", "生日当月可领取一次，积分即时到账。");

    /** 配置是否可执行：发放积分必须有正数；赠饮模式必须有兑换品。 */
    public boolean usable() {
        if (!enabled) {
            return false;
        }
        if ("DRINK".equalsIgnoreCase(giftType)) {
            return redeemProductId != null && !redeemProductId.isBlank();
        }
        return pointsAmount > 0;
    }
}
