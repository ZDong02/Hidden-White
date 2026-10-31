package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Recommendation;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 推荐位文案兜底测试。
 *
 * <p>「留空就用商品自身字段」这条规则只在服务端实现一次，
 * 端上拿到的四个文案字段必须永远非空 —— 否则小程序模板里会出现
 * 空白标题把轮播卡片撑塌（空文本元素高度为 0）的经典问题。
 */
class CatalogServiceRecommendTest {

    private final ShopRepository repository = mock(ShopRepository.class);
    private final CatalogService catalog = new CatalogService(repository);

    private Recommendation row(String eyebrow, String headline, String caption, String linkText) {
        return new Recommendation(1L, "latte", "招牌馥白", "双份浓缩 · 丝绒奶感",
                new BigDecimal("32.00"), "/static/coffee/latte.png",
                eyebrow, headline, caption, linkText, 1);
    }

    @Test
    void blankCopyFallsBackToProductFields() {
        when(repository.recommendations()).thenReturn(List.of(row("", "  ", "", "")));

        Recommendation slide = catalog.recommendations().get(0);

        assertThat(slide.eyebrow()).isEqualTo("本周推荐");
        assertThat(slide.headline()).isEqualTo("招牌馥白");
        assertThat(slide.caption()).isEqualTo("双份浓缩 · 丝绒奶感");
        assertThat(slide.linkText()).isEqualTo("品味招牌馥白");
    }

    @Test
    void configuredCopyIsTrimmedAndKept() {
        when(repository.recommendations()).thenReturn(List.of(
                row(" 本周推荐 · 匠心双份浓缩 ", " 一杯，恰好。 ", " 细腻奶感，纯粹咖啡香。 ", " 品味招牌馥白 ")));

        Recommendation slide = catalog.recommendations().get(0);

        assertThat(slide.eyebrow()).isEqualTo("本周推荐 · 匠心双份浓缩");
        assertThat(slide.headline()).isEqualTo("一杯，恰好。");
        assertThat(slide.caption()).isEqualTo("细腻奶感，纯粹咖啡香。");
        assertThat(slide.linkText()).isEqualTo("品味招牌馥白");
    }

    @Test
    void priceAndImagePassThroughUntouched() {
        when(repository.recommendations()).thenReturn(List.of(row("", "", "", "")));

        Recommendation slide = catalog.recommendations().get(0);

        // 价格与图片是商品实时数据：推荐位不做快照，端上看到的就是当前售价
        assertThat(slide.price()).isEqualByComparingTo("32.00");
        assertThat(slide.image()).isEqualTo("/static/coffee/latte.png");
        assertThat(slide.productId()).isEqualTo("latte");
    }

    @Test
    void emptyPoolStaysEmpty() {
        when(repository.recommendations()).thenReturn(List.of());

        assertThat(catalog.recommendations()).isEmpty();
    }
}
