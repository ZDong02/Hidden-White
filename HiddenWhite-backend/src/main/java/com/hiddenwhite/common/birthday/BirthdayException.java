package com.hiddenwhite.common.birthday;

import org.springframework.http.HttpStatus;

/**
 * 生日礼遇的业务异常，自带 HTTP 状态码。
 *
 * <p>与 {@code PointsException} 同一套理由：本包在 {@code common} 下被点单端与管理端共用，
 * 向下依赖任一侧的 {@code BusinessException} 都会把包依赖倒置。
 * 由 {@code GlobalExceptionHandler} 按请求路径分流成 {@code ApiError} / {@code R}。
 *
 * <p>状态码口径：窗口未开放、重复领取、一年一改都属于「当前状态不允许」，
 * 用 <b>409</b>；参数非法（月份 13、2 月 30 日）用 400；会员不存在 404。
 */
public class BirthdayException extends RuntimeException {

    private final HttpStatus status;

    public BirthdayException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 请求参数非法（如生日月/日越界）。 */
    public static BirthdayException invalid(String message) {
        return new BirthdayException(HttpStatus.BAD_REQUEST, message);
    }

    /** 当前状态不允许：未到窗口、已领取、生日一年内已改过。 */
    public static BirthdayException conflict(String message) {
        return new BirthdayException(HttpStatus.CONFLICT, message);
    }

    /** 会员不存在。 */
    public static BirthdayException memberNotFound(String memberId) {
        return new BirthdayException(HttpStatus.NOT_FOUND, "会员不存在: " + memberId);
    }

    public HttpStatus status() {
        return status;
    }
}
