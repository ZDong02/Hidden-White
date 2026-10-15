package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.dto.LoginRequest;
import com.hiddenwhite.shop.dto.MemberRegistrationRequest;
import com.hiddenwhite.shop.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/register")
    public Member register(@Valid @RequestBody MemberRegistrationRequest request) {
        return memberService.register(request);
    }

    @PostMapping("/login")
    public Member login(@Valid @RequestBody LoginRequest request) {
        return memberService.login(request);
    }
}
