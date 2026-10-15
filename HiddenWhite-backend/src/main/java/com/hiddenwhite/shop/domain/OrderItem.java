package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;

public record OrderItem(
        String productId,
        String name,
        String size,
        String temperature,
        String milk,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
