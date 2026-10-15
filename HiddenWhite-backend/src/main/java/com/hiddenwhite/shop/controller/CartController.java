package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.PriceSummary;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final OrderService orderService;

    public CartController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/quote")
    public PriceSummary quote(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.quote(request);
    }
}
