package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.StoreInfo;
import com.hiddenwhite.shop.service.StoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store")
public class StoreController {
    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public StoreInfo info() {
        return storeService.info();
    }
}
