package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;

/**
 * 报价结果。
 *
 * <p>{@code discount} 是<b>总优惠</b>，来源可能是首单礼遇，也可能是优惠券 ——
 * 两者互斥，不叠加（{@link com.hiddenwhite.shop.service.OrderService} 取更大的一边）。
 *
 * <p>{@code discountLabel} 说明这笔优惠叫什么，由服务端给出：
 * 前端把它直接显示在账单行上，不必再根据「有没有用券」反推该写什么文案 ——
 * 那种反推在「选了券但首单更划算、券没被采用」时会写错。
 *
 * @param discountLabel  优惠名称，如「首单礼遇」「每周好券」；无优惠时为空串
 * @param couponId       实际采用的优惠券 id；首单礼遇或无优惠时为 null
 * @param couponDiscount 优惠券实际抵扣金额；未用券为 0
 */
public record PriceSummary(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal packagingFee,
        BigDecimal total,
        int count,
        String discountLabel,
        String couponId,
        BigDecimal couponDiscount) {
}
