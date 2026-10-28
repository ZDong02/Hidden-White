package com.hiddenwhite.shop.controller;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.shop.domain.Redemption;
import com.hiddenwhite.shop.service.MemberSessionService;
import com.hiddenwhite.shop.service.RedeemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分兑换接口（点单端）。
 *
 * <p>全部需要会员身份：归属一律由 token 判定（{@link MemberSessionService}），
 * 路径里不出现 memberId —— 客户端传谁的 id 都不影响结果。
 * 游客账号与未登录一样返回 401，前端据此引导登录。
 *
 * <p>没有「兑换品详情」接口：兑换链路是「看列表 → 立即兑」，
 * 卡片上已有名称、积分价与规则说明，多一个详情页只会多一次跳转。
 */
@RestController
@RequestMapping("/api/redeem")
public class RedeemController {

    private final RedeemService redeemService;
    private final MemberSessionService sessionService;

    public RedeemController(RedeemService redeemService, MemberSessionService sessionService) {
        this.redeemService = redeemService;
        this.sessionService = sessionService;
    }

    /** 可兑列表：附带当前积分与每项的「可否兑换」判定。 */
    @GetMapping("/products")
    public RedeemService.Catalog products() {
        return redeemService.catalog(sessionService.requireAccountMemberId());
    }

    /** 兑换。成功返回兑换单（含核销码）。 */
    @PostMapping("/products/{id}/exchange")
    public Redemption exchange(@PathVariable String id) {
        return redeemService.exchange(sessionService.requireAccountMemberId(), id);
    }

    /** 我的兑换单（分页）。 */
    @GetMapping("/orders")
    public PageResult<Redemption> orders(PageQuery query) {
        return redeemService.orders(sessionService.requireAccountMemberId(), query);
    }

    /** 单张兑换单（回看核销码）。 */
    @GetMapping("/orders/{id}")
    public Redemption order(@PathVariable String id) {
        return redeemService.order(sessionService.requireAccountMemberId(), id);
    }

    /** 自助取消（仅待核销），积分全额退回。 */
    @PostMapping("/orders/{id}/cancel")
    public Redemption cancel(@PathVariable String id) {
        return redeemService.cancel(sessionService.requireAccountMemberId(), id);
    }
}
