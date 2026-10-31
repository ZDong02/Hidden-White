package com.hiddenwhite.admin.coffee.domain.dto;

/**
 * 首页推荐位新增 / 修改请求。
 *
 * <p>文案字段允许留空 —— 留空即「用商品自身的字段兜底」（主标题→商品名、
 * 短句→副标题、链接词→品味+商品名），由点单端读取时统一回填。
 * 这样运营只想「把某个商品挂上首页」时不必编造文案。
 */
public class RecommendWriteRequest {

    /** 关联商品 ID，必填且必须是存在的在售商品 */
    private String productId;
    /** 眉标，如「本周推荐 · 匠心双份浓缩」 */
    private String eyebrow;
    /** 主标题，如「一杯，恰好。」 */
    private String headline;
    /** 短句，如「细腻奶感，纯粹咖啡香。」 */
    private String caption;
    /** 链接文案，如「品味招牌馥白」 */
    private String linkText;
    private Integer sortOrder;
    private Boolean enabled;

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getEyebrow() { return eyebrow; }
    public void setEyebrow(String eyebrow) { this.eyebrow = eyebrow; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getLinkText() { return linkText; }
    public void setLinkText(String linkText) { this.linkText = linkText; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
