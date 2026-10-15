package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.dto.LoginRequest;
import com.hiddenwhite.shop.dto.MemberRegistrationRequest;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 会员服务：注册、登录、信息与权益查询。 */
@Service
public class MemberService {
    private final ShopRepository repository;

    public MemberService(ShopRepository repository) {
        this.repository = repository;
    }

    public Member get(String id) {
        return repository.member(id);
    }

    public Member register(MemberRegistrationRequest request) {
        return repository.register(request);
    }

    public Member login(LoginRequest request) {
        return repository.login(request);
    }

    public List<MemberBenefit> benefits() {
        return repository.benefits();
    }
}
