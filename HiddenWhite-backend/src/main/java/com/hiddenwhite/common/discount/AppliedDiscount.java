package com.hiddenwhite.common.discount;

import java.math.BigDecimal;

/**
 * 一次会员折扣的计算结果。
 *
 * @param levelKey  等级键
 * @param levelName 等级名（展示用）
 * @param rate      当日折扣率 = 折后价 / 原价
 * @param weekday   归属星期（1=周一 … 7=周日），客诉时用来解释「为什么今天这个价」
 * @param amount    折扣金额（已四舍五入到分）
 * @param label     展示文案，如「黄金会员 8.5 折」
 */
public record AppliedDiscount(
        String levelKey,
        String levelName,
        BigDecimal rate,
        int weekday,
        BigDecimal amount,
        String label) {

    /** 转成订单优惠明细的一行。 */
    public DiscountLine toLine() {
        return DiscountLine.level(label, amount);
    }
}
