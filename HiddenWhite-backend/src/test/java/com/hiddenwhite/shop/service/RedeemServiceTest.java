package com.hiddenwhite.shop.service;

import com.hiddenwhite.common.points.PointsBizType;
import com.hiddenwhite.common.points.PointsLedger;
import com.hiddenwhite.common.points.PointsRefType;
import com.hiddenwhite.shop.domain.RedeemProduct;
import com.hiddenwhite.shop.domain.Redemption;
import com.hiddenwhite.shop.domain.RedemptionStatus;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.repository.RedeemRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static com.hiddenwhite.common.domain.Constants.ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分兑换服务测试。
 *
 * <p>覆盖方案第八节的几条硬要求：扣分与建单同进同退、限兑超限时不留任何痕迹、
 * 库存耗尽即拒、取消/过期退回只发生一次（幂等）。
 *
 * <p>并发本身难以在单测里复现，这里锁住的是<b>支撑并发的那些写法</b>：
 * 扣分走账本的条件更新、状态翻转走带条件的 repository 方法，
 * 一旦有人把「先查再改」写回来，这里的交互断言就会失败。
 */
class RedeemServiceTest {

    private final RedeemRepository repository = mock(RedeemRepository.class);
    private final PointsLedger ledger = mock(PointsLedger.class);
    private final RedeemService service = new RedeemService(repository, ledger);

    private static final RedeemProduct LATTE = new RedeemProduct(
            "rw-latte", "经典拿铁", "到店核销 · 兑一杯", "双份浓缩与丝绒奶泡",
            "/uploads/2026/10/28/latte.jpg", 1280, null, 1, 2, true);

    // ---- 兑换 ----

    @Test
    void exchangeDeductsPointsAndIssuesEightDigitCode() {
        when(repository.product("rw-latte")).thenReturn(LATTE);
        when(ledger.balance("demo")).thenReturn(1280);
        when(repository.nextRedeemNo(any())).thenReturn("RW202610280001");
        when(repository.redeemNoExists(anyString())).thenReturn(false);
        when(repository.codeExists(anyString())).thenReturn(false);
        when(repository.countUsed("demo", "rw-latte")).thenReturn(0);
        when(repository.deductStock("rw-latte")).thenReturn(1);

        Redemption redemption = service.exchange("demo", "rw-latte");

        verify(ledger).adjust("demo", -1280, PointsBizType.REDEEM, PointsRefType.REDEMPTION,
                redemption.id(), "兑换「经典拿铁」");
        verify(repository).insert(redemption);
        assertThat(redemption.code()).matches("\\d{8}");
        assertThat(redemption.status()).isEqualTo(RedemptionStatus.PENDING);
        assertThat(redemption.redeemNo()).isEqualTo("RW202610280001");
        // 有效期为 30 天：核销截止时间必须落在下单之后第 30 天附近
        assertThat(redemption.expiresAt()).isAfter(redemption.createdAt().plusDays(29));
    }

    @Test
    void exchangeIsRejectedWhenPointsAreNotEnough() {
        when(repository.product("rw-latte")).thenReturn(LATTE);
        when(ledger.balance("demo")).thenReturn(300);

        assertThatThrownBy(() -> service.exchange("demo", "rw-latte"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分不足");

        // 预检就该拦住，不该先去扣分再回滚
        verify(ledger, never()).adjust(anyString(), anyInt(), any(), any(), anyString(), anyString());
        verify(repository, never()).insert(any());
    }

    @Test
    void exchangeIsRejectedWhenStockRanOut() {
        when(repository.product("rw-badge")).thenReturn(new RedeemProduct(
                "rw-badge", "隐白周边徽章", "限量", "", "", 2880, 0, 1, 4, true));

        assertThatThrownBy(() -> service.exchange("demo", "rw-badge"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已兑完");

        verify(ledger, never()).adjust(anyString(), anyInt(), any(), any(), anyString(), anyString());
    }

    @Test
    void exchangeRollsBackWhenMemberLimitReached() {
        when(repository.product("rw-latte")).thenReturn(LATTE);
        when(ledger.balance("demo")).thenReturn(5000);
        when(repository.nextRedeemNo(any())).thenReturn("RW202610280002");
        when(repository.redeemNoExists(anyString())).thenReturn(false);
        when(repository.codeExists(anyString())).thenReturn(false);
        // 扣分之后才查限兑：此时已有 1 张，limit = 1，必须拒绝
        when(repository.countUsed("demo", "rw-latte")).thenReturn(1);

        assertThatThrownBy(() -> service.exchange("demo", "rw-latte"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("限兑");

        // 抛异常 + 事务回滚 = 扣分与流水一起撤销；这里断言的是「没有建单」
        verify(repository, never()).insert(any());
        verify(repository, never()).deductStock(anyString());
    }

    @Test
    void exchangeRejectsDisabledProduct() {
        when(repository.product("rw-old")).thenReturn(new RedeemProduct(
                "rw-old", "下架商品", "", "", "", 100, null, 0, 9, false));

        assertThatThrownBy(() -> service.exchange("demo", "rw-old"))
                .isInstanceOf(RuntimeException.class);
        verify(repository, never()).insert(any());
    }

    // ---- 取消 ----

    @Test
    void cancelRefundsFullPointsOnce() {
        Redemption pending = pendingRedemption("rid-1", 1280);
        when(repository.redemption("rid-1")).thenReturn(pending);
        when(repository.cancel(anyString(), anyString(), any())).thenReturn(1);

        service.cancel("demo", "rid-1");

        verify(ledger).adjust("demo", 1280, PointsBizType.REDEEM_CANCEL, PointsRefType.REDEMPTION,
                "rid-1", "取消兑换「经典拿铁」退回积分");
    }

    @Test
    void cancelOfAlreadyUsedTicketIsRejectedWithoutRefund() {
        when(repository.redemption("rid-2")).thenReturn(pendingRedemption("rid-2", 1280));
        // 条件更新未命中：要么已核销 / 已过期，要么被并发取消过
        when(repository.cancel(anyString(), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancel("demo", "rid-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法取消");

        verify(ledger, never()).adjust(anyString(), anyInt(), any(), any(), anyString(), anyString());
    }

    // ---- 惰性过期 ----

    @Test
    void settleExpiredRefundsOnlyWhenStatusFlipSucceeded() {
        when(repository.expiredPending(any())).thenReturn(List.of(pendingRedemption("rid-3", 880)));
        when(repository.markExpired("rid-3")).thenReturn(1);

        assertThat(service.settleExpired()).isEqualTo(1);

        verify(ledger).adjust("demo", 880, PointsBizType.REDEEM_EXPIRE_REFUND, PointsRefType.REDEMPTION,
                "rid-3", "兑换「经典拿铁」逾期未核销，积分已退回");
    }

    @Test
    void settleExpiredIsIdempotentWhenAlreadyFlipped() {
        when(repository.expiredPending(any())).thenReturn(List.of(pendingRedemption("rid-4", 880)));
        // 第二次结算：状态已不是 PENDING，翻转为 0 行 → 绝不能再退一次分
        when(repository.markExpired("rid-4")).thenReturn(0);

        assertThat(service.settleExpired()).isZero();

        verify(ledger, never()).adjust(anyString(), anyInt(), any(), any(), anyString(), anyString());
    }

    private static Redemption pendingRedemption(String id, int pointsCost) {
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        return new Redemption(id, "RW202610280009", "demo", "rw-latte", "经典拿铁", "", pointsCost,
                "12345678", RedemptionStatus.PENDING, now.plusDays(30), null, null, null, now);
    }
}
