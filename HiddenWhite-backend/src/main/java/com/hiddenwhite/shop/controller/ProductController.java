package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.domain.ProductOption;
import com.hiddenwhite.shop.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<Product> list(@RequestParam(required = false) Integer category) {
        return catalog.products(category);
    }

    @GetMapping("/{id}")
    public Product detail(@PathVariable String id) {
        return catalog.product(id);
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("sizes", catalog.sizes(), "temperatures", catalog.temperatures(), "milks", catalog.milks());
    }
}
