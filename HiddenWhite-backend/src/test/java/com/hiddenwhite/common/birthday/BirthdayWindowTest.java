package com.hiddenwhite.common.birthday;

import com.hiddenwhite.common.birthday.BirthdayWindow.Window;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生日礼遇窗口判定测试。
 *
 * <p>这里锁的是四个「写错一次就多发/少发一堆积分」的边界：
 * <ol>
 *   <li>生日当月整月可领，月初与月末都不能漏；</li>
 *   <li>2 月 29 日在平年归一到 2 月 28 日（提前一天，不是顺延到 3 月 1 日）；</li>
 *   <li>AROUND 模式跨月、跨年都不能算错；</li>
 *   <li>窗口外给出的 {@code daysUntil} 是倒计时用的，不能是负数或跳到错误的年份。</li>
 * </ol>
 */
class BirthdayWindowTest {

    @Test
    void monthModeCoversWholeBirthdayMonth() {
        Window first = BirthdayWindow.of(11, 2, LocalDate.of(2026, 11, 1), "MONTH", 3);
        assertThat(first.claimable()).isTrue();
        assertThat(first.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(first.to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(first.target()).isEqualTo(LocalDate.of(2026, 11, 2));

        Window last = BirthdayWindow.of(11, 2, LocalDate.of(2026, 11, 30), "MONTH", 3);
        assertThat(last.claimable()).isTrue();
        // 生日已过，窗口内不再强调倒计时
        assertThat(last.daysUntil()).isZero();
    }

    @Test
    void monthModeCountsDownToNextBirthday() {
        Window before = BirthdayWindow.of(11, 2, LocalDate.of(2026, 10, 24), "MONTH", 3);
        assertThat(before.claimable()).isFalse();
        assertThat(before.target()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(before.daysUntil()).isEqualTo(9);
        assertThat(before.from()).isEqualTo(LocalDate.of(2026, 11, 1));

        Window after = BirthdayWindow.of(11, 2, LocalDate.of(2026, 12, 1), "MONTH", 3);
        assertThat(after.claimable()).isFalse();
        assertThat(after.target()).isEqualTo(LocalDate.of(2027, 11, 2));
        assertThat(after.daysUntil()).isPositive();
    }

    @Test
    void leapDayFallsBackToFeb28InCommonYear() {
        // 2027 不是闰年：2 月 29 日出生的人按 2 月 28 日判定
        Window common = BirthdayWindow.of(2, 29, LocalDate.of(2027, 2, 28), "AROUND", 0);
        assertThat(common.claimable()).isTrue();
        assertThat(common.target()).isEqualTo(LocalDate.of(2027, 2, 28));

        assertThat(BirthdayWindow.of(2, 29, LocalDate.of(2027, 3, 1), "AROUND", 0).claimable())
                .isFalse();
    }

    @Test
    void leapDayStaysOnFeb29InLeapYear() {
        Window onDay = BirthdayWindow.of(2, 29, LocalDate.of(2028, 2, 29), "AROUND", 0);
        assertThat(onDay.claimable()).isTrue();
        assertThat(onDay.target()).isEqualTo(LocalDate.of(2028, 2, 29));

        // 闰年的 2 月 28 日不是他的生日
        assertThat(BirthdayWindow.of(2, 29, LocalDate.of(2028, 2, 28), "AROUND", 0).claimable())
                .isFalse();
    }

    @Test
    void aroundModeSpansMonthBoundary() {
        // 生日 3 月 1 日，半径 3 天 → 2 月 26 日就进入窗口
        Window window = BirthdayWindow.of(3, 1, LocalDate.of(2026, 2, 26), "AROUND", 3);
        assertThat(window.claimable()).isTrue();
        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 2, 26));
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 3, 4));

        assertThat(BirthdayWindow.of(3, 1, LocalDate.of(2026, 2, 25), "AROUND", 3).claimable())
                .isFalse();
    }

    @Test
    void aroundModeSpansYearBoundaryWithCorrectTargetYear() {
        // 生日 1 月 2 日，12 月 30 日进入窗口：归属年份必须是次年的生日
        Window window = BirthdayWindow.of(1, 2, LocalDate.of(2026, 12, 30), "AROUND", 3);
        assertThat(window.claimable()).isTrue();
        assertThat(window.target()).isEqualTo(LocalDate.of(2027, 1, 2));
        assertThat(window.target().getYear()).isEqualTo(2027);
    }

    @Test
    void invalidInputYieldsEmptyWindow() {
        LocalDate today = LocalDate.of(2026, 11, 2);
        assertThat(BirthdayWindow.of(null, 2, today, "MONTH", 3).claimable()).isFalse();
        assertThat(BirthdayWindow.of(2, null, today, "MONTH", 3).claimable()).isFalse();
        assertThat(BirthdayWindow.of(13, 2, today, "MONTH", 3).claimable()).isFalse();
        assertThat(BirthdayWindow.of(0, 2, today, "MONTH", 3).claimable()).isFalse();
        assertThat(BirthdayWindow.of(11, 0, today, "MONTH", 3).claimable()).isFalse();
    }

    @Test
    void dayIsClampedToRealMonthLength() {
        // 4 月 31 日不存在，按 4 月 30 日算，不能抛异常也不能滚到 5 月 1 日
        Window window = BirthdayWindow.of(4, 31, LocalDate.of(2026, 4, 30), "AROUND", 0);
        assertThat(window.claimable()).isTrue();
        assertThat(window.target()).isEqualTo(LocalDate.of(2026, 4, 30));
    }
}
