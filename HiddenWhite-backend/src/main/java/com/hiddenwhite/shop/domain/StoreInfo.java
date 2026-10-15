package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;

/** 门店信息（含派生的营业状态与打烊提示语），点单端首页 / 菜单页直接读取。 */
public record StoreInfo(
        String name,
        String address,
        String shortAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String distance,
        String hours,
        boolean open,
        String statusNote) {
}
