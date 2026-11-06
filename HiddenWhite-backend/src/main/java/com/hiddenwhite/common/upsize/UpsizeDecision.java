package com.hiddenwhite.common.upsize;

import java.math.BigDecimal;

/**
 * 一次升杯资格判定的结果。
 *
 * <p>它同时是<b>服务端内部决策</b>与<b>点单端展示数据</b>——两处共用同一个对象，
 * 是为了避免「接口说可用、下单却拒绝」这类两套判断各写一遍造成的不一致。
 *
 * <p>{@code sizeDiff} 是「账面上要显示的商品金额增量」：目标杯型与起始杯型的差价。
 * 它<b>不是</b>顾客要多付的钱——升杯的全部意义就是把这笔差价免掉，
 * 顾客实付与不用升杯时完全相等（见方案 3.5）。
 *
 * @param available    本次是否可用（= {@code payType} 可用 且 积分够付）
 * @param payType      结算方式；不可用时为 {@link UpsizePayType#UNAVAILABLE}
 * @param reason       不可用原因；可用时为 {@code null}
 * @param sizeDiff     目标杯型 − 起始杯型的差价（元）
 * @param pointsCost   本次实际要扣的积分（{@code FREE} 时为 0）
 * @param pointsBalance 会员当前积分余额，供前端提示「还差 X 分」
 * @param freeLeft     本月剩余免费次数；-1 表示不限
 * @param remaining    本月剩余可用总次数；-1 表示不限
 */
public record UpsizeDecision(
        boolean available,
        UpsizePayType payType,
        UpsizeUnavailableReason reason,
        String levelKey,
        String levelName,
        String fromSize,
        String toSize,
        BigDecimal sizeDiff,
        int pointsCost,
        int pointsBalance,
        int freeLeft,
        int remaining,
        String note) {

    /** 不可用：只保留展示所需的等级与杯型信息，金额与积分一律归零。 */
    public static UpsizeDecision unavailable(UpsizeUnavailableReason reason, UpsizePolicy policy,
                                            BigDecimal sizeDiff, int pointsBalance) {
        return new UpsizeDecision(false, UpsizePayType.UNAVAILABLE, reason,
                policy == null ? "" : policy.levelKey(),
                policy == null ? "" : policy.levelName(),
                policy == null ? "" : policy.fromSize(),
                policy == null ? "" : policy.toSize(),
                sizeDiff == null ? BigDecimal.ZERO : sizeDiff,
                0, pointsBalance, 0, 0,
                policy == null ? "" : policy.note());
    }

    /** 顾客没勾选升杯时的占位结果：账单上不出现升杯行，但接口字段保持形状一致。 */
    public static UpsizeDecision skipped() {
        return new UpsizeDecision(false, UpsizePayType.UNAVAILABLE, UpsizeUnavailableReason.NOT_REQUESTED,
                "", "", "", "", BigDecimal.ZERO, 0, 0, 0, 0, "");
    }

    /** 可用：由策略与本月已用次数算出结算方式、剩余次数与本次扣分。 */    public static UpsizeDecision usable(UpsizePolicy policy, int used, BigDecimal sizeDiff, int pointsBalance) {
        UpsizePayType payType = policy.payTypeAt(used);
        int cost = policy.pointsCostFor(payType);
        boolean affordable = pointsBalance >= cost;
        return new UpsizeDecision(
                affordable,
                payType,
                affordable ? null : UpsizeUnavailableReason.INSUFFICIENT_POINTS,
                policy.levelKey(), policy.levelName(),
                policy.fromSize(), policy.toSize(),
                sizeDiff == null ? BigDecimal.ZERO : sizeDiff,
                cost, pointsBalance,
                policy.freeLeftAt(used), policy.remainingAt(used),
                policy.note());
    }

    /** 本次是否真的会改动订单（可用 + 有差价可免）。 */
    public boolean effective() {
        return available && sizeDiff.signum() > 0;
    }
}
