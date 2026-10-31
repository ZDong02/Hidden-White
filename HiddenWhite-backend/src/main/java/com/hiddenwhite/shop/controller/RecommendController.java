package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.Recommendation;
import com.hiddenwhite.shop.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页轮播推荐位（公开接口，免登录）。
 *
 * <p>返回的是「推荐池」而不是「3 屏」：抽哪几款由端上决定，
 * 端上在池子内容不变时保留当前 3 屏，避免切 Tab / 刷新时轮播整块换人。
 * 相关配置在管理端「咖啡运营 · 推荐商品」维护。
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendController {
    private final CatalogService catalog;

    public RecommendController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<Recommendation> list() {
        return catalog.recommendations();
    }
}
