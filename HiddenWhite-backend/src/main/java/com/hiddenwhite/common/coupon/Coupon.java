package com.hiddenwhite.common.coupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一张已发到会员手里的券（{@code member_coupons} 的一行）。
 *
 * <p>面额、门槛、标题都是<b>领取时的快照</b>，不随配置变化 ——
 * 详见 {@link CouponConfig} 的说明。{@code weekKey} 是领取归属自然周的周一日期，
 * 与 {@code member_id} 组成唯一键，这既是幂等锁也是「本周领没领过」的查询依据。
 *
 * @param id             券 id（UUID）
 * @param memberId       持有人
 * @param weekKey        领取归属自然周（周一日期 yyyy-MM-dd）
 * @param title          券标题快照
 * @param amount         面额快照（元）
 * @param minSpend       门槛快照（元）
 * @param status         状态
 * @param claimedAt      领取时刻
 * @param expiresAt      失效时刻（领取时刻 + validDays 天）
 * @param usedAt         核销时刻；未使用为 null
 * @param orderId        核销到哪笔订单；未使用为 null
 * @param discountAmount 实际抵扣金额（可能因订单金额小于面额而被截断）
 */
public record Coupon(
        String id,
        String memberId,
        String weekKey,
        String title,
        BigDecimal amount,
        BigDecimal minSpend,
        CouponStatus status,
        LocalDateTime claimedAt,
        LocalDateTime expiresAt,
        LocalDateTime usedAt,
        String orderId,
        BigDecimal discountAmount) {

    /** 给定订单商品金额下，这张券实际能减多少（不超过订单金额本身）。 */
    public BigDecimal discountFor(BigDecimal subtotal) {
        return amount.min(subtotal);
    }

    /** 是否满足门槛。 */
    public boolean meetsThreshold(BigDecimal subtotal) {
        return subtotal.compareTo(minSpend) >= 0;
    }

    /** 扣减后的应付合计，最小为 0，不出现负数订单。 */
    public BigDecimal applyTo(BigDecimal subtotal) {
        BigDecimal discounted = subtotal.subtract(discountFor(subtotal));
        return discounted.signum() < 0 ? BigDecimal.ZERO : discounted;
    }
}
