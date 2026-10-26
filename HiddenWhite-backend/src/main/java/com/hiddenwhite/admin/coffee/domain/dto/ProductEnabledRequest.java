package com.hiddenwhite.admin.coffee.domain.dto;

/**
 * 商品上下架请求。
 *
 * <p>刻意与 {@link ProductWriteRequest} 分开：上下架只改一个状态位，
 * 若复用全量写入 DTO，调用方只带 enabled 就会被「名称不能为空」拒掉，
 * 或把其余字段一起写成空值。字段保持包装类型以便区分「没传」与「传了 false」。
 */
public class ProductEnabledRequest {

    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
