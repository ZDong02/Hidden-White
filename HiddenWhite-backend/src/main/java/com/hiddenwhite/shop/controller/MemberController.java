package com.hiddenwhite.shop.controller;

import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.dto.MemberRegistrationRequest;
import com.hiddenwhite.shop.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/{id}")
    public Member get(@PathVariable String id) {
        return memberService.get(id);
    }

    @GetMapping("/benefits")
    public List<MemberBenefit> benefits() {
        return memberService.benefits();
    }

    @PostMapping
    public Member register(@Valid @RequestBody MemberRegistrationRequest request) {
        return memberService.register(request);
    }
}
