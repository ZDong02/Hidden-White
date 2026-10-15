package com.hiddenwhite.admin.operation.domain.dto;

/** 按星期运营时段行；dayOfWeek 1=周一 … 7=周日，时间为 HH:mm 两位格式。 */
public class DayHoursItem {

    private Integer dayOfWeek;
    private String openTime;
    private String closeTime;
    private Boolean closed;

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }
    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }
    public Boolean getClosed() { return closed; }
    public void setClosed(Boolean closed) { this.closed = closed; }
}
