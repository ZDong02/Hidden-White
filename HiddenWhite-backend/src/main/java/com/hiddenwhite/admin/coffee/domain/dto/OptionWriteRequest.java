package com.hiddenwhite.admin.coffee.domain.dto;

/** 规格选项（杯型 / 奶类 / 温度）新增 / 修改请求。 */
public class OptionWriteRequest {

    private String type;
    private String label;
    private java.math.BigDecimal extraPrice;
    private Integer sortOrder;
    private Boolean enabled;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public java.math.BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(java.math.BigDecimal extraPrice) { this.extraPrice = extraPrice; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
