package com.hiddenwhite.admin.coffee.domain.dto;

/** 会员资料修改请求（不含手机号与卡号，账号类字段由点单端维护）。 */
public class MemberWriteRequest {

    private String displayName;
    private String level;
    private Integer points;
    private Integer coupons;
    private Integer growthValue;
    /** 生日月份 1-12；与 birthdayDay 同为 null 表示清空（会员可自行重新填写）。 */
    private Integer birthdayMonth;
    /** 生日日 1-31。 */
    private Integer birthdayDay;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getCoupons() { return coupons; }
    public void setCoupons(Integer coupons) { this.coupons = coupons; }
    public Integer getGrowthValue() { return growthValue; }
    public void setGrowthValue(Integer growthValue) { this.growthValue = growthValue; }
    public Integer getBirthdayMonth() { return birthdayMonth; }
    public void setBirthdayMonth(Integer birthdayMonth) { this.birthdayMonth = birthdayMonth; }
    public Integer getBirthdayDay() { return birthdayDay; }
    public void setBirthdayDay(Integer birthdayDay) { this.birthdayDay = birthdayDay; }
}
