package com.hiddenwhite.common.coupon;

import org.springframework.http.HttpStatus;

/**
 * 优惠券业务失败。
 *
 * <p>自带 {@link HttpStatus} 而不是扩展某一端的 {@code BusinessException}，
 * 因为 {@link CouponService} 被点单端与管理端共用：点单端认 409 表示「状态不允许」，
 * 管理端认 {@code code} 字段。状态码由抛出方决定，由
 * {@code GlobalExceptionHandler} 按请求路径转成各端的响应结构 ——
 * 与 {@code PointsException}、{@code BirthdayException} 同一套路。
 */
public class CouponException extends RuntimeException {

    private final HttpStatus status;

    private CouponException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** 状态冲突：已领过 / 已用完 / 已过期 / 不满足门槛。用户可理解，不需要报障。 */
    public static CouponException conflict(String message) {
        return new CouponException(HttpStatus.CONFLICT, message);
    }

    /** 券不存在或不属于当前会员：一律 404，不透露「别人的券存在」。 */
    public static CouponException notFound(String message) {
        return new CouponException(HttpStatus.NOT_FOUND, message);
    }

    /** 参数不合法（运营端配置写入）。 */
    public static CouponException invalid(String message) {
        return new CouponException(HttpStatus.BAD_REQUEST, message);
    }
}
