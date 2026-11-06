package com.hiddenwhite.admin.upsize.domain.dto;

/**
 * 保存一个等级的升杯策略。
 *
 * <p><b>为什么不带路径参数</b>：等级键是中文（「黄金会员」），放进 URL 要依赖各层
 * 对编码的一致处理。与会员折扣接口同一处理——请求体传 {@code levelKey}，
 * 少一类只在中文环境下复现的问题。
 *
 * <p>数值语义见 {@code member_upsize_policies}：{@code -1} 表示不限。
 * 这里不做「必须为正」的校验，只拦明显笔误（见 {@code UpsizeAdminService}）。
 */
public record UpsizePolicyWriteRequest(
        String levelKey,
        Integer monthlyLimit,
        Integer freeQuota,
        Integer pointsCost,
        String fromSize,
        String toSize,
        Boolean enabled,
        String note) {
}
