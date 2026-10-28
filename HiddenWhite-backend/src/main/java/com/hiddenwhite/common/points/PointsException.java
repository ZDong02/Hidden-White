package com.hiddenwhite.common.points;

import org.springframework.http.HttpStatus;

/**
 * 积分账本抛出的业务异常。
 *
 * <p>为什么不复用两侧各自的 {@code BusinessException}：{@link PointsLedger} 在
 * {@code common} 包里被点单端与管理端共用，向下依赖任一侧的异常类型都会把
 * 包依赖关系倒过来。这里自带状态码，由 {@code GlobalExceptionHandler}
 * 统一转成各端的响应结构（点单端 {@code ApiError} / 管理端 {@code R}）。
 */
public class PointsException extends RuntimeException {

    private final HttpStatus status;

    public PointsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 余额不足：可重试类冲突，不是服务端故障。 */
    public static PointsException insufficient(String message) {
        return new PointsException(HttpStatus.CONFLICT, message);
    }

    /** 会员不存在。 */
    public static PointsException memberNotFound(String memberId) {
        return new PointsException(HttpStatus.NOT_FOUND, "会员不存在: " + memberId);
    }

    public HttpStatus status() {
        return status;
    }
}
