package com.hiddenwhite.common.coupon;

import java.math.BigDecimal;

/**
 * 优惠券配置（单行表 {@code coupon_config}，id 恒为 1）。
 *
 * <p><b>为什么是单行表而不是「券模板」多行表</b>：当前业务只有一种券
 * （每周可领、固定面额、固定门槛）。建模板表意味着要引入模板 id、上下架、
 * 排序、优先级、叠加规则一堆暂时用不上的结构，而配置项一多，
 * 每加一个字段就要同时改四处（表、DTO、服务端校验、管理端表单）。
 * 单行表把「这一期发什么券」压缩成 10 个字段，改完立刻对所有会员生效。
 *
 * <p><b>改配置不影响已发出的券</b>：{@link Coupon} 在领取时把面额、门槛、标题
 * 快照下来。运营把面额从 6 调到 8，已领的 6 元券仍然是 6 元 ——
 * 否则顾客截图里是 6 元、结算时变 8 元，或者反过来，都是对账事故。
 *
 * @param enabled    是否开放领取
 * @param amount     券面额（元）
 * @param minSpend   使用门槛：订单商品金额达到此值才能用（元）
 * @param validDays  领取后的有效天数（从领取时刻起算，含当日）
 * @param maxHeld    同时持有的上限（未使用且未过期的张数），防止长期不用的人囤券
 * @param totalStock 总发行量，null 表示不限量
 * @param issuedCount 已发行数量（计数器，扣减走条件更新）
 * @param title      券标题（点单端券面与列表共用）
 * @param subtitle   副标题（券面第二行）
 * @param description 使用说明（券详情与弹层）
 */
public record CouponConfig(
        boolean enabled,
        BigDecimal amount,
        BigDecimal minSpend,
        int validDays,
        int maxHeld,
        Integer totalStock,
        int issuedCount,
        String title,
        String subtitle,
        String description) {

    /**
     * 表为空时的兜底配置。
     *
     * <p>不抛异常而是按默认发放：配置表被误清空时，宁可少赚 6 元，
     * 也不要让整个券包页 500 —— 顾客看到的会是「加载失败」而不是「暂时没有券」。
     */
    public static final CouponConfig DEFAULT = new CouponConfig(
            true, BigDecimal.valueOf(6), BigDecimal.valueOf(20), 7, 2, null, 0,
            "每周好券", "满 20 元减 6 元", "每周可领一张，领取后 7 天内有效，单笔订单限用一张。");

    /** 库存是否已耗尽（不限量恒为 false）。 */
    public boolean soldOut() {
        return totalStock != null && issuedCount >= totalStock;
    }

    /** 剩余库存；不限量返回 null，供前端显示「不限量」而不是编一个数字。 */
    public Integer remaining() {
        return totalStock == null ? null : Math.max(0, totalStock - issuedCount);
    }

    /** 配置是否可用：面额必须为正，否则券没有任何意义。 */
    public boolean usable() {
        return amount != null && amount.signum() > 0 && validDays > 0;
    }
}
