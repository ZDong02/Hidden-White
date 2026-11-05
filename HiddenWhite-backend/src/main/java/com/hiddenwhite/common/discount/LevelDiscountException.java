package com.hiddenwhite.common.discount;

import org.springframework.http.HttpStatus;

/**
 * 会员折扣的业务失败。
 *
 * <p>与 {@code CouponException} 同一套路：服务被点单端与管理端共用，
 * 异常自带 {@link HttpStatus}，由 {@code GlobalExceptionHandler} 按请求路径
 * 转成各端的响应结构（点单端 {@code ApiError} / 管理端 {@code R}）。
 */
public class LevelDiscountException extends RuntimeException {

    private final HttpStatus status;

    private LevelDiscountException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /** 配置写入不合法（管理端）：折扣率越界、门槛为负、等级不在阶梯内。 */
    public static LevelDiscountException invalid(String message) {
        return new LevelDiscountException(HttpStatus.BAD_REQUEST, message);
    }

    /** 等级策略不存在。 */
    public static LevelDiscountException notFound(String message) {
        return new LevelDiscountException(HttpStatus.NOT_FOUND, message);
    }
}
