package com.hiddenwhite.common.birthday;

/**
 * 点单端「生日礼遇」卡片的一次完整状态。
 *
 * <p><b>为什么状态由服务端算好</b>：窗口判定涉及时区、闰年、月末，
 * 一旦下放到客户端，同一个会员在不同机型/时区上会看到不同结论，
 * 而结论直接决定按钮亮不亮。前端只读 {@code state} 与窗口文案，不做任何日期比较。
 *
 * @param state         {@code DISABLED} 未开放 / {@code NO_BIRTHDAY} 未填生日 /
 *                      {@code CLAIMABLE} 可领取 / {@code CLAIMED} 已领取 / {@code NOT_IN_WINDOW} 非窗口期
 * @param windowFrom    本次窗口起（yyyy-MM-dd），未填生日时为 null
 * @param windowTo      本次窗口止（yyyy-MM-dd）
 * @param daysUntil     距生日天数；窗口内为 0
 * @param claim         已领取时的凭证，未领取为 null
 * @param editable      生日是否还能修改（一年一改）
 * @param nextEditableAt 下一次可修改生日的日期（yyyy-MM-dd），{@code editable=true} 时为 null
 */
public record BirthdayStatus(
        boolean enabled,
        String state,
        Integer birthdayMonth,
        Integer birthdayDay,
        String title,
        String subtitle,
        String description,
        String giftType,
        int pointsAmount,
        String windowFrom,
        String windowTo,
        Integer daysUntil,
        Claim claim,
        boolean editable,
        String nextEditableAt) {

    /**
     * 已领取凭证。
     *
     * @param year      归属年份（= 本次生日所在年，不是领取当天所在年）
     * @param amount    实际发放数量
     * @param giftType  发放时的类型快照
     * @param claimedAt 领取时间（yyyy-MM-dd HH:mm）
     */
    public record Claim(int year, int amount, String giftType, String claimedAt) {
    }

    public static final String DISABLED = "DISABLED";
    public static final String NO_BIRTHDAY = "NO_BIRTHDAY";
    public static final String CLAIMABLE = "CLAIMABLE";
    public static final String CLAIMED = "CLAIMED";
    public static final String NOT_IN_WINDOW = "NOT_IN_WINDOW";
}
