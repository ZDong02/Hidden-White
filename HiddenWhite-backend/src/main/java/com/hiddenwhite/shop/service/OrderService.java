package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderCancelReason;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.domain.PriceSummary;
import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.dto.OrderItemRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.pay.PayDevModeProperties;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/** 订单服务：下单、报价、查询与完成。 */
@Service
public class OrderService {
    public static final String GUEST = "guest";
    private static final BigDecimal PACKAGING_FEE = BigDecimal.ZERO;
    private static final BigDecimal FIRST_ORDER_DISCOUNT = BigDecimal.valueOf(6);
    private static final int PREPARATION_MINUTES = 5;

    /** 开发模式下的支付渠道标记。对账时一眼可辨不是真实微信支付。 */
    private static final String DEV_PAY_CHANNEL = "DEV_MODE";

    private final CatalogService catalog;
    private final ShopRepository repository;
    private final PayDevModeProperties devMode;

    public OrderService(CatalogService catalog, ShopRepository repository, PayDevModeProperties devMode) {
        this.catalog = catalog;
        this.repository = repository;
        this.devMode = devMode;
    }

    public List<Order> findAll(String memberId) {
        return repository.orders(normalizeMemberId(memberId));
    }

    public Order find(UUID id) {
        return repository.order(id);
    }

    /** 读取订单并校验归属：非本人订单直接 403，避免越权读单。 */
    public Order findOwned(UUID id, String memberId) {
        Order order = find(id);
        assertOwned(order, memberId);
        return order;
    }

    /** 当前占名额的订单；状态过滤下推到 SQL，不读整段历史。 */
    public Order current(String memberId) {
        return repository.activeOrder(normalizeMemberId(memberId));
    }

    /**
     * 下单。
     *
     * <p>归属以服务端解析出的 {@code callerId}（来自 token）为准，
     * 请求体里的 memberId 一律忽略，避免伪造他人身份下单 / 冒领首单减免。
     *
     * <p><b>为什么这里必须 synchronized</b>：订单号与取餐号都是「先查当日最大值再 +1」
     * 的读—写序列，这两步之间没有数据库层面的唯一约束替我们兜底（{@code pickup} 列上
     * 没有唯一索引，{@code order_number} 上虽有却只会报错而非自动换号）。
     * 单实例部署下用方法级锁把取号串起来最省事。
     *
     * <p>代价是下单彼此串行，因此方法体内只能做必要的数据库访问 ——
     * 首单判定与名额判定都改成了 {@code LIMIT 1} 探测（见 {@code calculate} 与本方法首段），
     * 不再在锁内读整段订单历史。
     *
     * <p><b>已知边界</b>：多实例部署时该锁不跨进程，取号需改为数据库序列或
     * 「唯一索引 + 重试」。当前为单店单实例，暂不需要。
     */
    public synchronized Order create(CreateOrderRequest request, String callerId) {
        String closedReason = repository.closedReason();
        if (closedReason != null) {
            throw new BusinessException(HttpStatus.CONFLICT, closedReason);
        }
        String owner = normalizeMemberId(callerId);
        // 只探测「是否存在占名额的订单」，不再把整段订单历史连同明细读回来做 null 判断。
        if (repository.hasActiveOrder(owner)) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前有订单待支付或待取餐，请先处理或等待订单超时");
        }
        List<OrderItem> items = buildItems(request.items());
        PriceSummary summary = calculate(items, owner);
        OffsetDateTime created = OffsetDateTime.now(ZONE);
        OffsetDateTime ready = created.plusMinutes(PREPARATION_MINUTES);
        LocalDate orderDate = LocalDate.ofInstant(created.toInstant(), ZONE);
        String number = repository.nextOrderNumber(orderDate);
        // 取餐号独立取号：1~9999 每日循环，不再从订单号尾部派生。
        // 原实现 orderNumber.substring(len-3) 有两个硬伤：上限只有 999，
        // 且取餐号随订单号跨日重置，门店叫号时分不清是哪一天的号。
        String pickup = repository.nextPickupNumber(orderDate);
        Order order = new Order(UUID.randomUUID(), owner, List.copyOf(items), summary.subtotal(), summary.discount(),
                PACKAGING_FEE, summary.total(),
                items.stream().mapToInt(OrderItem::quantity).sum(), pickup,
                number, created, ready, OrderStatus.CREATED,
                request.note() == null ? "" : request.note().trim(), null,
                null, null, null, null, null, null, null, null, null);
        Order saved = repository.saveOrder(order);

        // 开发模式：跳过真实支付，直接推进到 PAID，
        // 让「下单 → 制作中 → 已取餐」的完整流程在没有支付凭据时也能跑通。
        if (devMode.isDevMode()) {
            saved = repository.markPaid(saved.id(), DEV_PAY_CHANNEL,
                    PayDevModeProperties.DEV_TX_PREFIX + UUID.randomUUID(), created);
        }
        return saved;
    }

    /**
     * 报价。首单减免依赖「该会员是否已有订单」，归属以 token 判定，
     * 忽略请求体中的 memberId，避免被用来探测他人是否下过单。
     */
    public PriceSummary quote(CreateOrderRequest request, String callerId) {
        String owner = normalizeMemberId(callerId);
        return calculate(buildItems(request.items()), owner);
    }

    /**
     * 完成订单：强制校验归属，非本人订单一律 403（不再允许 memberId 缺省来跳过校验）。
     *
     * <p>不加 {@code synchronized}：并发安全由 {@code ShopRepository.complete} 的
     * 条件更新（{@code WHERE status='PAID'}）保证，两个并发请求只有一个能命中，
     * 另一个拿到 0 行后按「已是 COMPLETED」幂等返回。原先的锁只是把不同顾客的
     * 取餐动作排到同一把锁上，还会被正在下单的请求挡住。
     */
    public Order complete(UUID id, String callerId) {
        Order existing = find(id);
        assertOwned(existing, callerId);
        return markCompleted(existing);
    }

    /**
     * 主动取消订单。
     *
     * <p><b>为什么必须有这条路径</b>：改造前只有超时任务能取消订单，意味着顾客下错单、
     * 支付未接入、或临时不想要了，唯一出路是<b>被动等待 35 分钟</b>超时
     * （制作 5 分钟 + 30 分钟缓冲）。那段时间里该会员被「仅一张进行中订单」
     * 的名额锁住，既不能重新下单，也不能确认取餐——形成一个体验极差的死等。
     * 提供主动取消让顾客立刻释放名额。
     *
     * <p><b>已付款的订单不能由前端直接取消</b>：钱已经到账，再取消等于让门店白做、
     * 顾客白花钱。此类订单必须走退款流程（{@code ShopRepository.markRefunded}），
     * 由店员介入，不给前端开这个口子。
     *
     * <p>不加 {@code synchronized}，理由同 {@link #complete}：真正的互斥在
     * {@code ShopRepository.cancel} 的条件更新上，重复取消会幂等返回当前状态。
     */
    public Order cancel(UUID id, String callerId) {
        Order existing = find(id);
        assertOwned(existing, callerId);

        if (existing.status() == OrderStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单已完成取餐，无法取消");
        }
        // 兼容已取消：重复点击直接返回当前状态，不报错
        if (existing.status() == OrderStatus.CANCELLED) {
            return existing;
        }
        if (existing.status().isPaid()) {
            // PAID 分支走到这里说明是已付款未取餐，属于需要人工对账的异常场景
            throw new BusinessException(HttpStatus.CONFLICT,
                    "订单已支付，请联系门店协助取消与退款");
        }
        existing.status().assertCanTransitionTo(OrderStatus.CANCELLED);
        return repository.cancel(existing.id(), OrderCancelReason.MANUAL, OffsetDateTime.now(ZONE));
    }

    /** 归属校验：订单必须属于当前登录会员。 */
    private void assertOwned(Order order, String memberId) {
        if (!order.memberId().equals(normalizeMemberId(memberId))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该订单");
        }
    }

    /**
     * 完成取餐。
     *
     * <p>仅 {@code PAID} 可完成——「没付钱不能标记取餐」是状态机的核心不变式，
     * 防止漏收。已超时取消的订单给出明确提示，区分「钱没收到」与「钱收到了需退款」。
     */
    private Order markCompleted(Order existing) {
        if (existing.status() == OrderStatus.COMPLETED) {
            return existing;
        }
        if (existing.status() == OrderStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "TIMEOUT_UNPICKED".equals(existing.cancelReason())
                            ? "订单已超时取消，如已完成付款请联系门店协助退款"
                            : "订单已取消，无法完成取餐");
        }
        if (existing.status().isAwaitingPayment()) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单尚未支付完成，无法标记取餐");
        }
        existing.status().assertCanTransitionTo(OrderStatus.COMPLETED);
        return repository.complete(existing.id(), OffsetDateTime.now(ZONE));
    }

    private List<OrderItem> buildItems(List<OrderItemRequest> requests) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest request : requests) {
            Product product = catalog.product(request.productId());
            String size = request.size() == null || request.size().isBlank() ? CatalogService.STANDARD_SIZE : request.size();
            String temperature = request.temperature() == null || request.temperature().isBlank() ? CatalogService.HOT : request.temperature();
            catalog.temperature(temperature);
            BigDecimal unitPrice = product.price().add(catalog.size(size).price());
            String milk;
            if (product.hasMilk()) {
                milk = request.milk() == null || request.milk().isBlank() ? "全脂奶" : request.milk();
                unitPrice = unitPrice.add(catalog.milk(milk).price());
            } else {
                milk = CatalogService.NO_MILK;
            }
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
            items.add(new OrderItem(product.id(), product.name(), size, temperature, milk,
                    request.quantity(), unitPrice, lineTotal));
        }
        return items;
    }

    private PriceSummary calculate(List<OrderItem> items, String owner) {
        BigDecimal subtotal = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 首单判定只需一个布尔值；`LIMIT 1` 探测与历史订单量无关，
        // 而原先的 findAll(owner).isEmpty() 会把历史订单与明细全量读回再丢掉。
        BigDecimal discount = repository.hasOrders(owner) ? BigDecimal.ZERO : FIRST_ORDER_DISCOUNT.min(subtotal);
        return new PriceSummary(subtotal, discount, PACKAGING_FEE,
                subtotal.subtract(discount).add(PACKAGING_FEE),
                items.stream().mapToInt(OrderItem::quantity).sum());
    }

    private String normalizeMemberId(String memberId) {
        return memberId == null || memberId.isBlank() ? GUEST : memberId;
    }
}
