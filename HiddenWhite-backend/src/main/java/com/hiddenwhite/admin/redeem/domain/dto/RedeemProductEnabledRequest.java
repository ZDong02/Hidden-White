package com.hiddenwhite.admin.redeem.domain.dto;

/** 兑换品上下架请求：只带状态位，走独立端点而非整行更新。 */
public class RedeemProductEnabledRequest {

    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
