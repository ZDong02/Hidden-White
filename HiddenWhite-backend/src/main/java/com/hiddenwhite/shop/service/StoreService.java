package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.StoreInfo;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;

/** 门店服务：门店信息查询。 */
@Service
public class StoreService {
    private final ShopRepository repository;

    public StoreService(ShopRepository repository) {
        this.repository = repository;
    }

    public StoreInfo info() {
        return repository.store();
    }
}
