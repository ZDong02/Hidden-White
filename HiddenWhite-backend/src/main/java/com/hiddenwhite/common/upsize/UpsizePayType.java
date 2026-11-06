package com.hiddenwhite.common.upsize;

/**
 * 一次升杯的结算方式。
 *
 * <p>{@code FREE} 与 {@code POINTS} 会落库到 {@code member_upsize_records.pay_type}；
 * {@code UNAVAILABLE} 只存在于判定结果里，表示「这次不能用」，不会被持久化。
 */
public enum UpsizePayType {

    /** 命中每月免费额度，不扣积分。 */
    FREE,

    /** 免费额度已用完，按等级价扣积分。 */
    POINTS,

    /** 本次不可用；具体原因见 {@link UpsizeUnavailableReason}。 */
    UNAVAILABLE;

    /** 是否为「会真实占用额度」的可用方式。 */
    public boolean usable() {
        return this != UNAVAILABLE;
    }
}
