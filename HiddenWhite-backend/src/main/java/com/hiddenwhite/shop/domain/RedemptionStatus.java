package com.hiddenwhite.shop.domain;

/**
 * 兑换单状态。
 *
 * <p>与 {@code redemptions.status} 的 ENUM 一一对应，不在 SQL 与 Java 里各写一份字面量。
 * 只有 {@link #PENDING} 能推进到其余三态，且每一次推进都用
 * {@code WHERE status='PENDING'} 做条件更新，保证幂等（逾期结算与人工取消不会重复退分）。
 */
public enum RedemptionStatus {

    /** 待核销：积分已扣，凭证有效。 */
    PENDING,

    /** 已核销：到店消费完成。 */
    REDEEMED,

    /** 已取消：会员自助取消，积分已退回。 */
    CANCELLED,

    /** 已过期：超过核销截止时间，积分已自动退回。 */
    EXPIRED;

    /** 是否占用「每人限兑次数」（待核销与已核销都算）。 */
    public boolean countsTowardLimit() {
        return this == PENDING || this == REDEEMED;
    }
}
