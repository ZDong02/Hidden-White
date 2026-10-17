package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.PriceSummary;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.service.MemberSessionService;
import com.hiddenwhite.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点单端购物车报价接口（需登录）。
 *
 * <p>首单减免依赖「该会员是否已有订单」，因此归属必须以 token 判定：
 * 若沿用请求体里的 memberId，攻击者可通过对比报价结果探测任意会员是否下过单。
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final OrderService orderService;
    private final MemberSessionService sessionService;

    public CartController(OrderService orderService, MemberSessionService sessionService) {
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    @PostMapping("/quote")
    public PriceSummary quote(@Valid @RequestBody CreateOrderRequest request) {
        String callerId = sessionService.currentMemberId()
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录"));
        return orderService.quote(request, callerId);
    }
}
