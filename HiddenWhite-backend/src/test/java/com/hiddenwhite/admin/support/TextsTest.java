package com.hiddenwhite.admin.support;

import com.hiddenwhite.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文本工具的语义边界。
 *
 * <p>这些方法被三个管理端服务共用，语义一旦漂移（例如把「空串」当成有值），
 * 表现是某个下拉框清空后报出一条与事实无关的错，很难从现象定位到工具类。
 * 这里把三类容易混的情况钉住：null、纯空白、以及「兜底只在 null 时生效」。
 */
class TextsTest {

    @Test
    void trimNormalisesNullToEmpty() {
        assertThat(Texts.trim(null)).isEmpty();
        assertThat(Texts.trim("  美式  ")).isEqualTo("美式");
    }

    @Test
    void trimWithFallbackOnlyAppliesToNull() {
        // 关键：空串不是 null，不会被兜底 —— 需要「空也算没填」时用 isBlank
        assertThat(Texts.trim(null, "POINTS")).isEqualTo("POINTS");
        assertThat(Texts.trim("", "POINTS")).isEmpty();
        assertThat(Texts.trim(" drink ", "POINTS")).isEqualTo("drink");
    }

    @Test
    void isBlankCoversNullAndWhitespace() {
        assertThat(Texts.isBlank(null)).isTrue();
        assertThat(Texts.isBlank("   ")).isTrue();
        assertThat(Texts.isBlank(" x ")).isFalse();
    }

    @Test
    void blankToNullKeepsQueriesParameterless() {
        assertThat(Texts.blankToNull("  ")).isNull();
        assertThat(Texts.blankToNull(" 拿铁 ")).isEqualTo("拿铁");
    }

    @Test
    void requireTextRejectsBlankAndReturnsValue() {
        assertThat(Texts.requireText("拿铁", "商品名称")).isEqualTo("拿铁");
        assertThatThrownBy(() -> Texts.requireText("  ", "商品名称"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品名称不能为空");
    }

    @Test
    void requireMaxLengthReportsBothSides() {
        Texts.requireMaxLength("招牌精选", 20, "分类名称");

        assertThatThrownBy(() -> Texts.requireMaxLength("一二三四五六", 5, "分类名称"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类名称最多 5 个字，当前 6 个字");
    }
}
