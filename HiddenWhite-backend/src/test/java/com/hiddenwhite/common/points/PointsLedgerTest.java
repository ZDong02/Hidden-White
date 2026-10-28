package com.hiddenwhite.common.points;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分账本测试。
 *
 * <p>锁住三条不能退让的口径：
 * <ol>
 *   <li>扣分必须是「条件更新」—— 余额判定交给数据库，不存在先读后写的超扣窗口；</li>
 *   <li>余额不足时不允许留下流水（否则对账会出现「扣了分没扣钱」的幽灵记录）；</li>
 *   <li>{@code balance_after} 取变动后的回读值，不是本地推算。</li>
 * </ol>
 */
class PointsLedgerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PointsLedger ledger = new PointsLedger(jdbc);

    @Test
    void deductWritesLogWithReReadBalance() {
        when(jdbc.update(contains("points >= ?"), any(), any(), any())).thenReturn(1);
        when(jdbc.query(contains("SELECT points FROM members"), ArgumentMatchers.<RowMapper<Integer>>any(), eq("demo")))
                .thenReturn(List.of(720));

        int balance = ledger.adjust("demo", -1280, PointsBizType.REDEEM, PointsRefType.REDEMPTION,
                "rid-1", "兑换「经典拿铁」");

        assertThat(balance).isEqualTo(720);
        verify(jdbc).update(contains("INSERT INTO member_points_log"),
                eq("demo"), eq(-1280), eq(720), eq("REDEEM"), eq("REDEMPTION"), eq("rid-1"), anyString());
    }

    @Test
    void insufficientBalanceIsAConflictAndLeavesNoLog() {
        // 条件更新影响 0 行 = 余额不够；同时说明会员本身是存在的
        when(jdbc.update(contains("points >= ?"), any(), any(), any())).thenReturn(0);
        when(jdbc.query(contains("SELECT 1 FROM members"), ArgumentMatchers.<RowMapper<Integer>>any(), any()))
                .thenReturn(List.of(1));

        assertThatThrownBy(() -> ledger.adjust("demo", -2000, PointsBizType.REDEEM,
                PointsRefType.REDEMPTION, "rid-2", "超兑"))
                .isInstanceOf(PointsException.class)
                .satisfies(error -> assertThat(((PointsException) error).status()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("积分不足");

        verify(jdbc, never()).update(contains("INSERT INTO member_points_log"),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownMemberReportsNotFoundInsteadOfInsufficient() {
        when(jdbc.update(contains("points >= ?"), any(), any(), any())).thenReturn(0);
        when(jdbc.query(contains("SELECT 1 FROM members"), ArgumentMatchers.<RowMapper<Integer>>any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> ledger.adjust("ghost", -10, PointsBizType.REDEEM,
                PointsRefType.REDEMPTION, "rid-3", ""))
                .isInstanceOf(PointsException.class)
                .satisfies(error -> assertThat(((PointsException) error).status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void zeroDeltaDoesNotTouchMembersOrLog() {
        when(jdbc.query(contains("SELECT points FROM members"), ArgumentMatchers.<RowMapper<Integer>>any(), eq("demo")))
                .thenReturn(List.of(1280));

        assertThat(ledger.adjust("demo", 0, PointsBizType.ADMIN_ADJUST,
                PointsRefType.ADMIN, "demo", "无变化")).isEqualTo(1280);

        // 0 变动写流水只会给对账制造噪音
        verify(jdbc, never()).update(contains("INSERT INTO member_points_log"),
                any(), any(), any(), any(), any(), any(), any());
    }
}
