package com.hiddenwhite.common.coupon;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 领取周期：自然周（周一 00:00 – 周日 24:00，Asia/Shanghai）。
 *
 * <p><b>为什么是自然周而不是滚动 7 天</b>
 * <ul>
 *   <li>自然周可以一句话说清：「每周一 00:00 重置」。滚动 7 天需要顾客自己记住上次
 *       领取的时刻，跨零点、跨时区、换设备都会让「我到底能不能领」变成一个需要推理的问题；</li>
 *   <li>自然周的重置点全局一致，运营做周活动时「本周发出去多少张」与活动周期天然对齐；</li>
 *   <li>幂等键可以直接取「本周周一日期」，一个人的一周最多对应一行记录，
 *       数据库唯一索引就是防重复领取的锁，不依赖任何先查后写。</li>
 * </ul>
 *
 * <p><b>为什么用「周一日期」而不是 ISO 周号</b>：{@code 2026-W53} 这种编号在跨年时会出现
 * （12 月 31 日可能属于下一年的第 1 周），人对它的直觉几乎为零；而 {@code 2026-12-28}
 * 一眼就能看出是哪一周，也天然按时间排序，排查问题不需要换算。
 *
 * <p>周起点用 {@link DayOfWeek#MONDAY}（ISO-8601），不是 {@code Calendar} 默认的周日。
 * 门店在中国，周一开工是共识；用周日当起点会让「周日领的券」和「周一领的券」落在同一周，
 * 顾客会看到「这周已经领过了」却完全想不起来什么时候领的。
 */
public final class CouponWeek {

    /** 幂等键格式：本周周一的日期。定长 10 位，按字符串排序即按时间排序。 */
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private CouponWeek() {
    }

    /** 取指定日期所属的自然周。 */
    public static Week of(LocalDate date) {
        // with(DayOfWeek.MONDAY) 走的是 ISO 的 DAY_OF_WEEK(周一=1) 调整，
        // 跨年时也能正确回到本周一（例如 2027-01-01 周五 → 2026-12-28 周一）
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        return new Week(KEY_FORMAT.format(monday), monday, monday.plusDays(6));
    }

    /** 当前时刻所属的自然周（门店时区）。 */
    public static Week current() {
        return of(LocalDate.now(ZONE));
    }

    /**
     * 一个自然周。
     *
     * @param key  幂等键，本周周一的 {@code yyyy-MM-dd}
     * @param from 周一
     * @param to   周日
     */
    public record Week(String key, LocalDate from, LocalDate to) {

        /** 本周结束的瞬间（下周一 00:00），用于「距重置还有多久」与前端文案。 */
        public LocalDateTime resetAt() {
            return to.plusDays(1).atStartOfDay();
        }

        /** 是否已过期：给定时刻不早于本周重置点。 */
        public boolean hasResetAt(LocalDateTime moment) {
            return !moment.isBefore(resetAt());
        }
    }
}
