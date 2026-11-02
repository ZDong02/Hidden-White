package com.hiddenwhite.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 填写 / 修改生日。
 *
 * <p>只收月与日：生日礼遇只需要「哪个月哪一天」，收集出生年份属于超出业务目的的采集。
 * 范围校验放在注解与 Service 两处：注解给出 400 的第一道拦截，
 * Service 再按「当月实际天数」判一次（2 月 30 日这类注解表达不了的错误）。
 */
public record BirthdayWriteRequest(
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull @Min(1) @Max(31) Integer day) {
}
