package com.hiddenwhite.shop.domain;

/**
 * 商品分类（点单端只读视图）。
 *
 * <p>分类名从此由服务端下发，而不是两端各维护一份：管理端新增「茶饮」后，
 * 点单端分类栏立刻能显示正确的名字，不必等前端发版。
 */
public record Category(int code, String name) {
}
