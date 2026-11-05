package com.hiddenwhite.common.discount;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 等级折扣策略的纯函数行为测试。
 *
 * <p>重点锁两件容易写错的事：
 * ① 「当天有覆盖用覆盖、没有就用基准」—— 写反了会出现「改了基准价但周三还是旧价」；
 * ② {@code rate = 1.000} 与「关掉开关」在金额上等价，判定必须一致，
 *    否则会出现「显示 9.5 折、实际没减钱」这种最伤信任的 bug。
 */
class LevelDiscountPolicyTest {

    private static LevelDiscountPolicy policy(boolean enabled, String baseRate, Map<Integer, BigDecimal> daily) {
        return new LevelDiscountPolicy("黄金会员", "黄金会员", 1500, enabled,
                new BigDecimal(baseRate), BigDecimal.ZERO, "全场 9.5 折", 2, daily);
    }

    @Test
    void usesBaseRateWhenDayIsNotOverridden() {
        LevelDiscountPolicy policy = policy(true, "0.950", Map.of(6, new BigDecimal("0.880")));

        // 周三（3）没有覆盖 → 用基准 9.5 折
        assertThat(policy.rateOn(3)).isEqualByComparingTo(new BigDecimal("0.950"));
        assertThat(policy.overriddenOn(3)).isFalse();
    }

    @Test
    void dayOverrideWinsOverBaseRate() {
        LevelDiscountPolicy policy = policy(true, "0.950", Map.of(6, new BigDecimal("0.880")));

        // 周六（6）单独配了 8.8 折
        assertThat(policy.rateOn(6)).isEqualByComparingTo(new BigDecimal("0.880"));
        assertThat(policy.overriddenOn(6)).isTrue();
    }

    @Test
    void disabledPolicyNeverDiscounts() {
        LevelDiscountPolicy policy = policy(false, "0.880", Map.of(6, new BigDecimal("0.500")));

        assertThat(policy.activeOn(6)).isFalse();
        assertThat(policy.activeOn(1)).isFalse();
    }

    @Test
    void rateOfOneMeansNoDiscountEvenWhenEnabled() {
        LevelDiscountPolicy policy = policy(true, "1.000", Map.of());

        assertThat(policy.activeOn(1)).isFalse();
        assertThat(policy.activeOn(7)).isFalse();
    }

    @Test
    void rateBelowOneIsActive() {
        LevelDiscountPolicy policy = policy(true, "0.950", Map.of());

        assertThat(policy.activeOn(1)).isTrue();
        assertThat(policy.activeOn(7)).isTrue();
    }

    @Test
    void emptyDailyMapFallsBackToBaseForEveryDay() {
        LevelDiscountPolicy policy = policy(true, "0.920", Map.of());

        for (int weekday = 1; weekday <= 7; weekday++) {
            assertThat(policy.rateOn(weekday)).isEqualByComparingTo(new BigDecimal("0.920"));
        }
    }

    @Test
    void labelConvertsRateToChineseDiscount() {
        // 0.85 → 8.5 折；0.9 → 9 折（末尾零要抹掉，不能显示 9.0 折）
        assertThat(LevelDiscountService.label("黄金会员", new BigDecimal("0.850")))
                .isEqualTo("黄金会员 8.5 折");
        assertThat(LevelDiscountService.label("钻石会员", new BigDecimal("0.900")))
                .isEqualTo("钻石会员 9 折");
        assertThat(LevelDiscountService.label("白银会员", new BigDecimal("0.875")))
                .isEqualTo("白银会员 8.75 折");
    }
}
