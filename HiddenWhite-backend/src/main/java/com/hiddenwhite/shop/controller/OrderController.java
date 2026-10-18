package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.service.MemberSessionService;
import com.hiddenwhite.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 点单端订单接口（需登录）。
 *
 * <p><b>归属判定一律以 token 为准</b>：所有方法都从 {@link MemberSessionService} 取当前会员，
 * 不再接受客户端传入 memberId 作为归属依据，从根上消除越权读单 / 改单。
 * 请求体中的 memberId 会被忽略，由服务端用 token 解析出的会员 ID 覆盖。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final MemberSessionService sessionService;

    public OrderController(OrderService orderService, MemberSessionService sessionService) {
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    /** 当前会员的全部订单（进行中 + 历史）。 */
    @GetMapping
    public List<Order> list() {
        return orderService.findAll(callerId());
    }

    /** 当前会员进行中的订单，不存在返回 null。 */
    @GetMapping("/current")
    public Order current() {
        return orderService.current(callerId());
    }

    /** 订单详情；非本人订单返回 403。 */
    @GetMapping("/{id}")
    public Order detail(@PathVariable UUID id) {
        return orderService.findOwned(id, callerId());
    }

    @PostMapping
    public Order create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request, callerId());
    }

    /** 完成取餐；非本人订单返回 403。 */
    @PostMapping("/{id}/complete")
    public Order complete(@PathVariable UUID id) {
        return orderService.complete(id, callerId());
    }

    /**
     * 主动取消订单；非本人订单返回 403。
     *
     * <p>仅允许取消未支付订单。已支付需联系门店走退款流程，此处不开放，
     * 避免「钱收了、订单没了」却无退款记录的资损场景。
     * 重复取消幂等返回当前状态，不报错。
     */
    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable UUID id) {
        return orderService.cancel(id, callerId());
    }

    /** 取当前登录会员 ID；未登录由 SecurityConfig 拦截，这里仅作防御性兜底。 */
    private String callerId() {
        return sessionService.currentMemberId()
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录"));
    }
}
