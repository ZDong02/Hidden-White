package com.hiddenwhite.common.points;

/**
 * 积分流水的关联对象类型。落库为 {@code member_points_log.ref_type}。
 *
 * <p>与 {@code ref_id} 组成「这笔积分是因为哪一件事变的」，
 * 客诉时可从流水直接跳回订单 / 兑换单核对，不必靠时间戳猜。
 */
public enum PointsRefType {

    /** 关联订单（orders.id）。 */
    ORDER,

    /** 关联兑换单（redemptions.id）。 */
    REDEMPTION,

    /** 关联管理端操作（当前用会员 id 定位）。 */
    ADMIN,

    /** 无关联对象（如系统补偿）。 */
    NONE
}
