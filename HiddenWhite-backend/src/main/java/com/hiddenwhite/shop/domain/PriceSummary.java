package com.hiddenwhite.shop.domain;

import com.hiddenwhite.common.discount.DiscountLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报价结果。
 *
 * <p>{@code discount} 是<b>总优惠</b>，可能由多条明细累加而成（会员折扣 + 优惠券）。
 * 明细在 {@code discounts} 里，账单按它逐行渲染 —— 只给一个总数，
 * 顾客看到「少了 12.8 元」却不知道为什么，客诉时也无法解释。
 *
 * <p>优惠之间的组合规则（判定见 {@link com.hiddenwhite.shop.service.OrderService}）：
 * <ul>
 *   <li>会员折扣 与 首单礼遇 <b>互斥</b>，取优惠更大的一边；</li>
 *   <li>优惠券 <b>叠加</b>在互斥结果之上，门槛按原价判定、抵扣额按折后金额截断。</li>
 * </ul>
 *
 * @param discountLabel  主优惠名称（会员折扣或首单礼遇），无优惠时为空串。
 *                       仅为兼容旧调用方保留，新代码请用 {@code discounts}
 * @param couponId       实际采用的优惠券 id；未用券为 null
 * @param couponDiscount 优惠券实际抵扣金额；未用券为 0
 * @param discounts      优惠明细（按账单展示顺序：身份权益在前，券在后）
 */
public record PriceSummary(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal packagingFee,
        BigDecimal total,
        int count,
        String discountLabel,
        String couponId,
        BigDecimal couponDiscount,
        List<DiscountLine> discounts) {
}
