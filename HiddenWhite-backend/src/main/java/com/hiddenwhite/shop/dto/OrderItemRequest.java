package com.hiddenwhite.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderItemRequest(
        @NotBlank String productId,
        String size,
        String temperature,
        String milk,
        @Min(1) int quantity) {
}
