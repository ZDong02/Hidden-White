package com.hiddenwhite.admin.coffee.service;

import com.hiddenwhite.admin.coffee.domain.dto.CategoryWriteRequest;
import com.hiddenwhite.admin.coffee.mapper.CoffeeMapper;
import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.common.points.PointsLedger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品分类的增删规则。
 *
 * <p>锁住三条不变式：
 * <ol>
 *   <li>编码由后端分配（{@code MAX(code)+1}），不接受调用方指定；</li>
 *   <li>同名分类不允许重复创建（409），否则下拉框里会出现两个一样的选项；</li>
 *   <li>分类被商品引用时禁止删除（409），绝不级联删商品、也不静默改商品分类。</li>
 * </ol>
 */
class CategoryServiceTest {

    private final CoffeeMapper mapper = mock(CoffeeMapper.class);
    private final CoffeeService service = new CoffeeService(mapper, mock(PointsLedger.class));

    private static CategoryWriteRequest request(String name) {
        CategoryWriteRequest request = new CategoryWriteRequest();
        request.setName(name);
        return request;
    }

    @Test
    void createAssignsNextCodeFromBackend() {
        when(mapper.countCategoryName("茶饮")).thenReturn(0L);
        when(mapper.nextCategoryCode()).thenReturn(2);

        int code = service.createCategory(request("  茶饮  "));

        assertThat(code).isEqualTo(2);
        // 名称去空格后再落库：否则「茶饮」与「茶饮 」会被唯一索引当成两个分类
        verify(mapper).insertCategory(2, "茶饮", 3);
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.createCategory(request("   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类名称");

        verify(mapper, never()).insertCategory(anyInt(), anyString(), anyInt());
    }

    @Test
    void createRejectsDuplicateName() {
        when(mapper.countCategoryName("招牌精选")).thenReturn(1L);

        assertThatThrownBy(() -> service.createCategory(request("招牌精选")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在同名分类");

        verify(mapper, never()).insertCategory(anyInt(), anyString(), anyInt());
    }

    @Test
    void deleteRejectsCategoryStillInUse() {
        when(mapper.countCategory(0)).thenReturn(1L);
        when(mapper.countProductsInCategory(0)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteCategory(0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 款商品");

        // 关键：一枚商品都不能动，也不能把分类删掉
        verify(mapper, never()).deleteCategory(anyInt());
        verify(mapper, never()).deleteProduct(anyString());
    }

    @Test
    void deleteRemovesUnusedCategory() {
        when(mapper.countCategory(2)).thenReturn(1L);
        when(mapper.countProductsInCategory(2)).thenReturn(0L);
        when(mapper.deleteCategory(2)).thenReturn(1);

        service.deleteCategory(2);

        verify(mapper).deleteCategory(2);
    }

    @Test
    void deleteRejectsUnknownCategory() {
        when(mapper.countCategory(99)).thenReturn(0L);

        assertThatThrownBy(() -> service.deleteCategory(99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");

        verify(mapper, never()).deleteCategory(anyInt());
    }
}
