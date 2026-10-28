package com.hiddenwhite.shop.domain;

import java.time.OffsetDateTime;

/**
 * 积分流水的一条记录（点单端「积分明细」列表项）。
 *
 * <p>只暴露展示需要的字段：{@code bizType} 由前端映射成中文文案
 * （与服务端枚举 {@code PointsBizType} 同名），{@code balanceAfter} 是
 * 该笔变动发生<b>之后</b>的余额 —— 从任意一条往前累加都能还原历史余额，
 * 这也是对账时最有用的一个字段。
 *
 * @param id           流水 id
 * @param changeAmount 变动量：正为收入，负为支出
 * @param balanceAfter 变动后余额
 * @param bizType      业务类型（ORDER_AWARD / REDEEM / ADMIN_ADJUST ...）
 * @param refType      关联对象类型
 * @param refId        关联对象 id
 * @param remark       备注
 * @param createdAt    发生时间
 */
public record PointsLogEntry(
        long id,
        int changeAmount,
        int balanceAfter,
        String bizType,
        String refType,
        String refId,
        String remark,
        OffsetDateTime createdAt) {
}
