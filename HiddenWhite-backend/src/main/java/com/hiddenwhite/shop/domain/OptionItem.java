package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;

/** 规格选项完整视图（含管理端所需的主键、类型、排序与启用状态）。 */
public record OptionItem(
        long id,
        String type,
        String label,
        BigDecimal extraPrice,
        int sortOrder,
        boolean enabled) {
}
