package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Order> list(@RequestParam(required = false) String memberId) {
        return orderService.findAll(memberId);
    }

    @GetMapping("/current")
    public Order current(@RequestParam(required = false) String memberId) {
        return orderService.current(memberId);
    }

    @GetMapping("/{id}")
    public Order detail(@PathVariable UUID id) {
        return orderService.find(id);
    }

    @PostMapping
    public Order create(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @PostMapping("/{id}/complete")
    public Order complete(@PathVariable UUID id, @RequestParam(required = false) String memberId) {
        return orderService.complete(id, memberId);
    }
}
