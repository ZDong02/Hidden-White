package com.hiddenwhite.shop.service;

import com.hiddenwhite.framework.security.JwtService;
import com.hiddenwhite.framework.security.TokenStore;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 会员会话服务：为点单端签发 / 校验 JWT。
 *
 * <p>复用管理端既有的 {@link JwtService} + {@link TokenStore}（内存态，重启需重新登录），
 * 不引入新依赖，也不引入 Redis。
 *
 * <p><b>与管理端账号的隔离约定</b>：token 的 subject 一律写成 {@code member:<会员ID>}，
 * 而管理端 subject 是 sys_user.username（形如 admin / chensy）。两者在同一套密钥下签发，
 * 靠前缀区分，使得：
 * <ul>
 *   <li>管理端权限切面 {@code PermissionAspect} 用 principal 反查 {@code sys_user}，
 *       {@code member:xxx} 永远查不到该用户 → 点单端 token 打不进管理端接口；</li>
 *   <li>反之 admin 的 username 不会命中 {@code member:} 前缀 → 管理端 token 打不进点单端接口。</li>
 * </ul>
 * 因此不要移除这个前缀，它是两端鉴权隔离的关键。
 */
@Service
public class MemberSessionService {

    /** 会员 subject 前缀，隔离管理端账号。 */
    static final String SUBJECT_PREFIX = "member:";

    private final JwtService jwtService;
    private final TokenStore tokenStore;

    public MemberSessionService(JwtService jwtService, TokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    /** 为会员签发 token，并登记到内存 token 表。 */
    public String issue(String memberId) {
        String token = jwtService.generateToken(null, SUBJECT_PREFIX + memberId);
        tokenStore.store(token, SUBJECT_PREFIX + memberId, jwtService.expiresSeconds());
        return token;
    }

    /** 注销当前请求携带的 token（若存在）。 */
    public void revokeCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token) {
            tokenStore.revoke(token);
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * 取出当前登录会员 ID；未登录或 token 不属于会员体系时返回空。
     * Controller 一律以本方法的结果为归属判定依据，不再信任客户端传入的 memberId。
     */
    public Optional<String> currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String principal = String.valueOf(authentication.getPrincipal());
        if (principal == null || !principal.startsWith(SUBJECT_PREFIX)) {
            return Optional.empty();
        }
        String memberId = principal.substring(SUBJECT_PREFIX.length()).trim();
        return memberId.isEmpty() ? Optional.empty() : Optional.of(memberId);
    }
}
