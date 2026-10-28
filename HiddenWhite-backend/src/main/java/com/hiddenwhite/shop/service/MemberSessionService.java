package com.hiddenwhite.shop.service;

import com.hiddenwhite.framework.security.JwtService;
import com.hiddenwhite.framework.security.TokenStore;
import com.hiddenwhite.shop.exception.BusinessException;
import org.springframework.http.HttpStatus;
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

    /**
     * 游客账号 id。
     *
     * <p>点单端下单时会自动创建/复用该账号，它没有手机号与登录口令，
     * 余额恒为 0 且变动无归属意义 —— 积分兑换与积分明细对它一律拒绝，
     * 否则会产生「永远对不上账」的流水。
     */
    public static final String GUEST_MEMBER_ID = "guest";

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

    /**
     * 取「有账号的」当前会员：未登录或游客账号都拒绝。
     *
     * <p>积分相关的读接口（明细 / 兑换）统一走这里：
     * 未登录时 401 让前端引导登录，游客账号同样 401 ——
     * 对顾客而言处置一致（去登录），不必再区分一种「登录了但不能用」的状态。
     */
    public String requireAccountMemberId() {
        String memberId = currentMemberId()
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录"));
        if (GUEST_MEMBER_ID.equals(memberId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "游客账号无法使用积分服务，请登录会员账号");
        }
        return memberId;
    }
}
