package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;

public record Product(
        String id,
        String name,
        String subtitle,
        String description,
        String detailDescription,
        BigDecimal price,
        String image,
        String detailImage,
        int category,
        boolean hasMilk) {
}
