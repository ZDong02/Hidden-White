package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.Category;
import com.hiddenwhite.shop.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类（点单端只读）。
 *
 * <p>放在独立的 {@code /api/categories} 而不是塞进 {@code /api/products}：
 * 分类是字典，数量是个位数、几乎不变；商品目录随上下架变化。
 * 合并返回会让「只想知道分类怎么叫」的请求把整份商品列表也拉一遍。
 *
 * <p>公开接口（{@code SecurityConfig} 已放行）：分类名要参与首屏渲染，
 * 不该强迫用户先登录。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CatalogService catalog;

    public CategoryController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<Category> list() {
        return catalog.categories();
    }
}
