package com.hiddenwhite.common.discount;

import java.math.BigDecimal;

/**
 * 订单上的一条优惠明细。
 *
 * <p>{@code type} 是「优惠种类」而不是「优惠来源」：
 * 同一单可能同时有会员折扣与优惠券，账单要能逐行显示、对账要能逐项核对。
 * 新增优惠种类（积分抵现、活动立减）只需要加一个常量，不动表结构。
 *
 * <p>{@code refId} 指向产生这条优惠的对象（券 id 等），用于客诉时反查。
 */
public record DiscountLine(String type, String label, BigDecimal amount, String refId) {

    /** 会员等级折扣 */
    public static final String TYPE_LEVEL = "LEVEL";
    /** 首单礼遇 */
    public static final String TYPE_FIRST_ORDER = "FIRST_ORDER";
    /** 优惠券 */
    public static final String TYPE_COUPON = "COUPON";

    public static DiscountLine level(String label, BigDecimal amount) {
        return new DiscountLine(TYPE_LEVEL, label, amount, null);
    }

    public static DiscountLine firstOrder(String label, BigDecimal amount) {
        return new DiscountLine(TYPE_FIRST_ORDER, label, amount, null);
    }

    public static DiscountLine coupon(String label, BigDecimal amount, String couponId) {
        return new DiscountLine(TYPE_COUPON, label, amount, couponId);
    }
}
