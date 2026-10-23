package com.hiddenwhite.shop.service;

import com.hiddenwhite.framework.security.JwtService;
import com.hiddenwhite.framework.security.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 点单端会员会话与「两端鉴权隔离」测试。
 *
 * <p>覆盖 2026-10-17 安全加固引入的约定：会员 token 的 subject 必须带 {@code member:} 前缀，
 * 从而使点单端 token 无法通过管理端权限校验，反之亦然。
 */
class MemberSessionServiceTest {

    private JwtService jwtService;
    private TokenStore tokenStore;
    private MemberSessionService sessionService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(randomSecret(), 7200);
        tokenStore = new TokenStore();
        sessionService = new MemberSessionService(jwtService, tokenStore);
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuedTokenResolvesBackToTheSameMember() {
        String token = sessionService.issue("member-42");
        authenticate(token);

        assertThat(sessionService.currentMemberId()).contains("member-42");
    }

    @Test
    void tokenIsRejectedAfterRevoke() {
        String token = sessionService.issue("member-42");
        authenticate(token);
        assertThat(sessionService.currentMemberId()).isPresent();

        sessionService.revokeCurrent();

        assertThat(sessionService.currentMemberId()).isEmpty();
    }

    @Test
    void subjectCarriesMemberPrefixSoAdminEndpointsCannotResolveIt() {
        String token = sessionService.issue("member-42");

        // 会员 subject 必须带前缀：管理端权限切面拿它反查 sys_user 时必然查不到
        assertThat(jwtService.parseUsername(token)).isEqualTo("member:member-42");
    }

    @Test
    void adminStylePrincipalIsNotTreatedAsMember() {
        authenticateAsPrincipal("admin");

        assertThat(sessionService.currentMemberId()).isEmpty();
    }

    @Test
    void unauthenticatedContextYieldsNoMember() {
        assertThat(sessionService.currentMemberId()).isEmpty();
    }

    @Test
    void memberWithEmptyIdIsNotAccepted() {
        // 防御：即使被塞进 "member:" 之后为空，也不应视为有效会员
        authenticateAsPrincipal("member:");

        assertThat(sessionService.currentMemberId()).isEmpty();
    }

    // ---- 内部工具 ----

    private void authenticate(String token) {
        String principal = jwtService.parseUsername(token);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, token, List.of()));
    }

    private void authenticateAsPrincipal(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "token", List.of()));
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
