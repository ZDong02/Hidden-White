package com.hiddenwhite.shop.controller;

import com.hiddenwhite.common.birthday.BirthdayGiftService;
import com.hiddenwhite.common.birthday.BirthdayStatus;
import com.hiddenwhite.common.discount.LevelDiscountService;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.domain.PointsLogEntry;
import com.hiddenwhite.shop.dto.BirthdayWriteRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.exception.NotFoundException;
import com.hiddenwhite.shop.service.MemberSessionService;
import com.hiddenwhite.shop.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 点单端会员接口。
 *
 * <p>{@code GET /api/members/{id}} 需登录且只能读取自己的资料（归属以 token 判定），
 * 避免越权读取他人手机号、卡号、积分。权益列表为公共信息，无需登录。
 *
 * <p>{@code GET /api/members/points-log} 补齐会员页「积分明细」入口：
 * 同样只读本人流水，游客账号拒绝（见 {@link MemberSessionService#requireAccountMemberId()}）。
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {
    /**
     * 本控制器下的固定子路径。
     *
     * <p>{@code @GetMapping("/{id}")} 会把任何**没有对应端点**的单段路径当成会员 ID 吞掉，
     * 随后因为「id 不等于当前登录会员」返回 403「无权查看该会员资料」。
     * 真实场景：新增了 {@code /level-discounts} 但后端进程还跑着旧代码，
     * 客户端收到 403 后会去查 token 与权限 —— 而问题只是接口没部署。
     *
     * <p>列出保留字，让这种情况明确变成 404。新增子路径时记得同步这里。
     */
    private static final Set<String> RESERVED_PATHS =
            Set.of("benefits", "points-log", "discount", "level-discounts", "birthday", "me");

    private final MemberService memberService;
    private final MemberSessionService sessionService;
    private final BirthdayGiftService birthdayService;
    private final LevelDiscountService levelDiscountService;

    public MemberController(MemberService memberService, MemberSessionService sessionService,
                            BirthdayGiftService birthdayService, LevelDiscountService levelDiscountService) {
        this.memberService = memberService;
        this.sessionService = sessionService;
        this.birthdayService = birthdayService;
        this.levelDiscountService = levelDiscountService;
    }

    /** 读取会员资料；路径 id 必须与 token 中的会员一致，否则 403。 */
    @GetMapping("/{id}")
    public Member get(@PathVariable String id) {
        // 走到这里说明这个子路径没有对应的端点 —— 通常是后端进程还没重启到最新代码。
        // 必须显式 404，不能让归属校验把它变成「无权查看该会员资料」：
        // 403 会让人以为是权限问题去查 token，而真实原因是接口没部署。
        if (RESERVED_PATHS.contains(id)) {
            throw new NotFoundException("接口不存在: GET /api/members/" + id);
        }
        String callerId = sessionService.currentMemberId()
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录"));
        if (!callerId.equals(id)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该会员资料");
        }
        return memberService.get(callerId);
    }

    /** 会员权益（公共信息，无需登录）。 */
    @GetMapping("/benefits")
    public List<MemberBenefit> benefits() {
        return memberService.benefits();
    }

    /** 积分明细（分页，按时间倒序）。 */
    @GetMapping("/points-log")
    public PageResult<PointsLogEntry> pointsLog(PageQuery query) {
        return memberService.pointsLog(sessionService.requireAccountMemberId(), query);
    }

    /**
     * 生日礼遇状态。
     *
     * <p>状态（可领取 / 已领取 / 非窗口期）由服务端算好下发：窗口判定涉及时区与闰年，
     * 下放到客户端会让同一个会员在不同机型上看到不同结论，而结论直接决定按钮亮不亮。
     */
    @GetMapping("/birthday")
    public BirthdayStatus birthday() {
        return birthdayService.status(sessionService.requireAccountMemberId());
    }

    /** 填写 / 修改生日（一年一改，冲突时 409 并给出下次可改日期）。 */
    @PutMapping("/me/birthday")
    public BirthdayStatus updateBirthday(@Valid @RequestBody BirthdayWriteRequest request) {
        return birthdayService.updateBirthday(sessionService.requireAccountMemberId(),
                request.month(), request.day());
    }

    /** 领取生日礼遇；重复请求返回同一份「已领取」状态，不报错。 */
    @PostMapping("/birthday/claim")
    public BirthdayStatus claimBirthday() {
        return birthdayService.claim(sessionService.requireAccountMemberId());
    }

    /**
     * 会员折扣：今日是否打折、整周日历、距下一等级还差多少成长值。
     *
     * <p><b>「今天打不打折」由服务端判定</b>，前端不做任何星期与时区比较：
     * 会员在 23:59 打开页面、00:01 下单，客户端算出来的「今天 8.5 折」
     * 与服务端判定的结果必然会对不上，而结论直接决定顾客以为要付多少钱。
     *
     * <p>读会员资料只为拿等级与成长值；折扣本身不落库到会员身上 ——
     * 它是「等级 + 今天星期几」的即时函数，存下来只会产生过期数据。
     */
    @GetMapping("/discount")
    public LevelDiscountService.MemberView discount() {
        Member member = memberService.get(sessionService.requireAccountMemberId());
        return levelDiscountService.view(member.level(), member.growthValue());
    }

    /**
     * 全部等级的折扣方案（「会员折扣」详情页）。
     *
     * <p>与 {@link #discount()} 的分工：那个回答「我今天打几折」，这个回答
     * 「各档分别是什么、我还差多少到下一档」。详情页需要横向对比，
     * 所以返回全部等级而不是只有当前等级。
     *
     * <p>需要登录：等级与成长值属于会员个人数据，未登录时页面只能显示「登录后查看」，
     * 与其给一份没有「当前在哪档」的静态表，不如让前端引导登录。
     */
    @GetMapping("/level-discounts")
    public LevelDiscountService.Catalog levelDiscounts() {
        Member member = memberService.get(sessionService.requireAccountMemberId());
        return levelDiscountService.overview(member.level(), member.growthValue());
    }
}
