package com.hiddenwhite.admin.coffee.service;

import com.hiddenwhite.admin.coffee.domain.dto.MemberWriteRequest;
import com.hiddenwhite.admin.coffee.mapper.CoffeeMapper;
import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.common.points.PointsBizType;
import com.hiddenwhite.common.points.PointsLedger;
import com.hiddenwhite.common.points.PointsRefType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品上下架与会员改分测试。
 *
 * <p>锁住 2026-10-26 修复的缺陷：列表页一键下架原本复用整行更新接口，
 * 请求体只带 {@code enabled}，被「商品名称不能为空」拒掉，表现为「点了没反应」。
 * 正确行为是走只更新状态位的专用方法，且全程不触碰整行更新。
 *
 * <p>另锁住 2026-10-28 的积分口径：管理端改分必须换算成差值交给
 * {@link PointsLedger} 记账，整行更新不得再写 {@code points}。
 */
class CoffeeServiceTest {

    private final CoffeeMapper mapper = mock(CoffeeMapper.class);
    private final PointsLedger ledger = mock(PointsLedger.class);
    private final CoffeeService service = new CoffeeService(mapper, ledger);

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

    // ---- 会员改分：差值进流水 ----

    @Test
    void updateMemberRecordsOnlyTheDeltaThroughLedger() {
        when(mapper.memberPoints("demo")).thenReturn(1280);
        when(mapper.updateMember(eq("demo"), anyString(), anyString(), anyInt(), anyInt())).thenReturn(1);

        service.updateMember("demo", memberRequest("隐白客人", "金卡会员", 1000, 5, 1280));

        // 差额 -280 才是这次的「变动」，不是目标值 1000：写目标值会让流水求和与余额对不上
        verify(ledger).adjust("demo", -280, PointsBizType.ADMIN_ADJUST, PointsRefType.ADMIN, "demo",
                "管理端调整积分 1280 → 1000");
    }

    @Test
    void updateMemberWithoutPointsChangeWritesNoLedgerEntry() {
        when(mapper.memberPoints("demo")).thenReturn(1280);
        when(mapper.updateMember(eq("demo"), anyString(), anyString(), anyInt(), anyInt())).thenReturn(1);

        service.updateMember("demo", memberRequest("新昵称", "金卡会员", 1280, 5, 1280));

        // 只改昵称不该在积分流水里留下 change_amount = 0 的噪音记录
        verify(ledger, never()).adjust(anyString(), anyInt(), any(), any(), anyString(), anyString());
    }

    @Test
    void updateUnknownMemberIsRejectedBeforeTouchingLedger() {
        when(mapper.memberPoints("ghost")).thenReturn(null);

        assertThatThrownBy(() -> service.updateMember("ghost", memberRequest("幽灵", "普通会员", 10, 0, 0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(404))
                .hasMessageContaining("会员不存在");

        verify(ledger, never()).adjust(anyString(), anyInt(), any(), any(), anyString(), anyString());
    }

    private static MemberWriteRequest memberRequest(String displayName, String level, int points, int coupons, int growth) {
        MemberWriteRequest request = new MemberWriteRequest();
        request.setDisplayName(displayName);
        request.setLevel(level);
        request.setPoints(points);
        request.setCoupons(coupons);
        request.setGrowthValue(growth);
        return request;
    }
}
