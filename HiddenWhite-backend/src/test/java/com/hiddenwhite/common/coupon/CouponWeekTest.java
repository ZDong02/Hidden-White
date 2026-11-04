package com.hiddenwhite.common.coupon;

import com.hiddenwhite.common.coupon.CouponWeek.Week;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自然周计算测试。
 *
 * <p>这段逻辑是「每周可领一张」的唯一真源，一旦算错就会直接影响发券：
 * 若周起点用周日（{@code Calendar} 的默认周），周日晚与周一早会被判成同一周，
 * 顾客会看到「这周已经领过了」却完全想不起来何时领的。
 * 跨年那两天（12 月 31 日 / 1 月 1 日）是最容易写错的地方，专门覆盖。
 */
class CouponWeekTest {

    @Test
    void weekStartsOnMondayAndEndsOnSunday() {
        // 2026-11-04 是周三
        Week week = CouponWeek.of(LocalDate.of(2026, 11, 4));

        assertThat(week.from()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(week.to()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(week.key()).isEqualTo("2026-11-02");
    }

    @Test
    void mondayAndSundayShareTheSameWeek() {
        Week monday = CouponWeek.of(LocalDate.of(2026, 11, 2));
        Week sunday = CouponWeek.of(LocalDate.of(2026, 11, 8));

        assertThat(sunday.key()).isEqualTo(monday.key());
    }

    @Test
    void nextMondayBelongsToTheNextWeek() {
        Week sunday = CouponWeek.of(LocalDate.of(2026, 11, 8));
        Week nextMonday = CouponWeek.of(LocalDate.of(2026, 11, 9));

        assertThat(nextMonday.key()).isNotEqualTo(sunday.key());
        assertThat(nextMonday.from()).isEqualTo(sunday.to().plusDays(1));
    }

    @Test
    void resetAtIsNextMondayMidnight() {
        Week week = CouponWeek.of(LocalDate.of(2026, 11, 4));

        assertThat(week.resetAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 0, 0));
        assertThat(week.hasResetAt(LocalDateTime.of(2026, 11, 8, 23, 59, 59))).isFalse();
        assertThat(week.hasResetAt(LocalDateTime.of(2026, 11, 9, 0, 0))).isTrue();
    }

    @Test
    void crossesYearBoundaryWithoutSplittingTheWeek() {
        // 2027-01-01 是周五，仍属于 2026-12-28 那一周 —— 若按自然年切分会拆成两周，
        // 会员在跨年夜就能领两次
        Week newYearDay = CouponWeek.of(LocalDate.of(2027, 1, 1));

        assertThat(newYearDay.from()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(newYearDay.to()).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThat(newYearDay.key()).isEqualTo("2026-12-28");
    }

    @Test
    void keysAreSortableAsDates() {
        String december = CouponWeek.of(LocalDate.of(2026, 12, 30)).key();
        String january = CouponWeek.of(LocalDate.of(2027, 1, 6)).key();

        // 键取「周一日期」的收益：字符串排序即时间排序，运营端按周筛选不必换算周号
        assertThat(december.compareTo(january)).isLessThan(0);
    }
}
