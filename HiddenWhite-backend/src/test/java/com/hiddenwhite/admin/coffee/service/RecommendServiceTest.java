package com.hiddenwhite.admin.coffee.service;

import com.hiddenwhite.admin.coffee.domain.dto.RecommendWriteRequest;
import com.hiddenwhite.admin.coffee.mapper.CoffeeMapper;
import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.common.points.PointsLedger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 首页推荐位写入校验测试。
 *
 * <p>锁住三条会在真实运营中出事的规则：
 * <ul>
 *   <li>不能把已下架商品挂上首页 —— 前台会直接少一屏，运营却以为配置成功；</li>
 *   <li>同一商品不能占两个推荐位（表上有唯一键，要在写库前给出可读提示）；</li>
 *   <li>文案超长必须挡在写库之前 —— MySQL 严格模式下的 1406 只会表现为笼统的保存失败。</li>
 * </ul>
 */
class RecommendServiceTest {

    private final CoffeeMapper mapper = mock(CoffeeMapper.class);
    private final CoffeeService service = new CoffeeService(mapper, mock(PointsLedger.class));

    private RecommendWriteRequest request(String productId) {
        RecommendWriteRequest request = new RecommendWriteRequest();
        request.setProductId(productId);
        request.setEyebrow("本周推荐");
        request.setHeadline("一杯，恰好。");
        request.setCaption("细腻奶感，纯粹咖啡香。");
        request.setLinkText("品味招牌馥白");
        request.setEnabled(true);
        return request;
    }

    @Test
    void createRejectsDisabledProductBeforeTouchingTable() {
        when(mapper.productEnabled("americano")).thenReturn(false);

        assertThatThrownBy(() -> service.createRecommend(request("americano")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下架");
        verify(mapper, never()).insertRecommend(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void createRejectsUnknownProductWithNotFound() {
        when(mapper.productEnabled("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.createRecommend(request("ghost")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(404))
                .hasMessageContaining("商品不存在");
    }

    @Test
    void createRejectsDuplicateProduct() {
        when(mapper.productEnabled("latte")).thenReturn(true);
        when(mapper.countRecommendProduct("latte", null)).thenReturn(1L);

        assertThatThrownBy(() -> service.createRecommend(request("latte")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(409))
                .hasMessageContaining("已在推荐位");
        verify(mapper, never()).insertRecommend(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void createAcceptsEmptyCopyAndDefaultsSortOrder() {
        when(mapper.productEnabled("latte")).thenReturn(true);
        when(mapper.countRecommendProduct("latte", null)).thenReturn(0L);
        RecommendWriteRequest request = new RecommendWriteRequest();
        request.setProductId(" latte ");

        service.createRecommend(request);

        // 文案留空 → 写空串（读取时再由服务端回填商品自身字段）；排序缺省 0
        verify(mapper).insertRecommend("latte", "", "", "", "", 0, true);
    }

    @Test
    void createRejectsOverlongCaptionWithoutTouchingTable() {
        when(mapper.productEnabled("latte")).thenReturn(true);
        when(mapper.countRecommendProduct("latte", null)).thenReturn(0L);
        RecommendWriteRequest request = request("latte");
        request.setCaption("细".repeat(61));

        assertThatThrownBy(() -> service.createRecommend(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("短句最多 60 个字");
        verify(mapper, never()).insertRecommend(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void updateExcludesItselfFromDuplicateCheck() {
        when(mapper.productEnabled("latte")).thenReturn(true);
        when(mapper.countRecommendProduct("latte", 7L)).thenReturn(0L);
        when(mapper.updateRecommend(7L, "latte", "本周推荐", "一杯，恰好。", "细腻奶感，纯粹咖啡香。", "品味招牌馥白", 0, true))
                .thenReturn(1);

        service.updateRecommend(7L, request("latte"));

        // 排除自身：否则「只改文案、不动商品」会被自己判成重复
        verify(mapper).countRecommendProduct("latte", 7L);
        verify(mapper).updateRecommend(7L, "latte", "本周推荐", "一杯，恰好。", "细腻奶感，纯粹咖啡香。", "品味招牌馥白", 0, true);
    }

    @Test
    void updateUnknownRowReportsNotFound() {
        when(mapper.productEnabled("latte")).thenReturn(true);
        when(mapper.countRecommendProduct("latte", 99L)).thenReturn(0L);
        when(mapper.updateRecommend(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updateRecommend(99L, request("latte")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(404));
    }

    @Test
    void toggleOnlyTouchesEnabledColumn() {
        when(mapper.updateRecommendEnabled(3L, false)).thenReturn(1);

        service.setRecommendEnabled(3L, false);

        verify(mapper).updateRecommendEnabled(3L, false);
        // 整行更新会要求商品与文案齐全，开关绝不能走它
        verify(mapper, never()).updateRecommend(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void toggleWithoutStateWritesNothing() {
        assertThatThrownBy(() -> service.setRecommendEnabled(3L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启用状态");
        verifyNoInteractions(mapper);
    }

    @Test
    void deleteUnknownRowReportsNotFound() {
        when(mapper.deleteRecommend(anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.deleteRecommend(42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(404));

        verify(mapper).deleteRecommend(42L);
        // 删除是推荐位自身的行，不该顺手动到商品
        verify(mapper, never()).deleteProduct(any());
    }
}
