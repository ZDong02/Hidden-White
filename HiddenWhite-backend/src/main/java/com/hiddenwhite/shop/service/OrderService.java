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

    public Order current(String memberId) {
        return findAll(memberId).stream().filter(order -> order.status() == OrderStatus.IN_PROGRESS).findFirst().orElse(null);
    }

    public synchronized Order create(CreateOrderRequest request) {
        String closedReason = repository.closedReason();
        if (closedReason != null) {
            throw new BusinessException(HttpStatus.CONFLICT, closedReason);
        }
        String owner = normalizeMemberId(request.memberId());
        if (current(owner) != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前有订单正在制作，请先完成取餐");
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
                number, created, ready, OrderStatus.IN_PROGRESS,
                request.note() == null ? "" : request.note().trim(), null);
        return repository.saveOrder(order);
    }

    public PriceSummary quote(CreateOrderRequest request) {
        String owner = normalizeMemberId(request.memberId());
        return calculate(buildItems(request.items()), owner);
    }

    public synchronized Order complete(UUID id, String memberId) {
        Order existing = find(id);
        if (memberId != null && !existing.memberId().equals(normalizeMemberId(memberId))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该订单");
        }
        return markCompleted(existing);
    }

    private Order markCompleted(Order existing) {
        if (existing.status() == OrderStatus.COMPLETED) return existing;
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
