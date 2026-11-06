package com.hiddenwhite.common.upsize;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 升杯额度的归属周期：自然月（1 日 00:00 – 月末 24:00，Asia/Shanghai）。
 *
 * <p><b>为什么是自然月</b>：与优惠券的「自然周」同一套理由——重置点全局一致，
 * 一句话就能说清「每月 1 号 0 点恢复」；滚动 30 天需要顾客自己记上次使用的时刻。
 *
 * <p><b>为什么存 {@code yyyy-MM} 而不是直接按 {@code created_at} 范围查</b>：
 * 额度统计是「按月分组」，月份是业务概念而不是时间区间。存成 {@code 2026-11}
 * 后，一次 {@code GROUP BY month_key} 即可，还走得上
 * {@code (member_id, month_key, status)} 索引；若在 SQL 里写
 * {@code DATE_FORMAT(created_at,'%Y-%m')} 会让索引失效（项目既有约定：不对列套函数）。
 */
public final class UpsizeMonth {

    private UpsizeMonth() {
    }

    /**
     * 指定日期所属自然月的键，形如 {@code 2026-11}。
     *
     * <p>{@link YearMonth#toString()} 的输出恰好就是 {@code yyyy-MM}（月份补零），
     * 定长 7 位，按字符串排序即按时间排序。
     */
    public static String keyOf(LocalDate date) {
        return YearMonth.from(date).toString();
    }

    /** 当前自然月（业务时区，不跟随服务器默认时区）。 */
    public static String currentKey() {
        return keyOf(LocalDate.now(ZONE));
    }
}
