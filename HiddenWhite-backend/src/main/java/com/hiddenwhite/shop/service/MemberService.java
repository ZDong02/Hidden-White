package com.hiddenwhite.shop.service;

import com.hiddenwhite.common.coupon.CouponService;
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
    private final CouponService couponService;

    public MemberService(ShopRepository repository, RedeemService redeemService, CouponService couponService) {
        this.repository = repository;
        this.redeemService = redeemService;
        this.couponService = couponService;
    }

    /**
     * 会员资料。
     *
     * <p><b>券数一律以券表为准</b>：{@code members.coupons} 是早期没有券表时留下的计数列，
     * 它从来没有被任何业务流程维护过，恒为 0。会员卡上的「优惠券」如果读它，
     * 就会出现「会员卡显示 0 张、券包里躺着 2 张」这种自相矛盾。
     * 这里用未使用且未过期的真实张数覆盖它，前端零改动。
     */
    public Member get(String id) {
        Member member = repository.member(id);
        return new Member(member.id(), member.displayName(), member.phone(), member.level(),
                member.cardNumber(), member.points(), couponService.unusedCount(id), member.growthValue());
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
