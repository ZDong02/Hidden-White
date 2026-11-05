package com.hiddenwhite.shop.service;

import com.hiddenwhite.common.coupon.Coupon;
import com.hiddenwhite.common.coupon.CouponService;
import com.hiddenwhite.common.coupon.CouponStatus;
import com.hiddenwhite.common.discount.AppliedDiscount;
import com.hiddenwhite.common.discount.LevelDiscountService;
import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderCancelReason;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.domain.PriceSummary;
import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.domain.ProductOption;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.dto.OrderItemRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.exception.NotFoundException;
import com.hiddenwhite.shop.pay.PayDevModeProperties;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private PayDevModeProperties devMode;
    private CouponService coupons;
    private LevelDiscountService levelDiscounts;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShopRepository.class);
        coupons = mock(CouponService.class);
        // 默认不打折（evaluate 返回 null）：绝大多数用例与会员折扣无关
        levelDiscounts = mock(LevelDiscountService.class);
        // 会员等级影响折扣判定，给一个默认值，免得每个用例都要 stub
        when(repository.memberLevel(anyString())).thenReturn("黄金会员");
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
        when(repository.nextPickupNumber(any(LocalDate.class))).thenReturn("0001");

        // 默认关闭支付开发模式：绝大多数用例应走真实下单路径（停在 CREATED）
        devMode = new PayDevModeProperties();
        service = new OrderService(new CatalogService(repository), repository, devMode, coupons, levelDiscounts);
    }

    /** 打开支付开发模式并重建 service，用于验证「下单即PAID」的分支。 */
    private void enableDevMode() {
        devMode = new PayDevModeProperties();
        devMode.setDevMode(true);
        service = new OrderService(new CatalogService(repository), repository, devMode, coupons, levelDiscounts);
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
                List.of(new OrderItemRequest("latte", "标准杯 240 毫升", "热", "全脂奶", 1)), "", null);
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
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // 客户端声称自己是 BOB，服务端以 token（ALICE）为准落库
        Order created = service.create(request(BOB), ALICE);

        assertThat(created.memberId()).isEqualTo(ALICE);
    }

    @Test
    void quoteIgnoresMemberIdInRequestBody() {
        // 恶意用户用已下单的 BOB 身份报价，试图冒领首单减免
        when(repository.hasOrders(BOB)).thenReturn(true);
        when(repository.hasOrders(ALICE)).thenReturn(false);

        PriceSummary asBob = service.quote(request(null), BOB);
        PriceSummary asAlice = service.quote(request(BOB), ALICE);

        assertThat(asBob.discount()).isEqualByComparingTo(BigDecimal.ZERO);
        // ALICE 无历史订单 → 有首单减免，证明减免按调用方身份而非请求体判定
        assertThat(asAlice.discount()).isEqualByComparingTo(new BigDecimal("6"));
    }

    // ---- 业务规则 ----

    @Test
    void firstOrderGetsSixYuanDiscount() {
        when(repository.hasOrders(ALICE)).thenReturn(false);

        PriceSummary summary = service.quote(request(null), ALICE);

        assertThat(summary.subtotal()).isEqualByComparingTo(new BigDecimal("32"));
        assertThat(summary.discount()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(summary.total()).isEqualByComparingTo(new BigDecimal("26"));
        assertThat(summary.count()).isEqualTo(1);
    }

    @Test
    void returningMemberGetsNoDiscount() {
        when(repository.hasOrders(ALICE)).thenReturn(true);

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
        when(repository.hasActiveOrder(ALICE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(null), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待支付或待取餐");
    }

    @Test
    void currentReturnsOnlyInProgressOrder() {
        Order running = orderOf(ALICE, OrderStatus.PAID);
        // 状态过滤已下推到 SQL，仓储层直接给出占名额的那一张
        when(repository.activeOrder(ALICE)).thenReturn(running);

        assertThat(service.current(ALICE).id()).isEqualTo(running.id());
    }

    @Test
    void pickupNumberIsAllocatedIndependentlyFromOrderNumber() {
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // 取餐号与订单号来自两个独立的取号器，刻意给不同的值以证明二者解耦
        when(repository.nextOrderNumber(any(LocalDate.class))).thenReturn("20261017001");
        when(repository.nextPickupNumber(any(LocalDate.class))).thenReturn("0007");

        Order created = service.create(request(null), ALICE);

        // 取餐号由独立取号器给出，不再是订单号末 3 位
        assertThat(created.pickup()).isEqualTo("0007");
        assertThat(created.number()).isEqualTo("20261017001");
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
        when(repository.hasActiveOrder(ALICE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(null), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待支付或待取餐");
    }

    @Test
    void terminalOrdersDoNotBlockNewOrdering() {
        // 终态订单不再占用名额：超时取消后顾客可以重新下单（这正是修复的核心价值）。
        // 状态过滤已下推到仓储层的 activeOrder / hasActiveOrder，这里显式声明
        // 「没有占名额的订单」，证明服务层不会因为历史上有终态订单就拦下单。
        when(repository.hasActiveOrder(ALICE)).thenReturn(false);
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(request(null), ALICE).status()).isEqualTo(OrderStatus.CREATED);
    }

    // ---- 主动取消：让顾客不必干等超时 ----

    @Test
    void memberCanCancelOwnUnpaidOrderToFreeUpSlotImmediately() {
        // 核心价值：下单后不想要了（或支付未接入），主动取消即可立刻重新下单，
        // 不必被动等待 35 分钟超时
        Order created = orderOf(ALICE, OrderStatus.CREATED);
        when(repository.order(created.id())).thenReturn(created);
        when(repository.cancel(eq(created.id()), eq(OrderCancelReason.MANUAL), any()))
                .thenReturn(orderOf(ALICE, OrderStatus.CANCELLED));

        Order cancelled = service.cancel(created.id(), ALICE);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellingPendingPaymentOrderIsAllowed() {
        // 已发起支付但未完成：钱还没到账，允许放弃
        Order pending = orderOf(ALICE, OrderStatus.PENDING_PAYMENT);
        when(repository.order(pending.id())).thenReturn(pending);
        when(repository.cancel(eq(pending.id()), eq(OrderCancelReason.MANUAL), any()))
                .thenReturn(orderOf(ALICE, OrderStatus.CANCELLED));

        assertThat(service.cancel(pending.id(), ALICE).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void paidOrderCannotBeCancelledByMember() {
        // 资损防线：钱已到账，再取消等于顾客白花钱、门店白做。
        // 这类订单必须走退款流程，由店员介入。
        Order paid = orderOf(ALICE, OrderStatus.PAID);
        when(repository.order(paid.id())).thenReturn(paid);

        assertThatThrownBy(() -> service.cancel(paid.id(), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已支付");
    }

    @Test
    void completedOrderCannotBeCancelled() {
        Order done = orderOf(ALICE, OrderStatus.COMPLETED);
        when(repository.order(done.id())).thenReturn(done);

        assertThatThrownBy(() -> service.cancel(done.id(), ALICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已完成");
    }

    @Test
    void cancellingAlreadyCancelledOrderIsIdempotent() {
        // 重复点击「取消订单」不应报错，直接返回当前状态
        Order cancelled = orderOf(ALICE, OrderStatus.CANCELLED);
        when(repository.order(cancelled.id())).thenReturn(cancelled);

        assertThat(service.cancel(cancelled.id(), ALICE).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellingAnotherMembersOrderIsRejected() {
        // 越权防线：与 complete 一致，归属一律以 token 为准
        Order bobsOrder = orderOf(BOB, OrderStatus.CREATED);
        when(repository.order(bobsOrder.id())).thenReturn(bobsOrder);

        assertThatThrownBy(() -> service.cancel(bobsOrder.id(), ALICE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- 支付开发模式：跳过真实支付，跑通完整流程 ----

    @Test
    void devModeMarksOrderPaidImmediately() {
        enableDevMode();
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // markPaid 会拿 saveOrder 返回的订单 id 回查并置为 PAID
        when(repository.markPaid(any(), anyString(), anyString(), any()))
                .thenReturn(orderOf(ALICE, OrderStatus.PAID));

        service.create(request(null), ALICE);

        // 关键断言：开发模式必须真的推进到已支付，否则前端仍会显示「待支付」而无法测取餐流程
        verify(repository).markPaid(any(), eq("DEV_MODE"),
                startsWith(PayDevModeProperties.DEV_TX_PREFIX), any());
    }

    @Test
    void devModeIsOffByDefaultSoOrdersStayCreated() {
        // 反向防线：不开开发模式时绝不能偷偷置 PAID，否则会在未收钱的情况下放行取餐
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(request(null), ALICE);

        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        verify(repository, never()).markPaid(any(), anyString(), anyString(), any());
    }
    // ---- 优惠券：与首单礼遇互斥，取更优的一边 ----

    /** 构造一张待使用的券：面额可调，门槛 20 元（订单 32 元刚好过线）。 */
    private static Coupon coupon(String id, String amount) {
        LocalDateTime now = LocalDateTime.parse("2026-11-04T10:00:00");
        return new Coupon(id, ALICE, "2026-11-02", "每周好券",
                new BigDecimal(amount), new BigDecimal("20"), CouponStatus.UNUSED,
                now, now.plusDays(7), null, null, BigDecimal.ZERO);
    }

    private static CreateOrderRequest couponRequest(String couponId) {
        return new CreateOrderRequest(null,
                List.of(new OrderItemRequest("latte", "标准杯 240 毫升", "热", "全脂奶", 1)), "", couponId);
    }

    /**
     * 新客同时有首单礼遇与券时，券面额与首单减免相等 → 走首单礼遇，<b>券不核销</b>。
     *
     * <p>这是「不让顾客吃亏」的落点：无条件用券会让新客白丢一张本可以留到下次的券。
     */
    @Test
    void firstOrderBonusWinsTieAndCouponIsKept() {
        when(repository.hasOrders(ALICE)).thenReturn(false);
        when(coupons.resolveForOrder(eq(ALICE), eq("cp-1"), any())).thenReturn(coupon("cp-1", "6"));
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(couponRequest("cp-1"), ALICE);

        assertThat(created.discount()).isEqualByComparingTo(new BigDecimal("6"));
        verify(coupons, never()).markUsed(anyString(), anyString(), anyString(), any());
    }

    /** 老客没有首单礼遇，券被采用 → 核销一次，抵扣金额写进订单。 */
    @Test
    void couponIsConsumedWhenItBeatsFirstOrderBonus() {
        when(repository.hasOrders(ALICE)).thenReturn(true);
        when(coupons.resolveForOrder(eq(ALICE), eq("cp-2"), any())).thenReturn(coupon("cp-2", "6"));
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(couponRequest("cp-2"), ALICE);

        assertThat(created.discount()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(created.total()).isEqualByComparingTo(new BigDecimal("26"));
        verify(coupons).markUsed(eq("cp-2"), eq(ALICE), anyString(), any(BigDecimal.class));
    }

    /** 券面额大于首单减免时，券胜出并核销（面额配置调到 8 元的场景）。 */
    @Test
    void largerCouponBeatsFirstOrderBonus() {
        when(repository.hasOrders(ALICE)).thenReturn(false);
        when(coupons.resolveForOrder(eq(ALICE), eq("cp-3"), any())).thenReturn(coupon("cp-3", "8"));
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(couponRequest("cp-3"), ALICE);

        assertThat(created.discount()).isEqualByComparingTo(new BigDecimal("8"));
        verify(coupons).markUsed(eq("cp-3"), eq(ALICE), anyString(), any(BigDecimal.class));
    }

    /** 取消订单必须把券退回：下单即核销的模型下，不退等于「点一下白丢 6 元」。 */
    @Test
    void cancellingOrderReleasesCoupon() {
        Order created = orderOf(ALICE, OrderStatus.CREATED);
        when(repository.order(created.id())).thenReturn(created);
        // 桩返回同一张订单即可：OrderService 只用返回值里的 id 去退券
        when(repository.cancel(eq(created.id()), any(), any())).thenReturn(created);

        service.cancel(created.id(), ALICE);

        verify(coupons).releaseForOrder(created.id().toString());
    }
    // ---- 会员折扣：与首单礼遇互斥、与优惠券叠加 ----

    /** 构造一次折扣结果：32 元单、rate 0.95 → 减 1.6 元。 */
    private static AppliedDiscount levelDiscount(String amount, String rate, String label) {
        return new AppliedDiscount("黄金会员", "黄金会员", new BigDecimal(rate), 3,
                new BigDecimal(amount), label);
    }

    /** 折扣金额大于首单礼遇时，走折扣 —— 首单礼遇被挤掉，不再计入。 */
    @Test
    void levelDiscountBeatsFirstOrderBonus() {
        when(repository.hasOrders(ALICE)).thenReturn(false);
        when(levelDiscounts.evaluate(eq("黄金会员"), any(), any()))
                .thenReturn(levelDiscount("9.60", "0.700", "黄金会员 7 折"));
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(request(null), ALICE);

        // 32 × 0.7 → 减 9.6，优于首单的 6 元
        assertThat(created.discount()).isEqualByComparingTo(new BigDecimal("9.60"));
        assertThat(created.total()).isEqualByComparingTo(new BigDecimal("22.40"));
    }

    /** 折扣金额小于首单礼遇时，走首单礼遇（新客不吃亏）。 */
    @Test
    void firstOrderBonusWinsWhenLevelDiscountIsSmaller() {
        when(repository.hasOrders(ALICE)).thenReturn(false);
        when(levelDiscounts.evaluate(eq("黄金会员"), any(), any()))
                .thenReturn(levelDiscount("1.60", "0.950", "黄金会员 9.5 折"));
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(request(null), ALICE);

        assertThat(created.discount()).isEqualByComparingTo(new BigDecimal("6"));
        assertThat(created.total()).isEqualByComparingTo(new BigDecimal("26"));
    }

    /**
     * 老客 + 9.5 折 + 6 元券：折扣与券叠加。
     *
     * <p>32 → 30.4（9.5 折）→ 券再减 6 → 24.4，总优惠 7.6。
     * 这条用例锁住「券在折后金额上抵扣」的顺序：反过来（先减券再打折）结果会不同，
     * 且顾客会觉得自己被少算了。
     */
    @Test
    void couponStacksOnTopOfLevelDiscount() {
        when(repository.hasOrders(ALICE)).thenReturn(true);
        when(levelDiscounts.evaluate(eq("黄金会员"), any(), any()))
                .thenReturn(levelDiscount("1.60", "0.950", "黄金会员 9.5 折"));
        when(coupons.resolveForOrder(eq(ALICE), eq("cp-9"), any())).thenReturn(coupon("cp-9", "6"));
        when(repository.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order created = service.create(couponRequest("cp-9"), ALICE);

        assertThat(created.discount()).isEqualByComparingTo(new BigDecimal("7.60"));
        assertThat(created.total()).isEqualByComparingTo(new BigDecimal("24.40"));
        verify(coupons).markUsed(eq("cp-9"), eq(ALICE), anyString(), eq(new BigDecimal("6")));
    }

    /** 报价要给出可逐行渲染的明细：账单不能只显示一个总数。 */
    @Test
    void quoteReturnsDiscountLinesForBill() {
        when(repository.hasOrders(ALICE)).thenReturn(true);
        when(levelDiscounts.evaluate(eq("黄金会员"), any(), any()))
                .thenReturn(levelDiscount("1.60", "0.950", "黄金会员 9.5 折"));
        when(coupons.resolveForOrder(eq(ALICE), eq("cp-5"), any())).thenReturn(coupon("cp-5", "6"));

        PriceSummary summary = service.quote(couponRequest("cp-5"), ALICE);

        assertThat(summary.discounts()).hasSize(2);
        assertThat(summary.discounts().get(0).type()).isEqualTo("LEVEL");
        assertThat(summary.discounts().get(0).amount()).isEqualByComparingTo(new BigDecimal("1.60"));
        assertThat(summary.discounts().get(1).type()).isEqualTo("COUPON");
        assertThat(summary.discount()).isEqualByComparingTo(new BigDecimal("7.60"));
        assertThat(summary.total()).isEqualByComparingTo(new BigDecimal("24.40"));
    }
}
