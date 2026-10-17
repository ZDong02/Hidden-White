package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.domain.PriceSummary;
import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.domain.ProductOption;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.dto.OrderItemRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.exception.NotFoundException;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单归属与价格计算测试。
 *
 * <p>覆盖 2026-10-17 安全加固引入的核心约定：<b>订单归属一律以服务端解析出的
 * callerId（来自 token）为准，请求体里的 memberId 一律被忽略</b>。
 * 加固前 {@code GET /api/orders?memberId=X} 无需凭证即可列出任意会员订单，
 * 且 complete 的归属判断写作 {@code memberId != null && ...}，省略参数即可绕过。
 */
class OrderServiceTest {

    private static final String ALICE = "member-alice";
    private static final String BOB = "member-bob";

    private ShopRepository repository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShopRepository.class);
        // CatalogService 的 size()/milk()/temperature() 都经 repository.options(type) 解析，
        // 因此只需桩这一个入口，就能让整条规格解析与价格计算路径真实跑通。
        when(repository.options("SIZE")).thenReturn(List.of(
                new ProductOption("标准杯 240 毫升", BigDecimal.ZERO),
                new ProductOption("大杯 360 毫升", new BigDecimal("4"))));
        when(repository.options("MILK")).thenReturn(List.of(
                new ProductOption("全脂奶", BigDecimal.ZERO),
                new ProductOption("低脂奶", new BigDecimal("4"))));
        when(repository.options("TEMPERATURE")).thenReturn(List.of(
                new ProductOption("热", BigDecimal.ZERO),
                new ProductOption("冰", BigDecimal.ZERO)));
        when(repository.product(anyString())).thenReturn(latte());
        when(repository.closedReason()).thenReturn(null);
        when(repository.nextOrderNumber(any(LocalDate.class))).thenReturn("20261017001");

        service = new OrderService(new CatalogService(repository), repository);
    }

    private static Product latte() {
        return new Product("latte", "拿铁", "Latte", "", "", new BigDecimal("32"), "", "", 1, true);
    }

    private static Order orderOf(String memberId, OrderStatus status) {
        OffsetDateTime created = OffsetDateTime.parse("2026-10-17T10:00:00+08:00");
        return new Order(UUID.randomUUID(), memberId,
                List.of(new OrderItem("latte", "拿铁", "标准杯 240 毫升", "热", "全脂奶", 1,
                        new BigDecimal("32"), new BigDecimal("32"))),
                new BigDecimal("32"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("32"),
                1, "001", "20261017001", created, created.plusMinutes(5), status, "", null,
                null, null, null, null, null, null, null, null, null);
    }

    private static CreateOrderRequest request(String memberIdInBody) {
        return new CreateOrderRequest(memberIdInBody,
                List.of(new OrderItemRequest("latte", "标准杯 240 毫升", "热", "全脂奶", 1)), "");
    }

    // ---- 归属校验：本次安全加固的核心 ----

    @Test
    void readingAnotherMembersOrderIsRejected() {
        Order bobOrder = orderOf(BOB, OrderStatus.PAID);
        when(repository.order(bobOrder.id())).thenReturn(bobOrder);

        assertThatThrownBy(() -> service.findOwned(bobOrder.id(), ALICE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void completingAnotherMembersOrderIsRejected() {
        Order bobOrder = orderOf(BOB, OrderStatus.PAID);
        when(repository.order(bobOrder.id())).thenReturn(bobOrder);

        assertThatThrownBy(() -> service.complete(bobOrder.id(), ALICE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerCanReadAndCompleteOwnOrder() {
        Order mine = orderOf(ALICE, OrderStatus.PAID);
        when(repository.order(mine.id())).thenReturn(mine);
        when(repository.complete(any(UUID.class), any(OffsetDateTime.class))).thenReturn(
                new Order(mine.id(), ALICE, mine.items(), mine.subtotal(), mine.discount(),
                        mine.packagingFee(), mine.total(), mine.count(), mine.pickup(), mine.number(),
                        mine.createdAt(), mine.readyAt(), OrderStatus.COMPLETED, mine.note(), mine.readyAt(),
                        mine.payChannel(), mine.transactionId(), mine.prepayId(), mine.prepayExpireAt(),
                        mine.paidAt(), null, null, null, null));

        assertThat(service.findOwned(mine.id(), ALICE)).isNotNull();
        assertThat(service.complete(mine.id(), ALICE).status()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void missingOrderSurfacesNotFound() {
        UUID unknown = UUID.randomUUID();
        when(repository.order(unknown)).thenThrow(new NotFoundException("订单不存在: " + unknown));

        assertThatThrownBy(() -> service.findOwned(unknown, ALICE))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- 请求体 memberId 必须被忽略 ----

    @Test
    void creatingOrderIgnoresMemberIdInRequestBody() {
        when(repository.orders(anyString())).thenReturn(List.of());
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // 客户端声称自己是 BOB，服务端以 token（ALICE）为准落库
        Order created = service.create(request(BOB), ALICE);

        assertThat(created.memberId()).isEqualTo(ALICE);
    }

    @Test
    void quoteIgnoresMemberIdInRequestBody() {
        // 恶意用户用已下单的 BOB 身份报价，试图冒领首单减免
        when(repository.orders(BOB)).thenReturn(List.of(orderOf(BOB, OrderStatus.COMPLETED)));
        when(repository.orders(ALICE)).thenReturn(List.of());

        PriceSummary asBob = service.quote(request(null), BOB);
        PriceSummary asAlice = service.quote(request(BOB), ALICE);

        assertThat(asBob.discount()).isEqualByComparingTo(BigDecimal.ZERO);
        // ALICE 无历史订单 → 有首单减免，证明减免按调用方身份而非请求体判定
        assertThat(asAlice.discount()).isEqualByComparingTo(new BigDecimal("6"));
    }

    // ---- 业务规则 ----

    @Test
    void firstOrderGetsSixYuanDiscount() {
        when(repository.orders(ALICE)).thenReturn(List.of());

        PriceSummary summary = service.quote(request(null), ALICE);

        assertThat(summary.subtotal()).isEqualByComparingTo(new BigDecimal("32"));
        assertThat(summary.discount()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(summary.total()).isEqualByComparingTo(new BigDecimal("26"));
        assertThat(summary.count()).isEqualTo(1);
    }

    @Test
    void returningMemberGetsNoDiscount() {
        when(repository.orders(ALICE)).thenReturn(List.of(orderOf(ALICE, OrderStatus.COMPLETED)));

        PriceSummary summary = service.quote(request(null), ALICE);

        assertThat(summary.discount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.total()).isEqualByComparingTo(new BigDecimal("32"));
    }

    @Test
    void orderingIsBlockedWhenStoreIsClosed() {
        when(repository.closedReason()).thenReturn("今日已打烊，明日 08:00 恢复营业");

        assertThatThrownBy(() -> service.create(request(null), ALICE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).status())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void onlyOneInProgressOrderAllowedPerMember() {
        when(repository.orders(ALICE)).thenReturn(List.of(orderOf(ALICE, OrderStatus.PAID)));

        assertThatThrownBy(() -> service.create(request(null), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待支付或待取餐");
    }

    @Test
    void currentReturnsOnlyInProgressOrder() {
        Order completed = orderOf(ALICE, OrderStatus.COMPLETED);
        Order running = orderOf(ALICE, OrderStatus.PAID);
        when(repository.orders(ALICE)).thenReturn(List.of(completed, running));

        assertThat(service.current(ALICE).id()).isEqualTo(running.id());
    }

    @Test
    void pickupNumberIsDerivedFromOrderNumberByServer() {
        when(repository.orders(ALICE)).thenReturn(List.of());
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(request(null), ALICE);

        // 取餐号取订单号后 3 位，由后端生成，前端不可干预
        assertThat(created.pickup()).isEqualTo("001");
        assertThat(created.number()).isEqualTo("20261017001");
        // 下单即创建，尚未发起支付；发起 JSAPI 下单后才进入 PENDING_PAYMENT
        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.transactionId()).isNull();
        assertThat(created.paidAt()).isNull();
    }

    // ---- 支付相关的不变式 ----

    @Test
    void cannotCompleteOrderBeforePaymentIsConfirmed() {
        // 「没付钱不能标记取餐」：防止漏收，是支付接入后的最后一道闸门
        Order unpaid = orderOf(ALICE, OrderStatus.PENDING_PAYMENT);
        when(repository.order(unpaid.id())).thenReturn(unpaid);

        assertThatThrownBy(() -> service.complete(unpaid.id(), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未支付");
    }

    @Test
    void cancelledOrderCannotBeCompleted() {
        Order cancelled = orderOf(ALICE, OrderStatus.CANCELLED);
        when(repository.order(cancelled.id())).thenReturn(cancelled);

        assertThatThrownBy(() -> service.complete(cancelled.id(), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已取消");
    }

    @Test
    void pendingPaymentStillBlocksMemberFromOrderingAgain() {
        // 已发起支付但未完成的订单必须占用名额，否则顾客可能重复下单
        when(repository.orders(ALICE)).thenReturn(List.of(orderOf(ALICE, OrderStatus.PENDING_PAYMENT)));

        assertThatThrownBy(() -> service.create(request(null), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待支付或待取餐");
    }

    @Test
    void terminalOrdersDoNotBlockNewOrdering() {
        // 终态订单不再占用名额：超时取消后顾客可以重新下单（这正是修复的核心价值）
        when(repository.orders(ALICE)).thenReturn(List.of(
                orderOf(ALICE, OrderStatus.COMPLETED),
                orderOf(ALICE, OrderStatus.CANCELLED)));
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(request(null), ALICE).status()).isEqualTo(OrderStatus.CREATED);
    }
}
