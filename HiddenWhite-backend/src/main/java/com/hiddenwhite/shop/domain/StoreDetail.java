package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;
import java.time.LocalTime;

/** 门店原始字段（不含派生的营业状态），供管理端读取和修改。 */
public record StoreDetail(
        String name,
        String address,
        String shortAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String distance,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean manualClosed,
        String statusNote) {

    public StoreInfo toInfo() {
        boolean open = !manualClosed
                && !LocalTime.now().isBefore(openingTime)
                && LocalTime.now().isBefore(closingTime);
        String hours = openingTime.toString().substring(0, 5) + "–" + closingTime.toString().substring(0, 5);
        return new StoreInfo(name, address, shortAddress, latitude, longitude, distance, hours, open, statusNote);
    }
}
