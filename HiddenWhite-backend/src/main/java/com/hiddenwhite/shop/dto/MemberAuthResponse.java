package com.hiddenwhite.shop.dto;

import com.hiddenwhite.shop.domain.Member;

/**
 * 点单端登录 / 注册响应：会员资料 + 访问令牌。
 *
 * <p>客户端后续所有需要归属校验的请求都必须带上 {@code Authorization: Bearer <token>}，
 * 服务端据此判定会员身份，不再接受客户端明文传入 memberId 作为归属依据。
 */
public record MemberAuthResponse(Member member, String token) {
}
