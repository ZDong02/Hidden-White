package com.hiddenwhite.common.domain;

/**
 * Unified pagination request model.
 *
 * <p><b>注意</b>：{@code sortField} / {@code sortOrder} 目前<b>不生效</b>。
 * 各列表接口的排序在 SQL 里写死（如订单按 {@code created_at DESC}、
 * 商品按 {@code category,id}）。保留这两个字段是为了不破坏已有调用方的参数结构，
 * 也是后续做「可配置排序」的落点；在此之前，客户端传了也不会有任何效果 ——
 * 如果前端需要排序能力，优先在 Service 里用白名单映射字段，绝不要把入参直接拼进 SQL。
 *
 * @param pageNum 当前页码（&lt; 1 时回落到 {@value #DEFAULT_PAGE_NUM}）
 * @param pageSize 每页数量（钳制在 {@value #MAX_PAGE_SIZE} 以内）
 * @param sortField 排序字段（预留，当前未生效）
 * @param sortOrder 排序方向（预留，当前未生效）
 */
public record PageQuery(Integer pageNum, Integer pageSize, String sortField, String sortOrder) {

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    public int normalizedPageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    public int normalizedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public String normalizedSortOrder() {
        if ("asc".equalsIgnoreCase(sortOrder)) {
            return "asc";
        }
        return "desc";
    }
}
