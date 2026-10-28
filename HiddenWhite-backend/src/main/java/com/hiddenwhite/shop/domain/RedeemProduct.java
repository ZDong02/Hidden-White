package com.hiddenwhite.shop.domain;

/**
 * 积分兑换品（点单端可兑目录的一行）。
 *
 * <p><b>为什么不复用 {@link Product}</b>：{@code products} 的模型绑定奶咖假设
 * （{@code has_milk}、SIZE/MILK/TEMPERATURE 单一分类 INT、价格是元），
 * 既表达不了「0 元 / 纯积分价」，也装不下徽章这类非饮品，
 * 强行复用会把规格矩阵带进一条根本不需要规格的链路。
 *
 * @param id             业务主键，如 rw-latte
 * @param name           名称
 * @param subtitle       副标题
 * @param description    描述
 * @param image          图片相对路径（/uploads/...，由前端补域名）
 * @param pointsCost     兑换所需积分
 * @param stock          库存：null 表示不限量
 * @param perMemberLimit 每人限兑次数，0 表示不限
 * @param sortOrder      排序值（升序）
 * @param enabled        是否上架
 */
public record RedeemProduct(
        String id,
        String name,
        String subtitle,
        String description,
        String image,
        int pointsCost,
        Integer stock,
        int perMemberLimit,
        int sortOrder,
        boolean enabled) {

    /** 库存是否已耗尽（不限量恒为 false）。 */
    public boolean soldOut() {
        return stock != null && stock <= 0;
    }
}
