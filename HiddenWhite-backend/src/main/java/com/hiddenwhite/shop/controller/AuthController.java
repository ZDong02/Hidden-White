package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.dto.LoginRequest;
import com.hiddenwhite.shop.dto.MemberAuthResponse;
import com.hiddenwhite.shop.dto.MemberRegistrationRequest;
import com.hiddenwhite.shop.service.MemberService;
import com.hiddenwhite.shop.service.MemberSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点单端认证接口。
 *
 * <p>登录 / 注册成功后会签发 JWT（{@code Authorization: Bearer <token>}），
 * 客户端需持久化并在后续请求中携带，服务端据此判定会员归属。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final MemberService memberService;
    private final MemberSessionService sessionService;

    public AuthController(MemberService memberService, MemberSessionService sessionService) {
        this.memberService = memberService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    public MemberAuthResponse register(@Valid @RequestBody MemberRegistrationRequest request) {
        var member = memberService.register(request);
        return new MemberAuthResponse(member, sessionService.issue(member.id()));
    }

    @PostMapping("/login")
    public MemberAuthResponse login(@Valid @RequestBody LoginRequest request) {
        var member = memberService.login(request);
        return new MemberAuthResponse(member, sessionService.issue(member.id()));
    }

    /** 主动注销，使当前 token 立即失效。 */
    @PostMapping("/logout")
    public void logout() {
        sessionService.revokeCurrent();
    }
}
