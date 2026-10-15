package com.hiddenwhite.admin.coffee.domain.dto;

/** 会员权益新增 / 修改请求。 */
public class BenefitWriteRequest {

    private String icon;
    private String title;
    private String description;
    private Integer sortOrder;
    private Boolean enabled;

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
