package com.hiddenwhite.shop.domain;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 订单支付与制作状态机。
 *
 * <p>当前对接**微信支付沙箱**（JSAPI 下单）。沙箱与正式环境的差异仅在网关地址与
 * 凭据，业务流转完全一致，因此状态设计面向「先付款后制作」这一通用模型；
 * 未来若改用其他收款渠道，只需在「支付确认」处接入对应回调即可。
 *
 * <pre>
 *   CREATED ──JSAPI 下单──&gt; PENDING_PAYMENT ──支付回调──&gt; PAID ──完成取餐──&gt; COMPLETED
 *      │                          │                        │
 *      └────────超时/放弃──────────┴────超时未取餐───────────┴──&gt; CANCELLED
 * </pre>
 *
 * <p><b>为什么必须有 PENDING_PAYMENT 这一态</b>：JSAPI 下单只是「向微信发起预支付」，
 * 用户可能中途关掉支付页面。此时订单既未付款也未制作，若直接标记为已支付会造成
 * 漏收钱，若直接取消则让用户丢失订单。中间态让「发起过支付但未完成」可被表达。
 *
 * <p><b>核心不变式</b>：
 * <ol>
 *   <li>{@code COMPLETED} 与 {@code CANCELLED} 为终态，不可再流转；</li>
 *   <li>只有 {@code PAID} 能推进到 {@code COMPLETED} —— 「没付钱不能取餐」，
 *       防止漏收的最后一道闸门；</li>
 *   <li>只有 {@code CREATED} / {@code PENDING_PAYMENT} / {@code PAID} 占用
 *       「同一会员仅一张进行中订单」的名额，终态订单不阻塞再次下单。</li>
 * </ol>
 */
public enum OrderStatus {

    /** 已创建，尚未发起支付。保留给「先下单后支付」的流程分支。 */
    CREATED,

    /** 已发起 JSAPI 下单，等待用户支付确认。超时未支付即取消。 */
    PENDING_PAYMENT,

    /** 支付成功，咖啡师制作中。 */
    PAID,

    /** 已完成取餐。终态。 */
    COMPLETED,

    /** 已取消。终态。若曾收款则保留 paidAt 以便对账退款。 */
    CANCELLED;

    /** 订单进入终态后不再变化。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * 该状态下订单是否已收到钱。
     *
     * <p>终态保留此语义：已付款后被取消的订单仍算收过钱，
     * 统计与退款都必须把它算进去，否则会漏退。
     */
    public boolean isPaid() {
        return this == PAID || this == COMPLETED || this == CANCELLED;
    }

    /** 是否占用「同一会员仅一张进行中订单」的名额。 */
    public boolean isBlocking() {
        return this == CREATED || this == PENDING_PAYMENT || this == PAID;
    }

    /** 是否处于「已发起支付但尚未确认」的可超时态。 */
    public boolean isAwaitingPayment() {
        return this == CREATED || this == PENDING_PAYMENT;
    }

    /**
     * 校验流转是否合法，非法时抛出说明性异常。
     *
     * <p>状态机的唯一入口。所有状态变更都应经由它，避免规则散落在各处的
     * if 判断里、在后续迭代中产生互相矛盾的判断。
     */
    public void assertCanTransitionTo(OrderStatus target) {
        if (this == target) {
            return;
        }
        if (isTerminal()) {
            throw new IllegalStateException("订单已处于终态 " + this + "，不能再变更为 " + target);
        }
        boolean allowed = switch (this) {
            case CREATED -> target == PENDING_PAYMENT || target == CANCELLED;
            case PENDING_PAYMENT -> target == PAID || target == CANCELLED;
            case PAID -> target == COMPLETED || target == CANCELLED;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("非法状态流转: " + this + " -> " + target);
        }
    }

    /**
     * 订单从创建时刻起的默认有效期。
     *
     * <p>取「预计制作完成时间 + 30 分钟缓冲」：制作超时后顾客可能仍在门店等待，
     * 过早取消会让顾客白等；过晚则该会员会被订单长期阻塞，无法再次下单。
     *
     * <p>该规则是 {@code OrderStatus} 的唯一实现点，SQL 与定时任务都不得另写一份阈值。
     */
    public static Duration timeoutAfter(OffsetDateTime createdAt, OffsetDateTime readyAt) {
        Duration preparation = Duration.between(createdAt, readyAt);
        return preparation.plusMinutes(30);
    }
}