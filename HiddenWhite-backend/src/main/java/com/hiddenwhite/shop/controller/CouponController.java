package com.hiddenwhite.shop.controller;

import com.hiddenwhite.common.coupon.CouponService;
import com.hiddenwhite.shop.service.MemberSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 点单端优惠券接口（需登录）。
 *
 * <p><b>为什么领取返回整个券包而不是单张券</b>：领取成功后前端要同时更新
 * 「本周已领」状态、券列表和会员卡上的张数。只回一张券会让页面出现
 * 「券列表里还没有刚领的券」这种自相矛盾的瞬间，前端得再补一次请求才能对齐。
 * 一次返回 {@link CouponService.Wallet} 与生日礼遇的 {@code claim} 口径一致
 * （那边也返回整份 {@code BirthdayStatus}）。
 *
 * <p>游客账号一律 401（{@link MemberSessionService#requireAccountMemberId()}）：
 * 券要挂在会员身上，游客账号没有手机号也没有归属意义 ——
 * 对它发券只会产生永远对不上账的记录。
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;
    private final MemberSessionService sessionService;

    public CouponController(CouponService couponService, MemberSessionService sessionService) {
        this.couponService = couponService;
        this.sessionService = sessionService;
    }

    /** 我的券包：本周领取状态 + 券列表 + 重置时间。 */
    @GetMapping
    public CouponService.Wallet wallet() {
        return couponService.wallet(sessionService.requireAccountMemberId());
    }

    /**
     * 结算页选券视图：按「可用 / 不可用」分组下发。
     *
     * @param subtotal 当前订单商品金额；缺省按 0 处理（全部券都会显示为「还差 X 元」），
     *                 不报错 —— 结算页首帧可能还没拿到报价
     */
    @GetMapping("/usable")
    public CouponService.Usable usable(@RequestParam(required = false) BigDecimal subtotal) {
        BigDecimal amount = subtotal == null || subtotal.signum() < 0 ? BigDecimal.ZERO : subtotal;
        return couponService.usable(sessionService.requireAccountMemberId(), amount);
    }

    /**
     * 领取本周优惠券。
     *
     * <p>重复领取返回同一张券（200），不抛错：连点两下不该看到红色提示。
     * 发行量领完 / 持有超限 / 功能关闭才返回 409。
     */
    @PostMapping("/claim")
    public CouponService.Wallet claim() {
        String memberId = sessionService.requireAccountMemberId();
        couponService.claim(memberId);
        return couponService.wallet(memberId);
    }
}
