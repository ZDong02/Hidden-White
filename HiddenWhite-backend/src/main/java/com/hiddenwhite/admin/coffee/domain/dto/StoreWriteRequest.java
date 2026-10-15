package com.hiddenwhite.admin.coffee.domain.dto;

import java.math.BigDecimal;

/** 门店信息修改请求；营业时间为 HH:mm 两位格式。 */
public class StoreWriteRequest {

    private String name;
    private String address;
    private String shortAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String distance;
    private String openingTime;
    private String closingTime;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getShortAddress() { return shortAddress; }
    public void setShortAddress(String shortAddress) { this.shortAddress = shortAddress; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }
    public String getOpeningTime() { return openingTime; }
    public void setOpeningTime(String openingTime) { this.openingTime = openingTime; }
    public String getClosingTime() { return closingTime; }
    public void setClosingTime(String closingTime) { this.closingTime = closingTime; }
}
