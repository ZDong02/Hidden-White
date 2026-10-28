package com.hiddenwhite.shop.service;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.domain.PointsLogEntry;
import com.hiddenwhite.shop.dto.LoginRequest;
import com.hiddenwhite.shop.dto.MemberRegistrationRequest;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 会员服务：注册、登录、信息与权益查询、积分明细。 */
@Service
public class MemberService {
    private final ShopRepository repository;
    private final RedeemService redeemService;

    public MemberService(ShopRepository repository, RedeemService redeemService) {
        this.repository = repository;
        this.redeemService = redeemService;
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

    /**
     * 积分明细。
     *
     * <p>流水读写都在兑换模块的仓储里（与兑换单同一批表、同一套分页口径），
     * 这里只做透传，避免会员模块再复制一份查询。
     */
    public PageResult<PointsLogEntry> pointsLog(String memberId, PageQuery query) {
        return redeemService.pointsLog(memberId, query);
    }
}
