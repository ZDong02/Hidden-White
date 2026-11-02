package com.hiddenwhite.common.birthday;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 生日礼遇的窗口判定 —— 纯函数，不依赖 Spring，也不读数据库。
 *
 * <p><b>为什么单独抽出来</b>：窗口判定有三个容易写错的边界（月末、闰年 2/29、跨年），
 * 而它们又直接决定「能不能白拿 200 积分」。埋在 Service 里就没法用一个测试把
 * 「2 月 29 日出生的人在平年怎么办」钉死。这里输入输出都是值对象，测试可以穷举。
 *
 * <p><b>两种窗口</b>：
 * <ul>
 *   <li>{@code MONTH}：生日所在自然月整月可领（默认）。客诉最少 —— 用户不会因为
 *       "刚好错过那三天"而觉得被耍；</li>
 *   <li>{@code AROUND}：生日当天 ± radius 天，跨月按自然日推算，不做月份裁剪。</li>
 * </ul>
 *
 * <p><b>2 月 29 日</b>：平年归一为 2 月 28 日（{@link #birthdayIn(int, int, int)}）。
 * 采用"提前一天"而不是"顺延到 3 月 1 日"：生日礼遇是福利，宁可早一天，
 * 也不要让用户在 2 月最后一天刷新页面时发现还没有。
 */
public final class BirthdayWindow {

    /** 判定结果。{@code target} 是本次归属的那一个生日（决定礼遇归属年份）。 */
    public record Window(boolean claimable, LocalDate target, LocalDate from, LocalDate to, int daysUntil) {

        /** 未填写生日时的空结果。 */
        public static Window empty() {
            return new Window(false, null, null, null, -1);
        }
    }

    private BirthdayWindow() {
    }

    /**
     * 计算窗口。
     *
     * @param month  生日月份 1-12；null 表示未填写
     * @param day    生日日 1-31；null 表示未填写
     * @param today  今天（由调用方按 Asia/Shanghai 取，保证服务端口径统一）
     * @param mode   {@code MONTH} / {@code AROUND}
     * @param radius AROUND 模式的半径天数（负数按 0 处理）
     */
    public static Window of(Integer month, Integer day, LocalDate today, String mode, int radius) {
        if (month == null || day == null || month < 1 || month > 12 || day < 1) {
            return Window.empty();
        }
        int safeDay = Math.min(day, YearMonth.of(2000, month).lengthOfMonth()); // 2000 是闰年，2/29 不被裁掉
        int safeRadius = Math.max(radius, 0);

        if ("AROUND".equalsIgnoreCase(mode)) {
            return around(month, safeDay, today, safeRadius);
        }
        return monthWindow(month, safeDay, today);
    }

    /** 某年的「生日」（平年 2/29 归一到 2/28）。 */
    public static LocalDate birthdayIn(int year, int month, int day) {
        YearMonth ym = YearMonth.of(year, month);
        return ym.atDay(Math.min(day, ym.lengthOfMonth()));
    }

    private static Window monthWindow(int month, int day, LocalDate today) {
        if (today.getMonthValue() == month) {
            LocalDate target = birthdayIn(today.getYear(), month, day);
            YearMonth ym = YearMonth.from(today);
            // 落在生日当天及之后都算 0：窗口内不再强调倒计时
            int daysUntil = Math.max(0, (int) (target.toEpochDay() - today.toEpochDay()));
            return new Window(true, target, ym.atDay(1), ym.atEndOfMonth(), daysUntil);
        }

        LocalDate target = birthdayIn(today.getYear(), month, day);
        if (target.isBefore(today)) {
            target = birthdayIn(today.getYear() + 1, month, day);
        }
        YearMonth ym = YearMonth.from(target);
        int daysUntil = (int) (target.toEpochDay() - today.toEpochDay());
        return new Window(false, target, ym.atDay(1), ym.atEndOfMonth(), daysUntil);
    }

    private static Window around(int month, int day, LocalDate today, int radius) {
        LocalDate nextTarget = null;
        LocalDate nextStart = null;
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate target = birthdayIn(today.getYear() + offset, month, day);
            LocalDate from = target.minusDays(radius);
            LocalDate to = target.plusDays(radius);
            if (!today.isBefore(from) && !today.isAfter(to)) {
                int daysUntil = Math.max(0, (int) (target.toEpochDay() - today.toEpochDay()));
                return new Window(true, target, from, to, daysUntil);
            }
            // 窗口还没开始的最近一次，用于非窗口期的倒计时（loop 按年份升序，先到先得）
            if (today.isBefore(from) && nextTarget == null) {
                nextTarget = target;
                nextStart = from;
            }
        }
        if (nextTarget == null) {
            nextTarget = birthdayIn(today.getYear() + 1, month, day);
            nextStart = nextTarget.minusDays(radius);
        }
        int daysUntil = (int) (nextTarget.toEpochDay() - today.toEpochDay());
        return new Window(false, nextTarget, nextStart, nextTarget.plusDays(radius), daysUntil);
    }
}
