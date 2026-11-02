package com.hiddenwhite.shop.controller;

import com.hiddenwhite.common.birthday.BirthdayGiftService;
import com.hiddenwhite.common.birthday.BirthdayStatus;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.domain.PointsLogEntry;
import com.hiddenwhite.shop.dto.BirthdayWriteRequest;
import com.hiddenwhite.shop.exception.BusinessException;
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
    private final MemberService memberService;
    private final MemberSessionService sessionService;
    private final BirthdayGiftService birthdayService;

    public MemberController(MemberService memberService, MemberSessionService sessionService,
                            BirthdayGiftService birthdayService) {
        this.memberService = memberService;
        this.sessionService = sessionService;
        this.birthdayService = birthdayService;
    }

    /** 读取会员资料；路径 id 必须与 token 中的会员一致，否则 403。 */
    @GetMapping("/{id}")
    public Member get(@PathVariable String id) {
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
}
