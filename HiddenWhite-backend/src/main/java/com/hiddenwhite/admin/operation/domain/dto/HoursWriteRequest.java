package com.hiddenwhite.admin.operation.domain.dto;

import java.util.List;

/**
 * 运营时间保存请求：默认时段（stores 表，全天生效的兜底值）
 * 加 7 天按星期配置（store_hours 表，整批事务保存）。
 */
public class HoursWriteRequest {

    private String defaultOpen;
    private String defaultClose;
    private List<DayHoursItem> days;

    public String getDefaultOpen() { return defaultOpen; }
    public void setDefaultOpen(String defaultOpen) { this.defaultOpen = defaultOpen; }
    public String getDefaultClose() { return defaultClose; }
    public void setDefaultClose(String defaultClose) { this.defaultClose = defaultClose; }
    public List<DayHoursItem> getDays() { return days; }
    public void setDays(List<DayHoursItem> days) { this.days = days; }
}
