package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.domain.ProductOption;
import com.hiddenwhite.shop.domain.Recommendation;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 目录服务：商品与规格选项查询、下单参数校验。 */
@Service
public class CatalogService {
    public static final String STANDARD_SIZE = "标准杯 240 毫升";
    public static final String HOT = "热";
    public static final String NO_MILK = "无奶";

    /** 推荐位未填眉标时的默认文案，与点单端原有的写死文案保持一致。 */
    private static final String DEFAULT_EYEBROW = "本周推荐";

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

    /**
     * 首页轮播的推荐池（已回填兜底文案）。
     *
     * <p>兜底放在服务端而不是前端：同一个「留空就是默认值」的规则只写一处，
     * 将来接入其它客户端（H5 之外的小程序版本、运营大屏）时不会各自实现一遍。
     * 具体规则：眉标 → 「本周推荐」；主标题 → 商品名；短句 → 副标题；
     * 链接词 → 「品味 + 商品名」。
     *
     * <p>随机抽样不在这里做：池子是确定的，抽哪 3 款由端上决定，
     * 这样同一屏在用户浏览期间不会因为再次请求而整块换掉。
     */
    public List<Recommendation> recommendations() {
        return repository.recommendations().stream().map(this::withDisplayCopy).toList();
    }

    private Recommendation withDisplayCopy(Recommendation row) {
        String name = blankToDefault(row.name(), "");
        String eyebrow = blankToDefault(row.eyebrow(), DEFAULT_EYEBROW);
        String headline = blankToDefault(row.headline(), name);
        String caption = blankToDefault(row.caption(), row.subtitle());
        String linkText = blankToDefault(row.linkText(), name.isEmpty() ? "查看" : "品味" + name);
        return new Recommendation(row.id(), row.productId(), name, blankToDefault(row.subtitle(), ""),
                row.price(), row.image(), eyebrow, headline, caption, linkText, row.sortOrder());
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value.trim();
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
