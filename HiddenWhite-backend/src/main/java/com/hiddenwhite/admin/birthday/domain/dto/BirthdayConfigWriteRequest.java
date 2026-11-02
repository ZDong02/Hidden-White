package com.hiddenwhite.admin.birthday.domain.dto;

/**
 * 生日礼遇配置写入请求。
 *
 * <p>{@code giftType} 在 V1 只接受 {@code POINTS}：赠饮模式需要「0 元兑换单 + 核销」
 * 联动，链路尚未打通，放开只会让运营配出一个用户点了就报错的开关。
 */
public class BirthdayConfigWriteRequest {

    private Boolean enabled;
    private String giftType;
    private Integer pointsAmount;
    private String windowMode;
    private Integer windowDays;
    private String title;
    private String subtitle;
    private String description;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getGiftType() { return giftType; }
    public void setGiftType(String giftType) { this.giftType = giftType; }
    public Integer getPointsAmount() { return pointsAmount; }
    public void setPointsAmount(Integer pointsAmount) { this.pointsAmount = pointsAmount; }
    public String getWindowMode() { return windowMode; }
    public void setWindowMode(String windowMode) { this.windowMode = windowMode; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
