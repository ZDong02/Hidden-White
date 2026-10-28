package com.hiddenwhite.shop.domain;

import java.time.OffsetDateTime;

/**
 * 兑换单（到店核销凭证）。
 *
 * <p>商品名称与图片是<b>快照</b>：兑换品后续改名、换图、下架甚至删除，
 * 已经发出去的凭证仍要能核对「当时兑的是什么」；{@code pointsCost} 同理，
 * 记录实际扣了多少分，而不是回头去读可能已被运营改价的目录。
 *
 * @param id           兑换单 id（UUID）
 * @param redeemNo     兑换单号：RW + yyyyMMdd + 4 位日序号
 * @param memberId     会员 id
 * @param productId    兑换品 id
 * @param productName  商品名快照
 * @param productImage 商品图快照
 * @param pointsCost   实际扣除积分
 * @param code         8 位核销码
 * @param status       状态
 * @param expiresAt    核销截止时间
 * @param verifiedAt   核销时间（未核销为 null）
 * @param verifiedBy   核销管理员
 * @param cancelledAt  取消时间
 * @param createdAt    下单时间
 */
public record Redemption(
        String id,
        String redeemNo,
        String memberId,
        String productId,
        String productName,
        String productImage,
        int pointsCost,
        String code,
        RedemptionStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime verifiedAt,
        String verifiedBy,
        OffsetDateTime cancelledAt,
        OffsetDateTime createdAt) {
}
