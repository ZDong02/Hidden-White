package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单。
 *
 * <p>支付相关字段面向**微信支付 JSAPI**（当前为沙箱环境）设计：
 * <ul>
 *   <li>{@code transactionId} —— 微信支付交易号，对账与退款必需；</li>
 *   <li>{@code prepayId} —— 统一下单返回的预支付交易会话标识，唤起支付时下发前端；</li>
 *   <li>{@code paidAt} / {@code payChannel} —— 支付确认时间与来源渠道，
 *       回调可能来自用户主动查询补单，不只是异步通知。</li>
 * </ul>
 *
 * <p>取消信息单独成列：{@code cancelReason} 用于区分「超时未支付」与
 * 「超时未取餐」——后者钱已到账，属需要人工退款的异常场景。
 */
public record Order(
        UUID id,
        String memberId,
        List<OrderItem> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal packagingFee,
        BigDecimal total,
        int count,
        String pickup,
        String number,
        OffsetDateTime createdAt,
        OffsetDateTime readyAt,
        OrderStatus status,
        String note,
        OffsetDateTime completedAt,
        /** 支付渠道：WECHAT_JSAPI。发起支付前为 null */
        String payChannel,
        /** 微信支付交易号，支付成功后回填，对账与退款必需 */
        String transactionId,
        /** 统一下单返回的 prepay_id，唤起支付时下发前端，过期或用后作废 */
        String prepayId,
        /** 预支付会话过期时间；超时任务据此清理未支付订单 */
        OffsetDateTime prepayExpireAt,
        /** 支付确认时间；已收款的取消订单仍保留，供对账 */
        OffsetDateTime paidAt,
        /** 退款单号；退款成功后回填 */
        String refundId,
        /** 退款完成时间 */
        OffsetDateTime refundedAt,
        /** 取消时间；非取消订单为 null */
        OffsetDateTime cancelledAt,
        /** 取消原因：TIMEOUT_UNPAID / TIMEOUT_UNPICKED / MANUAL / PAY_FAILED */
        String cancelReason) {
}