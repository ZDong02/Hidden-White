package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Order(
        UUID id,
        String memberId,
        List<OrderItem> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal packagingFee,
        BigDecimal total,
        int count,
        String pickup,
        String number,
        OffsetDateTime createdAt,
        OffsetDateTime readyAt,
        OrderStatus status,
        String note,
        OffsetDateTime completedAt) {
}
