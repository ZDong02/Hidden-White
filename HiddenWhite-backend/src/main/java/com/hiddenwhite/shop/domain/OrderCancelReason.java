package com.hiddenwhite.shop.domain;

/**
 * 订单取消原因。
 *
 * <p><b>为什么要区分</b>：同为「订单没了」，不同原因对钱的影响完全相反。
 * 门店运营看到 {@code TIMEOUT_UNPAID} 知道钱没到账、直接取消无损失；
 * 看到 {@code TIMEOUT_UNPICKED} 知道<b>钱已经收了</b>，必须安排退款。
 * 若混用原因，运营容易误以为钱没到账而漏退——这是真实的资损风险。
 *
 * <p>取值与 {@code database/init.sql} 中 {@code orders.cancel_reason} 列的注释保持一致：
 * {@code TIMEOUT_UNPAID / TIMEOUT_UNPICKED / MANUAL / PAY_FAILED}。
 * 新增原因时两处同步，否则运营侧看到的值无法解读。
 *
 * <p>该类是取消原因的唯一实现点，SQL 与业务代码都不得另写字面量。
 */
public final class OrderCancelReason {

    /** 超时未支付：钱没到账，直接取消无损失。 */
    public static final String TIMEOUT_UNPAID = "TIMEOUT_UNPAID";

    /** 超时未取餐：<b>钱已收</b>，属异常场景，需人工对账与退款。 */
    public static final String TIMEOUT_UNPICKED = "TIMEOUT_UNPICKED";

    /** 顾客主动取消：下单后发现不需要，或支付未接入时放弃。钱未到账。 */
    public static final String MANUAL = "MANUAL";

    /** 支付失败：由支付流程在明确失败时置入，与顾客主动放弃区分。 */
    public static final String PAY_FAILED = "PAY_FAILED";

    private OrderCancelReason() {
    }
}