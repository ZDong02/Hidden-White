package com.hiddenwhite.shop.domain;

public record Member(
        String id,
        String displayName,
        String phone,
        String level,
        String cardNumber,
        int points,
        int coupons,
        int growthValue) {
}
