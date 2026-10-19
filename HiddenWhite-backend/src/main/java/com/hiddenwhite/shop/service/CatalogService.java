package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.domain.ProductOption;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 目录服务：商品与规格选项查询、下单参数校验。 */
@Service
public class CatalogService {
    public static final String STANDARD_SIZE = "标准杯 240 毫升";
    public static final String HOT = "热";
    public static final String NO_MILK = "无奶";

    private final ShopRepository repository;

    public CatalogService(ShopRepository repository) {
        this.repository = repository;
    }

    public List<Product> products(Integer category) {
        return repository.products(category);
    }

    public Product product(String id) {
        return repository.product(id);
    }

    public List<ProductOption> sizes() {
        return byType("SIZE");
    }

    public List<ProductOption> milks() {
        return byType("MILK");
    }

    public List<String> temperatures() {
        return byType("TEMPERATURE").stream().map(ProductOption::label).toList();
    }

    private List<ProductOption> byType(String type) {
        return repository.options(type);
    }

    public ProductOption size(String label) {
        return sizes().stream().filter(item -> item.label().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("杯型不支持: " + label));
    }

    public ProductOption milk(String label) {
        return milks().stream().filter(item -> item.label().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("奶类不支持: " + label));
    }

    public void temperature(String value) {
        if (!temperatures().contains(value)) throw new IllegalArgumentException("温度不支持: " + value);
    }
}
