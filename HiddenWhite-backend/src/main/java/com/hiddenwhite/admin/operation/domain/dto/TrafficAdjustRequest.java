package com.hiddenwhite.admin.operation.domain.dto;

import java.math.BigDecimal;

/** 日流量调整请求：仅已结算（settled）的日期可调整，原因必填。 */
public class TrafficAdjustRequest {

    private Integer orderCount;
    private BigDecimal revenue;
    private Integer visitorCount;
    private String reason;

    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    public Integer getVisitorCount() { return visitorCount; }
    public void setVisitorCount(Integer visitorCount) { this.visitorCount = visitorCount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
