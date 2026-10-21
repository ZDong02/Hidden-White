package com.hiddenwhite.framework.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class OperationLogAspect {

    private static final int MAX_PARAMS_LENGTH = 4_000;
    private static final int MAX_ERROR_LENGTH = 4_000;

    private final OperationLogRecorder recorder;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogRecorder recorder, ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operLog)")
    public Object record(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            recordSafely(joinPoint, operLog, start, 1, null);
            return result;
        } catch (Throwable error) {
            recordSafely(joinPoint, operLog, start, 0, error);
            throw error;
        }
    }

    /**
     * 记录操作日志；记录失败只告警，绝不影响业务结果。
     *
     * <p>审计是旁路能力，若它抛异常就会把业务本来要返回的结果（或原始异常）覆盖掉。
     */
    private void recordSafely(ProceedingJoinPoint joinPoint, OperLog operLog, long start,
                              int result, Throwable error) {
        try {
            record(joinPoint, operLog, start, result, error);
        } catch (RuntimeException ignored) {
            // 忽略：日志记录失败不应让业务请求失败
        }
    }

    private void record(ProceedingJoinPoint joinPoint, OperLog operLog, long start, int result, Throwable error) {
        HttpServletRequest request = currentRequest();
        recorder.record(new OperationLogEntry(
                operLog.module(),
                operLog.action(),
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                truncate(params(joinPoint.getArgs()), MAX_PARAMS_LENGTH),
                currentUsername(),
                request == null ? null : clientIp(request),
                System.currentTimeMillis() - start,
                result,
                truncate(error == null ? null : error.getMessage(), MAX_ERROR_LENGTH)
        ));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String params(Object[] args) {
        try {
            // 上传文件的参数需先替换为摘要：直接序列化 MultipartFile 会因
            // 其内部持有 InputStream 而抛序列化异常，并把真正的业务结果一并吞掉。
            JsonNode root = objectMapper.valueToTree(summarizeFiles(args));
            maskSensitiveFields(root);
            return objectMapper.writeValueAsString(root);
        } catch (RuntimeException | JsonProcessingException error) {
            // 记录参数失败不应影响业务结果本身，这里只放弃参数明细
            return "[]";
        }
    }

    /**
     * 把参数里的上传文件替换为可读的摘要（文件名 + 字节数）。
     *
     * <p>既避免序列化文件流导致的异常，也避免把文件内容写进操作日志。
     */
    private Object[] summarizeFiles(Object[] args) {
        Object[] summarized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            summarized[i] = arg instanceof MultipartFile file ? describeFile(file) : arg;
        }
        return summarized;
    }

    /** 上传文件的日志摘要。 */
    private Map<String, Object> describeFile(MultipartFile file) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fileName", file.getOriginalFilename());
        summary.put("size", file.getSize());
        summary.put("contentType", file.getContentType());
        return summary;
    }

    private void maskSensitiveFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(field -> {
                JsonNode child = objectNode.get(field);
                if (isSensitive(field)) {
                    objectNode.put(field, "******");
                } else {
                    maskSensitiveFields(child);
                }
            });
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskSensitiveFields);
        }
    }

    private boolean isSensitive(String field) {
        String lower = field.toLowerCase();
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("authorization");
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : String.valueOf(authentication.getPrincipal());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
