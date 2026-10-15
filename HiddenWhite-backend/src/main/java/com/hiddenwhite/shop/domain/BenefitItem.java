package com.hiddenwhite.shop.domain;

/** 会员权益完整视图（含管理端所需的主键、排序与启用状态）。 */
public record BenefitItem(
        long id,
        String icon,
        String title,
        String description,
        int sortOrder,
        boolean enabled) {
}
