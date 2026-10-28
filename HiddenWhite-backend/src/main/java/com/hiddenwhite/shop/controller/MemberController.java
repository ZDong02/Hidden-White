package com.hiddenwhite.shop.controller;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.domain.PointsLogEntry;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.service.MemberSessionService;
import com.hiddenwhite.shop.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public MemberController(MemberService memberService, MemberSessionService sessionService) {
        this.memberService = memberService;
        this.sessionService = sessionService;
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
}
