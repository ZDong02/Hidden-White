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
 * @param useUpsize 是否使用免费升杯。必须<b>显式勾选</b>：免费额度是稀缺资源（黄金每月 1 次），
 *                  自动消耗会让顾客在不需要大杯的那一单里被悄悄扣掉。报价与下单共用此开关。
 */
public record CreateOrderRequest(
        String memberId,
        @NotEmpty List<@Valid OrderItemRequest> items,
        @Size(max = 200) String note,
        String couponId,
        boolean useUpsize) {

    /**
     * 兼容构造器：不带升杯开关的旧调用方（历史测试、内部工具）默认不用升杯。
     *
     * <p>加它而不是去改所有构造点，是因为 {@code Order} 那个 24 参数的 record 已经证明过
     * 「每加一个字段就改一遍全部构造点」的代价。新字段一律给默认值兜住旧调用。
     */
    public CreateOrderRequest(String memberId, List<OrderItemRequest> items, String note, String couponId) {
        this(memberId, items, note, couponId, false);
    }
}
