package com.hiddenwhite.common.points;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会员积分账本 —— 全应用<b>唯一</b>的积分写入口。
 *
 * <p><b>为什么要收口</b>：改造前 {@code members.points} 是裸整数字段，唯一的写点是
 * 支付成功后的 {@code REPLACE points = points + ?} 与管理端「会员编辑」的直接赋值。
 * 积分只增不减、没有任何记录，客诉与对账都无从追溯（开发清单 P0-2）。
 * 一旦放开兑换，若仍允许各处直接 UPDATE 余额，流水立刻失真 ——
 * 因此所有变动都必须经过本类的 {@link #adjust}：它同时改余额、写流水，
 * 且流水里的 {@code balance_after} 是<b>同事务回读</b>的真实值。
 *
 * <p><b>并发安全</b>：扣分写成条件更新
 * {@code UPDATE members SET points = points - ? WHERE id = ? AND points >= ?}，
 * 影响行数为 0 即余额不足（409），不存在「先读后算再写」的丢失更新窗口；
 * 行锁同时把同一会员的并发兑换串行化。
 *
 * <p><b>事务</b>：本类方法只做 {@code REQUIRED} 传播，调用方（下单、兑换、核销）
 * 已在自己的事务里，任一步失败整体回滚，不会留下「扣了分没建单」的孤儿流水。
 */
@Service
public class PointsLedger {

    /** 备注列宽，超长由数据库静默截断会产生不可预期文案，这里显式截断。 */
    private static final int REMARK_MAX = 200;

    /** 关联 id 列宽。 */
    private static final int REF_ID_MAX = 64;

    private final JdbcTemplate jdbc;

    public PointsLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 变动积分并记账。
     *
     * @param memberId 会员 id
     * @param delta    变动量：正数为发放 / 退回，负数为消耗
     * @param bizType  业务类型
     * @param refType  关联对象类型
     * @param refId    关联对象 id
     * @param remark   备注（可为空）
     * @return 变动后的余额
     * @throws PointsException 会员不存在（404）或余额不足（409）
     */
    @Transactional
    public int adjust(String memberId, int delta, PointsBizType bizType, PointsRefType refType,
                      String refId, String remark) {
        if (delta == 0) {
            // 0 变动不产生流水：一条 change_amount=0 的记录对账时只会制造噪音
            return balance(memberId);
        }
        int updated = delta < 0
                // 扣分：把「余额是否够」交给数据库判定，天然防超扣
                ? jdbc.update("UPDATE members SET points = points - ? WHERE id = ? AND points >= ?",
                        -delta, memberId, -delta)
                // 加分：自增而非读改写，避免并发送分互相覆盖
                : jdbc.update("UPDATE members SET points = points + ? WHERE id = ?", delta, memberId);

        if (updated == 0) {
            if (!exists(memberId)) {
                throw PointsException.memberNotFound(memberId);
            }
            throw PointsException.insufficient("积分不足，本次需要 " + (-delta) + " 积分");
        }

        // 同事务回读：余额是数据库刚算出来的值，不是「旧值 + delta」的本地推算
        int balanceAfter = balance(memberId);
        appendLog(memberId, delta, balanceAfter, bizType, refType, refId, remark);
        return balanceAfter;
    }

    /** 当前余额；会员不存在抛 404。 */
    public int balance(String memberId) {
        List<Integer> rows = jdbc.query("SELECT points FROM members WHERE id = ?",
                (rs, row) -> rs.getInt("points"), memberId);
        if (rows.isEmpty()) {
            throw PointsException.memberNotFound(memberId);
        }
        return rows.get(0);
    }

    /** 会员是否存在（不抛异常，供调用方做「静默跳过」判断）。 */
    public boolean exists(String memberId) {
        List<Integer> rows = jdbc.query("SELECT 1 FROM members WHERE id = ? LIMIT 1",
                (rs, row) -> 1, memberId);
        return !rows.isEmpty();
    }

    /** 写一条流水。 */
    private void appendLog(String memberId, int delta, int balanceAfter, PointsBizType bizType,
                           PointsRefType refType, String refId, String remark) {
        jdbc.update("INSERT INTO member_points_log"
                        + "(member_id, change_amount, balance_after, biz_type, ref_type, ref_id, remark) "
                        + "VALUES(?,?,?,?,?,?,?)",
                memberId, delta, balanceAfter, bizType.name(),
                refType == null ? PointsRefType.NONE.name() : refType.name(),
                truncate(refId, REF_ID_MAX), truncate(remark, REMARK_MAX));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
