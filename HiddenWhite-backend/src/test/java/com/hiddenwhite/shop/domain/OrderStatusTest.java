package com.hiddenwhite.shop.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单状态机测试。
 *
 * <p>状态机是支付环节的规则中心。流转规则若被后续迭代改坏，后果是
 * 「没付钱能取餐」或「已付款订单被丢弃」，因此逐条锁定不变式。
 *
 * <p>当前对接微信支付沙箱（JSAPI），五态：CREATED / PENDING_PAYMENT /
 * PAID / COMPLETED / CANCELLED。
 */
class OrderStatusTest {

    @Test
    void terminalStatesCannotTransitionFurther() {
        for (OrderStatus terminal : new OrderStatus[]{OrderStatus.COMPLETED, OrderStatus.CANCELLED}) {
            for (OrderStatus target : OrderStatus.values()) {
                // 自反是允许的（重复操作属幂等），其余一律拒绝
                if (terminal == target) {
                    continue;
                }
                assertThatThrownBy(() -> terminal.assertCanTransitionTo(target))
                        .as("%s 不应能流转到 %s", terminal, target)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test
    void pendingPaymentCannotBecomeCompletedDirectly() {
        // 核心不变式：不能跳过「已支付」直接完成，否则等于允许未付款就取餐
        assertThatThrownBy(() -> OrderStatus.PENDING_PAYMENT.assertCanTransitionTo(OrderStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createdCannotBecomePaidDirectly() {
        // 必须先发起 JSAPI 下单进入 PENDING_PAYMENT，由支付回调才能确认 PAID
        assertThatThrownBy(() -> OrderStatus.CREATED.assertCanTransitionTo(OrderStatus.PAID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThatCode(() -> OrderStatus.CREATED.assertCanTransitionTo(OrderStatus.PENDING_PAYMENT))
                .doesNotThrowAnyException();
        assertThatCode(() -> OrderStatus.PENDING_PAYMENT.assertCanTransitionTo(OrderStatus.PAID))
                .doesNotThrowAnyException();
        assertThatCode(() -> OrderStatus.PAID.assertCanTransitionTo(OrderStatus.COMPLETED))
                .doesNotThrowAnyException();
    }

    @Test
    void anyNonTerminalStateCanBeCancelled() {
        // 支付回调迟到、顾客放弃支付、店员手工取消都需要这条通路
        for (OrderStatus from : new OrderStatus[]{
                OrderStatus.CREATED, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID}) {
            assertThatCode(() -> from.assertCanTransitionTo(OrderStatus.CANCELLED))
                    .as("%s 应可取消", from)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void transitioningToSameStateIsIdempotent() {
        // 微信支付通知会重复投递，同状态流转必须允许，否则第二次回调会抛异常
        for (OrderStatus status : OrderStatus.values()) {
            assertThatCode(() -> status.assertCanTransitionTo(status))
                    .as("%s → %s 应允许", status, status)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void blockingStatesAreExactlyTheUnfinishedOnes() {
        assertThat(OrderStatus.CREATED.isBlocking()).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.isBlocking()).isTrue();
        assertThat(OrderStatus.PAID.isBlocking()).isTrue();
        assertThat(OrderStatus.COMPLETED.isBlocking()).isFalse();
        assertThat(OrderStatus.CANCELLED.isBlocking()).isFalse();
    }

    @Test
    void awaitingPaymentCoversBothPrePaymentStates() {
        // 超时任务据此区分「还没付钱」与「已付钱但没取餐」
        assertThat(OrderStatus.CREATED.isAwaitingPayment()).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.isAwaitingPayment()).isTrue();
        assertThat(OrderStatus.PAID.isAwaitingPayment()).isFalse();
    }

    @Test
    void cancelledPaidOrderStillCountsAsPaidForReconciliation() {
        // 取消的已付款订单仍需参与对账，否则退款时会漏掉这笔钱
        assertThat(OrderStatus.CANCELLED.isPaid()).isTrue();
        assertThat(OrderStatus.PAID.isPaid()).isTrue();
        assertThat(OrderStatus.COMPLETED.isPaid()).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.isPaid()).isFalse();
        assertThat(OrderStatus.CREATED.isPaid()).isFalse();
    }

    @Test
    void timeoutIsPreparationPlusThirtyMinutesBuffer() {
        OffsetDateTime created = OffsetDateTime.parse("2026-10-17T10:00:00+08:00");
        OffsetDateTime ready = created.plusMinutes(5);

        assertThat(OrderStatus.timeoutAfter(created, ready)).isEqualTo(Duration.ofMinutes(35));
    }

    @Test
    void timeoutGrowsWithPreparationTime() {
        // 慢单（如手冲）需要更长有效期，否则会被过早取消
        OffsetDateTime created = OffsetDateTime.parse("2026-10-17T10:00:00+08:00");

        assertThat(OrderStatus.timeoutAfter(created, created.plusMinutes(10)))
                .isEqualTo(Duration.ofMinutes(40));
    }
}