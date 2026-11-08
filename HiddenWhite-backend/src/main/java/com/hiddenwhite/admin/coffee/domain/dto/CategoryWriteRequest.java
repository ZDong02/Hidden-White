package com.hiddenwhite.admin.coffee.domain.dto;

/**
 * 商品分类新增请求。
 *
 * <p>只有名称与排序：编码（code）由后端分配 —— 它同时是 {@code products.category}
 * 引用的值，交给运营手填既容易撞车，也会让人以为它可以随意重排。
 * 本次不提供改名：改名的请求量远低于增删，且一旦改错会影响已在售商品的展示口径，
 * 后续需要时再单独加 {@code PUT /categories/{code}}。
 */
public class CategoryWriteRequest {

    private String name;
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
