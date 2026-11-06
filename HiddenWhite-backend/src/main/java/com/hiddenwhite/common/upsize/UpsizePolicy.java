package com.hiddenwhite.common.upsize;

/**
 * 一个会员等级的升杯策略（{@code member_upsize_policies} 的一行）。
 *
 * <p><b>模型</b>：每月「总次数」里包含「免费次数」，免费次数用完后按积分补足。
 * 两个维度分开配，是因为它们回答的是不同问题：
 * <ul>
 *   <li>{@code freeQuota} —— 这个等级每月白送几杯；</li>
 *   <li>{@code pointsCost} —— 白送的用完还想升，一杯值多少积分。</li>
 * </ul>
 *
 * <p><b>无限 {@code -1}</b>：只有两处支持无限——{@code monthlyLimit}（钻石不卡次数）
 * 与 {@code freeQuota}（钻石全免）。不用 999 这类大数，是因为「999 次」和「不限」
 * 在展示上是两句话（「本月还可享 998 次」与「不限次」），拿大数冒充无限早晚露馅。
 *
 * @param levelKey     等级键，与 {@code members.member_level} 一致
 * @param monthlyLimit 每月可用总次数；-1 = 不限。它是硬上限，优先于免费次数判定
 * @param freeQuota    每月免费次数；-1 = 免费不限
 * @param pointsCost   免费额度用完后每次消耗的积分；0 = 不支持积分补足
 * @param fromSize     可升的起始杯型 label
 * @param toSize       升级后的目标杯型 label
 */
public record UpsizePolicy(
        String levelKey,
        String levelName,
        int monthlyLimit,
        int freeQuota,
        int pointsCost,
        String fromSize,
        String toSize,
        boolean enabled,
        String note,
        int sortOrder) {

    /** 「不限次」的表示值。 */
    public static final int UNLIMITED = -1;

    /** 该等级是否根本不能用升杯（策略关闭）。 */
    public boolean available() {
        return enabled;
    }

    /**
     * 在「本月已用 {@code used} 次」的前提下，下一次升杯以什么方式结算。
     *
     * <p>判定顺序（方案的唯一真源，改动前请同步 {@code docs/免费升杯方案.md} 3.4）：
     * <ol>
     *   <li>策略关闭 → 不可用；</li>
     *   <li><b>总次数先判</b>——{@code monthlyLimit} 是硬上限。先判免费次数的话，
     *       {@code limit=1, free=3} 这种误配会在第 2 次冒出「免费可用」却已经超总次数；</li>
     *   <li>免费次数不限或还没用完 → 免费；</li>
     *   <li>否则若配了积分价 → 扣积分；</li>
     *   <li>否则 → 不可用（额度用完且不支持补足）。</li>
     * </ol>
     */
    public UpsizePayType payTypeAt(int used) {
        if (!enabled) {
            return UpsizePayType.UNAVAILABLE;
        }
        if (monthlyLimit != UNLIMITED && used >= monthlyLimit) {
            return UpsizePayType.UNAVAILABLE;
        }
        if (freeQuota == UNLIMITED || used < freeQuota) {
            return UpsizePayType.FREE;
        }
        if (pointsCost > 0) {
            return UpsizePayType.POINTS;
        }
        return UpsizePayType.UNAVAILABLE;
    }

    /** 不可用时对应的原因；可用时返回 {@code null}。 */
    public UpsizeUnavailableReason reasonAt(int used) {
        if (!enabled) {
            return UpsizeUnavailableReason.DISABLED;
        }
        if (monthlyLimit != UNLIMITED && used >= monthlyLimit) {
            return UpsizeUnavailableReason.LIMIT_REACHED;
        }
        if (freeQuota == UNLIMITED || used < freeQuota) {
            return null;
        }
        return pointsCost > 0 ? null : UpsizeUnavailableReason.NO_POINTS_COST;
    }

    /** 本月剩余可用次数；{@link #UNLIMITED} 表示不限。 */
    public int remainingAt(int used) {
        return monthlyLimit == UNLIMITED ? UNLIMITED : Math.max(0, monthlyLimit - used);
    }

    /** 本月剩余免费次数；{@link #UNLIMITED} 表示不限。 */
    public int freeLeftAt(int used) {
        return freeQuota == UNLIMITED ? UNLIMITED : Math.max(0, freeQuota - used);
    }

    /** 本次应扣的积分：只有 {@code POINTS} 方式才扣。 */
    public int pointsCostFor(UpsizePayType payType) {
        return payType == UpsizePayType.POINTS ? Math.max(0, pointsCost) : 0;
    }
}
