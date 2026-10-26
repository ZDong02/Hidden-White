package com.hiddenwhite.admin.coffee.service;

import com.hiddenwhite.admin.coffee.mapper.CoffeeMapper;
import com.hiddenwhite.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品上下架测试。
 *
 * <p>锁住 2026-10-26 修复的缺陷：列表页一键下架原本复用整行更新接口，
 * 请求体只带 {@code enabled}，被「商品名称不能为空」拒掉，表现为「点了没反应」。
 * 正确行为是走只更新状态位的专用方法，且全程不触碰整行更新。
 */
class CoffeeServiceTest {

    private final CoffeeMapper mapper = mock(CoffeeMapper.class);
    private final CoffeeService service = new CoffeeService(mapper);

    @Test
    void disableProductOnlyTouchesEnabledColumn() {
        when(mapper.updateProductEnabled("latte", false)).thenReturn(1);

        service.setProductEnabled("latte", false);

        verify(mapper).updateProductEnabled("latte", false);
        // 整行更新会要求名称/价格/分类，且会覆盖其余列 —— 上下架绝不能走它
        verify(mapper, never()).updateProduct(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean());
        verify(mapper, never()).disableProduct(anyString());
    }

    @Test
    void enableProductPassesTrueThrough() {
        when(mapper.updateProductEnabled("latte", true)).thenReturn(1);

        service.setProductEnabled("latte", true);

        verify(mapper).updateProductEnabled("latte", true);
    }

    @Test
    void unknownProductReportsNotFound() {
        when(mapper.updateProductEnabled("ghost", false)).thenReturn(0);

        assertThatThrownBy(() -> service.setProductEnabled("ghost", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(404))
                .hasMessageContaining("商品不存在");
    }

    @Test
    void missingStatusIsRejectedWithoutTouchingDatabase() {
        assertThatThrownBy(() -> service.setProductEnabled("latte", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(400))
                .hasMessageContaining("上下架状态");

        verify(mapper, never()).updateProductEnabled(anyString(), anyBoolean());
    }
}
