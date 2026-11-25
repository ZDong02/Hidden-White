package com.hiddenwhite.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderItemRequest(
        @NotBlank String productId,
        String size,
        String temperature,
        String milk,
        // 上限与点单端 Stepper 的 max 默认值一致；只设下限时 quantity 取 Integer.MAX_VALUE
        // 会让订单总额溢出 DECIMAL(10,2)、item_count 溢出 int，最终变成 500。
        @Min(1) @Max(999) int quantity) {
}
