package com.hiddenwhite.common.points;

/**
 * 积分变动的业务类型。落库为 {@code member_points_log.biz_type} 字符串。
 *
 * <p>用枚举而不是到处传字符串：流水类型是对账的唯一分类维度，
 * 两处手抄字符串迟早出现 {@code ORDER_AWARD} 与 {@code ORDER_AVARD} 这种
 * 「写错了也不报错、只是永远查不到」的脏数据。
 */
public enum PointsBizType {

    /** 订单支付成功发放。 */
    ORDER_AWARD,

    /** 兑换扣分。 */
    REDEEM,

    /** 会员自助取消兑换，全额退回。 */
    REDEEM_CANCEL,

    /** 兑换单逾期未核销，系统自动退回。 */
    REDEEM_EXPIRE_REFUND,

    /** 下单时积分抵现扣分（阶段 C）。 */
    POINTS_DEDUCT,

    /** 订单取消退回抵现积分（阶段 C）。 */
    POINTS_REFUND,

    /** 管理端直接调整积分。 */
    ADMIN_ADJUST;

    /** 是否为「消耗」方向：流水的正负号应与之一致，便于对账时快速自检。 */
    public boolean isSpend() {
        return this == REDEEM || this == POINTS_DEDUCT;
    }
}
