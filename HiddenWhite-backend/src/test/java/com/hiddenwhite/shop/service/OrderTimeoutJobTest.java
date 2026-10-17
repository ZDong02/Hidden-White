package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时取消任务测试。
 *
 * <p>重点验证「已支付订单被取消时使用 TIMEOUT_UNPICKED 而非 TIMEOUT_UNPAID」——
 * 两者混用会让运营在对账时误以为钱没到账，从而漏退款。
 */
class OrderTimeoutJobTest {

    private ShopRepository repository;
    private OrderTimeoutJob job;

    private static Order order(String memberId, OrderStatus status) {
        OffsetDateTime created = OffsetDateTime.parse("2026-10-17T10:00:00+08:00");
        boolean paid = status.isPaid();
        return new Order(UUID.randomUUID(), memberId,
                List.of(new OrderItem("latte", "拿铁", "标准杯 240 毫升", "热", "全脂奶", 1,
                        new BigDecimal("32"), new BigDecimal("32"))),
                new BigDecimal("32"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("32"),
                1, "001", "20261017001", created, created.plusMinutes(5), status, "",
                null,
                paid ? "WECHAT_JSAPI" : null,
                paid ? "4200001234202610171234567890" : null,
                null,
                null,
                paid ? created.plusMinutes(1) : null,
                null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        repository = mock(ShopRepository.class);
        job = new OrderTimeoutJob(repository);
    }

    @Test
    void cancelsUnpaidOrdersWithTimeoutUnpaidReason() {
        Order unpaid = order("member-a", OrderStatus.PENDING_PAYMENT);
        when(repository.findExpired(any())).thenReturn(List.of(unpaid));
        when(repository.cancel(eq(unpaid.id()), anyString(), any()))
                .thenReturn(order("member-a", OrderStatus.CANCELLED));

        job.cancelAll(List.of(unpaid), OffsetDateTime.parse("2026-10-17T11:00:00+08:00"));

        verify(repository).cancel(eq(unpaid.id()), eq(OrderTimeoutJob.REASON_UNPAID), any());
    }

    @Test
    void cancelsPaidOrdersWithTimeoutUnpickedReasonNotUnpaid() {
        // 关键断言：已支付的订单若被标成 TIMEOUT_UNPAID，运营会以为钱没到账而漏退款
        Order paid = order("member-b", OrderStatus.PAID);
        when(repository.cancel(eq(paid.id()), anyString(), any()))
                .thenReturn(order("member-b", OrderStatus.CANCELLED));

        job.cancelAll(List.of(paid), OffsetDateTime.parse("2026-10-17T11:00:00+08:00"));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(repository).cancel(eq(paid.id()), reason.capture(), any());
        assertThat(reason.getValue()).isEqualTo(OrderTimeoutJob.REASON_UNPICKED)
                .isNotEqualTo(OrderTimeoutJob.REASON_UNPAID);
    }

    @Test
    void createdOrdersAreTreatedAsUnpaid() {
        Order created = order("member-h", OrderStatus.CREATED);
        when(repository.cancel(eq(created.id()), anyString(), any()))
                .thenReturn(order("member-h", OrderStatus.CANCELLED));

        job.cancelAll(List.of(created), OffsetDateTime.parse("2026-10-17T11:00:00+08:00"));

        verify(repository).cancel(eq(created.id()), eq(OrderTimeoutJob.REASON_UNPAID), any());
    }

    @Test
    void oneFailingOrderDoesNotBlockOthers() {
        Order bad = order("member-c", OrderStatus.PAID);
        Order good = order("member-d", OrderStatus.PENDING_PAYMENT);
        when(repository.cancel(eq(bad.id()), anyString(), any())).thenThrow(new RuntimeException("数据库抖动"));
        when(repository.cancel(eq(good.id()), anyString(), any()))
                .thenReturn(order("member-d", OrderStatus.CANCELLED));

        int cancelled = job.cancelAll(List.of(bad, good), OffsetDateTime.parse("2026-10-17T11:00:00+08:00"));

        assertThat(cancelled).isEqualTo(1);
        verify(repository).cancel(eq(good.id()), eq(OrderTimeoutJob.REASON_UNPAID), any());
    }

    @Test
    void alreadyFinalOrdersAreSkippedWithoutError() {
        // 并发场景：店员刚把订单标记完成，定时任务随后扫到它
        Order done = order("member-e", OrderStatus.PAID);
        when(repository.cancel(eq(done.id()), anyString(), any()))
                .thenThrow(new IllegalStateException("订单已处于终态 COMPLETED"));

        int cancelled = job.cancelAll(List.of(done), OffsetDateTime.parse("2026-10-17T11:00:00+08:00"));

        assertThat(cancelled).isZero();
    }

    @Test
    void noExpiredOrdersMeansNoWrites() {
        when(repository.findExpired(any())).thenReturn(List.of());

        job.cancelOverdueOrders();

        verify(repository, never()).cancel(any(), anyString(), any());
    }

    @Test
    void scanFailureIsSwallowedSoSchedulerKeepsRunning() {
        // 定时任务抛异常会导致后续调度被取消，因此必须吞掉并留给下一分钟重试
        when(repository.findExpired(any())).thenThrow(new RuntimeException("连接池耗尽"));

        job.cancelOverdueOrders();

        verify(repository, never()).cancel(any(), anyString(), any());
    }

    @Test
    void mixedBatchCancelsEachWithCorrectReason() {
        Order unpaid = order("member-f", OrderStatus.PENDING_PAYMENT);
        Order paid = order("member-g", OrderStatus.PAID);
        when(repository.cancel(eq(unpaid.id()), anyString(), any()))
                .thenReturn(order("unpaid", OrderStatus.CANCELLED));
        when(repository.cancel(eq(paid.id()), anyString(), any()))
                .thenReturn(order("paid", OrderStatus.CANCELLED));

        job.cancelAll(List.of(unpaid, paid), OffsetDateTime.parse("2026-10-17T11:00:00+08:00"));

        verify(repository, times(1)).cancel(eq(unpaid.id()), eq(OrderTimeoutJob.REASON_UNPAID), any());
        verify(repository, times(1)).cancel(eq(paid.id()), eq(OrderTimeoutJob.REASON_UNPICKED), any());
    }
}