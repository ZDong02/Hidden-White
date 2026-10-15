package com.hiddenwhite.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        String memberId,
        @NotEmpty List<@Valid OrderItemRequest> items,
        @Size(max = 200) String note) {
}
