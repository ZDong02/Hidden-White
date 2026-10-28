package com.hiddenwhite.common.exception;

import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.shop.dto.ApiError;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 统一异常处理。
 * 小程序端（/api/shop 之外的 /api/**）沿用 ApiError 结构；
 * 管理端（/api/admin/**）沿用 R 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ADMIN_PREFIX = "/api/admin";

    // ---- 业务异常 ----

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> notFound(NotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> shopBusiness(BusinessException exception, HttpServletRequest request) {
        return response(exception.status(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(com.hiddenwhite.common.exception.BusinessException.class)
    public ResponseEntity<Object> adminBusiness(com.hiddenwhite.common.exception.BusinessException exception,
                                                HttpServletRequest request) {
        if (isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(R.error(exception.code(), exception.getMessage()));
        }
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
    }

    /**
     * 积分账本的业务失败。
     *
     * <p>{@code PointsLedger} 被点单端与管理端共用，自己带状态码：
     * 余额不足是 409（可重试冲突），会员不存在是 404。
     * 这里按请求路径分流到各自端已有的响应结构，不新增前端需要识别的格式。
     */
    @ExceptionHandler(com.hiddenwhite.common.points.PointsException.class)
    public ResponseEntity<Object> points(com.hiddenwhite.common.points.PointsException exception,
                                         HttpServletRequest request) {
        return response(exception.status(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        if (isAdmin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.error(403, "没有访问权限"));
        }
        return response(HttpStatus.FORBIDDEN, "没有访问权限", request, List.of());
    }

    /**
     * 订单状态机拒绝的流转。
     *
     * <p>{@code OrderStatus.assertCanTransitionTo} 抛的是 {@link IllegalStateException}。
     * 不单独兜住它就会掉进通用兜底分支，变成一个 500「服务器内部错误」——
     * 而这类失败的真实含义是「你操作时订单已经被别人推进了」，
     * 属于<b>可以理解并重试</b>的冲突（409），不是服务端故障。
     * 典型触发场景：顾客点「确认取餐」的同一秒，超时任务正好把订单取消。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> illegalState(IllegalStateException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request, List.of());
    }

    // ---- 请求异常 ----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage()).toList();
        if (isAdmin(request)) {
            String message = details.isEmpty() ? "请求参数校验失败" : details.get(0);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.error(400, message));
        }
        return response(HttpStatus.BAD_REQUEST, "请求参数校验失败", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return badRequest("请求体格式错误", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return badRequest("参数类型错误: " + exception.getName(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> badArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return badRequest(exception.getMessage(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> methodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                     HttpServletRequest request) {
        return badRequest("不支持的请求方法: " + exception.getMethod(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> noResource(NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "资源不存在", request, List.of());
    }

    // ---- 上传异常 ----

    /**
     * 上传文件超过容器限制。
     *
     * <p>Spring 在读取 multipart 时就会中断，这里兜住它，
     * 否则会掉进通用兜底分支，运营者只能看到「服务器内部错误」，
     * 不知道是图片太大。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> uploadTooLarge(MaxUploadSizeExceededException exception,
                                                  HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "图片大小超过服务器限制，请压缩后再上传", request, List.of());
    }

    /**
     * multipart 请求本身不合法（格式错误等）。
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> multipartInvalid(MultipartException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "图片上传请求有误，请重新选择图片上传", request, List.of());
    }

    /**
     * 缺少必填的请求参数（如未选择文件）。
     *
     * <p>该异常继承自 ServletRequestBindingException 而非 MultipartException，
     * 不单独兜住会掉进通用兜底分支，只能看到「服务器内部错误」。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> missingParameter(MissingServletRequestParameterException exception,
                                                   HttpServletRequest request) {
        String name = exception.getParameterName();
        String message = "file".equals(name) ? "请选择要上传的图片" : "缺少必要参数：" + name;
        return response(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    /**
     * 缺少必填的请求体片段（multipart 中的文件字段缺失）。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Object> missingPart(MissingServletRequestPartException exception,
                                              HttpServletRequest request) {
        String name = exception.getRequestPartName();
        String message = "file".equals(name) ? "请选择要上传的图片" : "缺少必要内容：" + name;
        return response(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    // ---- 兜底 ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, HttpServletRequest request) {
        log.error("未处理异常", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误", request, List.of());
    }

    // ---- 内部工具 ----

    private ResponseEntity<Object> badRequest(String message, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    private ResponseEntity<Object> response(HttpStatus status, String message, HttpServletRequest request,
                                            List<String> details) {
        if (isAdmin(request)) {
            return ResponseEntity.status(status).body(R.error(status.value(), message));
        }
        ApiError body = new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }

    private boolean isAdmin(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ADMIN_PREFIX);
    }
}
