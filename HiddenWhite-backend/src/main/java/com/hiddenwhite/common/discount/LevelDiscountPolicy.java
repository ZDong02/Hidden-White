package com.hiddenwhite.common.discount;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 一个会员等级的折扣策略。
 *
 * <p><b>折扣率语义</b>：{@code rate = 折后价 / 原价}。{@code 0.850} 表示 8.5 折，
 * {@code 1.000} 表示不打折。用「比率」而不是「折扣数（8.5）」存库，
 * 是因为所有金额计算都是乘法：{@code 金额 × rate}。中间那层「8.5 / 10」
 * 少写一次就少一次出错的机会；只在展示时换算成「8.5 折」。
 *
 * <p><b>为什么 dailyRates 只存覆盖项</b>：未配置的那天回落到 {@code baseRate}。
 * 若预置 7×N 行，运营改一次基准价就得逐格同步，漏一格就会出现
 * 「周一 9.5 折、周二还是上周的 9.7 折」这种没人能解释的现象。
 *
 * @param dailyRates weekday(1=周一…7=周日) → 当日折扣率；空表示整周都用 baseRate
 */
public record LevelDiscountPolicy(
        String levelKey,
        String levelName,
        int minGrowth,
        boolean enabled,
        BigDecimal baseRate,
        BigDecimal minSpend,
        String note,
        int sortOrder,
        Map<Integer, BigDecimal> dailyRates) {

    public LevelDiscountPolicy {
        dailyRates = dailyRates == null ? Map.of() : Map.copyOf(dailyRates);
    }

    /** 指定星期几的折扣率：有覆盖用覆盖，否则用基准。 */
    public BigDecimal rateOn(int weekday) {
        BigDecimal override = dailyRates.get(weekday);
        return override == null ? baseRate : override;
    }

    /** 该天是否为单独配置（供管理端矩阵区分「继承值」与「显式值」）。 */
    public boolean overriddenOn(int weekday) {
        return dailyRates.containsKey(weekday);
    }

    /**
     * 该天是否真的会打折。
     *
     * <p>三个条件缺一不可：策略启用、折扣率小于 1。{@code rate = 1} 就是不打折，
     * 与「关掉开关」在金额上等价 —— 但语义不同（一个是「这天原价」，一个是「整个等级不参与」），
     * 判定时统一按「不打折」处理即可。
     */
    public boolean activeOn(int weekday) {
        return enabled && rateOn(weekday).compareTo(BigDecimal.ONE) < 0;
    }
}
