package com.hiddenwhite.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 下单 / 报价请求。
 *
 * <p>{@code memberId} 仅作兼容保留：<b>服务端一律以 token 解析出的会员为准</b>，
 * 请求体里的值会被忽略（否则可以伪造他人身份下单、冒领首单减免）。
 *
 * @param couponId 要使用的优惠券 id，可为空。是否真的用上由服务端决定：
 *                 与首单礼遇互斥，系统取优惠更大的一边；券不满足门槛会直接拒绝（409），
 *                 而不是「静默不用」——顾客选了券却没减钱，比报错更让人困惑。
 */
public record CreateOrderRequest(
        String memberId,
        @NotEmpty List<@Valid OrderItemRequest> items,
        @Size(max = 200) String note,
        String couponId) {
}
