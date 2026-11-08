package com.hiddenwhite.admin.support;

import com.hiddenwhite.common.exception.BusinessException;

/**
 * 管理端服务的文本处理与字段校验小工具。
 *
 * <p><b>为什么放在 admin 包下而不是 common</b>：这些方法抛的是管理端的
 * {@link BusinessException}（自带 int code，由 GlobalExceptionHandler 按路径映射成 R.error）。
 * 点单端有另一套 {@code shop.exception.BusinessException}（携带 HttpStatus），
 * 放进 common 会让「通用工具」反向依赖某一端的异常语义，两端都会想改它。
 *
 * <p>抽出来的原因很实际：{@code trim} / {@code blankToNull} 原先在 CoffeeService、
 * RedeemAdminService 各写一份，{@code requireText} 在两处还签名不同（一个返回 void、
 * 一个返回值），同一个校验在不同模块产生不同的错误文案只是时间问题。
 *
 * <p>方法刻意保持无状态、无 Spring 依赖，便于服务层直接静态导入。
 */
public final class Texts {

    private Texts() {
    }

    /** null 安全的去空白：null 归一成空串。 */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** null 安全的「是否没填」：null 与纯空白都算没填。 */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 带兜底的去空白：用于「缺省即用默认值」的配置项。 */
    public static String trim(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    /** 空串归一成 null：用于「空表示不筛选」的查询参数。 */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 必填校验，返回原值以便链式使用。
     *
     * <p>返回 String 而不是 void：调用处常常要「校验后马上用」，
     * 早期两个服务分别写成 void 和 String 两个版本，等于同一件事有两种写法。
     */
    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + "不能为空");
        }
        return value;
    }

    /**
     * 长度上界校验：与列宽一致。
     *
     * <p>超长在 MySQL 严格模式下会抛 1406（Data too long），
     * 那是一条运营看不懂的 500；这里提前转成字段级提示。
     */
    public static void requireMaxLength(String value, int max, String field) {
        if (value != null && value.trim().length() > max) {
            throw new BusinessException(field + "最多 " + max + " 个字，当前 " + value.trim().length() + " 个字");
        }
    }
}
