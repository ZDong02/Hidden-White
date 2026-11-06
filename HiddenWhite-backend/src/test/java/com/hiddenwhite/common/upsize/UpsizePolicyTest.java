package com.hiddenwhite.common.upsize;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 升杯额度判定测试。
 *
 * <p>锁住方案 3.4 的判定顺序，尤其是「<b>总次数先于免费次数</b>」这一条：
 * 若先判免费次数，{@code monthly_limit=1, free_quota=3} 这种误配会在第 2 次
 * 冒出「免费可用」而实际已超总次数——运营配错了参数，顾客却拿到了超额权益。
 *
 * <p>另一个重点是两处 {@code -1}（不限）的语义：它必须在<b>任何</b>剩余次数的
 * 计算里都保持「-1」而不是变成 0，否则展示层会把「不限」显示成「已用完」。
 */
class UpsizePolicyTest {

    /** 普通会员：无免费额度，靠积分，不限总次数。 */
    private static UpsizePolicy normal() {
        return new UpsizePolicy("普通会员", "普通会员", -1, 0, 200,
                "标准杯 240 毫升", "大杯 360 毫升", true, "", 1);
    }

    /** 黄金会员：每月 1 次免费，之后 100 分，总次数 6。 */
    private static UpsizePolicy gold() {
        return new UpsizePolicy("黄金会员", "黄金会员", 6, 1, 100,
                "标准杯 240 毫升", "大杯 360 毫升", true, "每月 1 次免费", 3);
    }

    /** 钻石会员：全免不限次。 */
    private static UpsizePolicy diamond() {
        return new UpsizePolicy("钻石会员", "钻石会员", -1, -1, 0,
                "标准杯 240 毫升", "大杯 360 毫升", true, "不限次", 5);
    }

    @Test
    void normalMemberAlwaysPaysPointsAndNeverRunsOut() {
        UpsizePolicy policy = normal();

        // 不限总次数：用到第 50 次仍然是积分
        assertThat(policy.payTypeAt(0)).isEqualTo(UpsizePayType.POINTS);
        assertThat(policy.payTypeAt(50)).isEqualTo(UpsizePayType.POINTS);
        assertThat(policy.pointsCostFor(policy.payTypeAt(50))).isEqualTo(200);
        // 剩余次数保持「不限」，不能被算成 0
        assertThat(policy.remainingAt(50)).isEqualTo(UpsizePolicy.UNLIMITED);
        assertThat(policy.freeLeftAt(50)).isZero();
    }

    @Test
    void goldMemberGetsFirstFreeThenPaysPointsUntilLimit() {
        UpsizePolicy policy = gold();

        assertThat(policy.payTypeAt(0)).isEqualTo(UpsizePayType.FREE);
        assertThat(policy.pointsCostFor(UpsizePayType.FREE)).isZero();

        // 第 2 次起扣积分，一直到第 6 次
        assertThat(policy.payTypeAt(1)).isEqualTo(UpsizePayType.POINTS);
        assertThat(policy.payTypeAt(5)).isEqualTo(UpsizePayType.POINTS);
        assertThat(policy.pointsCostFor(UpsizePayType.POINTS)).isEqualTo(100);

        // 第 7 次超总次数
        assertThat(policy.payTypeAt(6)).isEqualTo(UpsizePayType.UNAVAILABLE);
        assertThat(policy.reasonAt(6)).isEqualTo(UpsizeUnavailableReason.LIMIT_REACHED);
    }

    @Test
    void diamondMemberIsFreeWithoutLimit() {
        UpsizePolicy policy = diamond();

        assertThat(policy.payTypeAt(0)).isEqualTo(UpsizePayType.FREE);
        assertThat(policy.payTypeAt(999)).isEqualTo(UpsizePayType.FREE);
        assertThat(policy.freeLeftAt(999)).isEqualTo(UpsizePolicy.UNLIMITED);
        assertThat(policy.remainingAt(999)).isEqualTo(UpsizePolicy.UNLIMITED);
        assertThat(policy.pointsCostFor(UpsizePayType.FREE)).isZero();
    }

    @Test
    void totalLimitWinsOverFreeQuotaWhenMisconfigured() {
        // 运营把总次数配得比免费次数还小：以总次数为硬上限
        UpsizePolicy misconfigured = new UpsizePolicy("黄金会员", "黄金会员", 1, 3, 100,
                "标准杯 240 毫升", "大杯 360 毫升", true, "", 3);

        assertThat(misconfigured.payTypeAt(0)).isEqualTo(UpsizePayType.FREE);
        assertThat(misconfigured.payTypeAt(1)).isEqualTo(UpsizePayType.UNAVAILABLE);
        assertThat(misconfigured.reasonAt(1)).isEqualTo(UpsizeUnavailableReason.LIMIT_REACHED);
    }

    @Test
    void disabledPolicyIsAlwaysUnavailable() {
        UpsizePolicy disabled = new UpsizePolicy("黄金会员", "黄金会员", 6, 1, 100,
                "标准杯 240 毫升", "大杯 360 毫升", false, "", 3);

        assertThat(disabled.payTypeAt(0)).isEqualTo(UpsizePayType.UNAVAILABLE);
        assertThat(disabled.reasonAt(0)).isEqualTo(UpsizeUnavailableReason.DISABLED);
    }

    @Test
    void noPointsCostMeansUnavailableAfterFreeQuota() {
        // 白银：有总次数但没有积分价，免费用完就是没有
        UpsizePolicy silver = new UpsizePolicy("白银会员", "白银会员", 4, 0, 0,
                "标准杯 240 毫升", "大杯 360 毫升", true, "", 2);

        assertThat(silver.payTypeAt(0)).isEqualTo(UpsizePayType.UNAVAILABLE);
        assertThat(silver.reasonAt(0)).isEqualTo(UpsizeUnavailableReason.NO_POINTS_COST);
    }

    @Test
    void remainingAndFreeLeftNeverGoNegative() {
        UpsizePolicy policy = gold();

        assertThat(policy.remainingAt(99)).isZero();
        assertThat(policy.freeLeftAt(99)).isZero();
    }
}
