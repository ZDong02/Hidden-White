package com.hiddenwhite.admin.redeem.domain.dto;

/**
 * 核销请求：店员输入顾客出示的 8 位核销码。
 *
 * <p>用核销码而不是兑换单 id：到店场景只有顾客手机上的数字可用，
 * 让店员去列表里找单号既慢又容易点错。
 */
public class RedeemVerifyRequest {

    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
