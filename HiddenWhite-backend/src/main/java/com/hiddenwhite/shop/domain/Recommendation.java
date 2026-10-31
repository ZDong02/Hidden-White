package com.hiddenwhite.shop.domain;

import java.math.BigDecimal;

/**
 * 首页轮播位上的一屏推荐。
 *
 * <p>文案（眉标 / 主标题 / 短句 / 链接词）由管理端配置，留空时由
 * {@code CatalogService} 回填成商品自身的字段 —— 因此这里拿到的四个字段
 * 一定非空，前端可以直接渲染，不必各自再判一遍兜底。
 *
 * @param id        推荐位主键
 * @param productId 关联商品 ID，点击这一屏跳转详情页用
 * @param name      商品名称（兜底文案的来源）
 * @param subtitle  商品副标题
 * @param price     售价，随商品实时变化，不在推荐位里做快照
 * @param image     商品图，管理端上传的是 /uploads 相对路径，点单端统一补域名
 * @param eyebrow   眉标：如「本周推荐 · 匠心双份浓缩」
 * @param headline  主标题：如「一杯，恰好。」
 * @param caption   短句：如「细腻奶感，纯粹咖啡香。」
 * @param linkText  链接文案：如「品味招牌馥白」
 * @param sortOrder 池内顺序，前端随机抽样前的稳定基准
 */
public record Recommendation(
        long id,
        String productId,
        String name,
        String subtitle,
        BigDecimal price,
        String image,
        String eyebrow,
        String headline,
        String caption,
        String linkText,
        int sortOrder) {
}
