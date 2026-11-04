package com.hiddenwhite.admin.coupon.domain.dto;

import java.math.BigDecimal;

/**
 * 优惠券配置写入请求。
 *
 * <p>所有字段都是「改完立刻影响所有会员」的量，因此服务端逐个校验而不是只信前端：
 * 前端能拦住明显的空值，但拦不住「把面额写成 600」「把有效期写成 0 天」
 * 这类能让券变成废纸或让门店一天亏穿的输入。
 *
 * @param totalStock 总发行量；null 表示不限量
 */
public record CouponConfigWriteRequest(
        Boolean enabled,
        BigDecimal amount,
        BigDecimal minSpend,
        Integer validDays,
        Integer maxHeld,
        Integer totalStock,
        String title,
        String subtitle,
        String description) {
}
