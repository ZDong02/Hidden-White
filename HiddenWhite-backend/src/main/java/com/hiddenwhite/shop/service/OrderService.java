package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.domain.PriceSummary;
import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.dto.OrderItemRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 订单服务：下单、报价、查询与完成。 */
@Service
public class OrderService {
    public static final String GUEST = "guest";
    private static final BigDecimal PACKAGING_FEE = BigDecimal.ZERO;
    private static final BigDecimal FIRST_ORDER_DISCOUNT = BigDecimal.valueOf(6);
    private static final int PREPARATION_MINUTES = 5;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final CatalogService catalog;
    private final ShopRepository repository;

    public OrderService(CatalogService catalog, ShopRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
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

    public Order current(String memberId) {
        return findAll(memberId).stream().filter(order -> order.status().isBlocking()).findFirst().orElse(null);
    }

    /**
     * 下单。
     *
     * <p>归属以服务端解析出的 {@code callerId}（来自 token）为准，
     * 请求体里的 memberId 一律忽略，避免伪造他人身份下单 / 冒领首单减免。
     */
    public synchronized Order create(CreateOrderRequest request, String callerId) {
        String closedReason = repository.closedReason();
        if (closedReason != null) {
            throw new BusinessException(HttpStatus.CONFLICT, closedReason);
        }
        String owner = normalizeMemberId(callerId);
        if (current(owner) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前有订单待支付或待取餐，请先处理或等待订单超时");
        }
        List<OrderItem> items = buildItems(request.items());
        PriceSummary summary = calculate(items, owner);
        OffsetDateTime created = OffsetDateTime.now(ZONE);
        OffsetDateTime ready = created.plusMinutes(PREPARATION_MINUTES);
        String number = repository.nextOrderNumber(LocalDate.ofInstant(created.toInstant(), ZONE));
        String pickup = number.substring(number.length() - 3);
        Order order = new Order(UUID.randomUUID(), owner, List.copyOf(items), summary.subtotal(), summary.discount(),
                PACKAGING_FEE, summary.total(),
                items.stream().mapToInt(OrderItem::quantity).sum(), pickup,
                number, created, ready, OrderStatus.CREATED,
                request.note() == null ? "" : request.note().trim(), null,
                null, null, null, null, null, null, null, null, null);
        return repository.saveOrder(order);
    }

    /**
     * 报价。首单减免依赖「该会员是否已有订单」，归属以 token 判定，
     * 忽略请求体中的 memberId，避免被用来探测他人是否下过单。
     */
    public PriceSummary quote(CreateOrderRequest request, String callerId) {
        String owner = normalizeMemberId(callerId);
        return calculate(buildItems(request.items()), owner);
    }

    /** 完成订单：强制校验归属，非本人订单一律 403（不再允许 memberId 缺省来跳过校验）。 */
    public synchronized Order complete(UUID id, String callerId) {
        Order existing = find(id);
        assertOwned(existing, callerId);
        return markCompleted(existing);
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
        BigDecimal discount = findAll(owner).isEmpty() ? FIRST_ORDER_DISCOUNT.min(subtotal) : BigDecimal.ZERO;
        return new PriceSummary(subtotal, discount, PACKAGING_FEE,
                subtotal.subtract(discount).add(PACKAGING_FEE),
                items.stream().mapToInt(OrderItem::quantity).sum());
    }

    private String normalizeMemberId(String memberId) {
        return memberId == null || memberId.isBlank() ? GUEST : memberId;
    }
}
