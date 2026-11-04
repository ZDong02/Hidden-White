package com.hiddenwhite.common.coupon;

/**
 * 单张优惠券的生命周期。
 *
 * <p><b>只有三态，且都落在库里</b>：{@code UNUSED}（待使用）、{@code USED}（已核销）、
 * {@code EXPIRED}（已过期）。刻意不引入 {@code FROZEN}（下单锁券）这样的中间态：
 * 下单 → 支付 → 取餐的窗口只有几分钟，锁券会让「下单后取消」必须精确回滚，
 * 一旦回滚失败券就永久卡死。改用「下单即核销、取消即退回」，
 * 状态机只有单向前进，失败面小得多。
 *
 * <p>过期是<b>惰性结算</b>的结果（没有定时任务）：在券包、可用券、管理端记录
 * 这三个真正会读到状态的入口上先跑一次结算。理由与
 * {@code RedeemService.settleExpired()} 一致 —— 一个日活几十人的点单端
 * 不值得为一次状态翻转引入调度基础设施。
 */
public enum CouponStatus {

    /** 待使用 */
    UNUSED,

    /** 已核销（挂在一笔订单上） */
    USED,

    /** 已过期（超过 expires_at 仍未使用） */
    EXPIRED;

    public boolean isUnused() {
        return this == UNUSED;
    }

    /** 中文标签：错误提示与运营端列表共用，避免两处各写一套翻译。 */
    public String label() {
        return switch (this) {
            case UNUSED -> "待使用";
            case USED -> "已使用";
            case EXPIRED -> "已过期";
        };
    }
}
