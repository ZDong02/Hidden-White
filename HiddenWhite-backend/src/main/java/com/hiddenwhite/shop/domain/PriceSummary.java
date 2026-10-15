package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;

public record PriceSummary(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal packagingFee,
        BigDecimal total,
        int count) {
}
