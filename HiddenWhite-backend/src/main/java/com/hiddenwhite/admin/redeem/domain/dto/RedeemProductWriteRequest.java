package com.hiddenwhite.admin.redeem.domain.dto;

/**
 * 兑换品新增 / 编辑请求。
 *
 * <p>{@code stock} 用包装类型：{@code null} 是「不限量」这个业务语义本身，
 * 不能与「库存 0」混为一谈 —— 前者可以一直兑，后者是已兑完。
 */
public class RedeemProductWriteRequest {

    private String id;
    private String name;
    private String subtitle;
    private String description;
    private String image;
    private Integer pointsCost;
    private Integer stock;
    private Integer perMemberLimit;
    private Integer sortOrder;
    private Boolean enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Integer getPointsCost() { return pointsCost; }
    public void setPointsCost(Integer pointsCost) { this.pointsCost = pointsCost; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getPerMemberLimit() { return perMemberLimit; }
    public void setPerMemberLimit(Integer perMemberLimit) { this.perMemberLimit = perMemberLimit; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
