package com.hiddenwhite.admin.coffee.domain.dto;

/** 咖啡商品新增 / 修改请求（id 仅创建时需要，创建后不可改）。 */
public class ProductWriteRequest {

    private String id;
    private String name;
    private String subtitle;
    private String description;
    private String detailDescription;
    private java.math.BigDecimal price;
    private String image;
    private String detailImage;
    private Integer category;
    private Boolean hasMilk;
    private Boolean enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDetailDescription() { return detailDescription; }
    public void setDetailDescription(String detailDescription) { this.detailDescription = detailDescription; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getDetailImage() { return detailImage; }
    public void setDetailImage(String detailImage) { this.detailImage = detailImage; }
    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public Boolean getHasMilk() { return hasMilk; }
    public void setHasMilk(Boolean hasMilk) { this.hasMilk = hasMilk; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
