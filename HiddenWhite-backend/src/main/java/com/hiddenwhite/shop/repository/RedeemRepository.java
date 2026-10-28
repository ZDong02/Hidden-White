package com.hiddenwhite.shop.repository;

import com.hiddenwhite.shop.domain.PointsLogEntry;
import com.hiddenwhite.shop.domain.RedeemProduct;
import com.hiddenwhite.shop.domain.Redemption;
import com.hiddenwhite.shop.domain.RedemptionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 积分兑换数据访问（点单端）。
 *
 * <p>风格对齐 {@link ShopRepository}：JdbcTemplate + 显式列清单，不使用 {@code SELECT *}。
 * 管理端另有一套 MyBatis 注解 Mapper 直读同一批表（{@code admin/redeem}），
 * 两侧字段语义必须保持一致 —— 尤其是 {@code status} 与时间列的时区处理。
 */
@Repository
public class RedeemRepository {

    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 核销截止时间与「每人限兑」相关的列清单：单查与分页共用，避免两处投影漂移。 */
    private static final String REDEMPTION_COLUMNS =
            "id,redeem_no,member_id,product_id,product_name,product_image,points_cost,code,status," +
                    "expires_at,verified_at,verified_by,cancelled_at,created_at";

    private static final String PRODUCT_COLUMNS =
            "id,name,subtitle,description,image,points_cost,stock,per_member_limit,sort_order,enabled";

    /**
     * 占用「每人限兑次数」的状态集合。
     *
     * <p>由 {@link RedemptionStatus#countsTowardLimit()} 反推：新增状态时只改枚举，
     * SQL 这侧自动跟随，不会出现「Java 认为已取消不占次数、SQL 仍把它算进去」的错位。
     */
    private static final String LIMIT_STATUS_SQL = Arrays.stream(RedemptionStatus.values())
            .filter(RedemptionStatus::countsTowardLimit)
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(","));

    /** 单次惰性过期结算的处理上限：一批一批来，避免某次查询把大量订单一次性拉进内存。 */
    private static final int EXPIRE_BATCH = 200;

    private final JdbcTemplate jdbc;

    public RedeemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- 兑换品 ----

    /** 可兑列表：仅上架，按运营设定的排序值升序。 */
    public List<RedeemProduct> enabledProducts() {
        return jdbc.query("SELECT " + PRODUCT_COLUMNS + " FROM redeem_products WHERE enabled = TRUE "
                + "ORDER BY sort_order, id", PRODUCT_MAPPER);
    }

    /** 按 id 读兑换品；不存在返回 null（下架品也要能读到，由 Service 给出准确原因）。 */
    public RedeemProduct product(String id) {
        return jdbc.query("SELECT " + PRODUCT_COLUMNS + " FROM redeem_products WHERE id = ?", PRODUCT_MAPPER, id)
                .stream().findFirst().orElse(null);
    }

    /** 该会员在各兑换品上已占用限兑名额的次数（只查占名额的状态）。 */
    public Map<String, Integer> usedCounts(String memberId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("SELECT product_id, COUNT(*) AS used FROM redemptions "
                        + "WHERE member_id = ? AND status IN (" + LIMIT_STATUS_SQL + ") GROUP BY product_id",
                rs -> {
                    counts.put(rs.getString("product_id"), rs.getInt("used"));
                }, memberId);
        return counts;
    }

    /**
     * 原子递减库存。
     *
     * <p><b>为什么拆成两步</b>：不限量商品的 {@code stock} 是 NULL，
     * {@code SET stock = stock - 1} 得到仍是 NULL —— 行「命中但没有变化」。
     * 此时影响行数取决于 JDBC 的 {@code useAffectedRows} 开关（Connector/J 默认 false，
     * 返回匹配行数；一旦有人改成 true，返回变化行数），兑换会在「不限量商品」上
     * 突然被误判为「已兑完」。这里先按 {@code stock > 0} 递减，
     * 未命中时再用存在性探测区分「不限量」与「已兑完 / 不存在」，不依赖连接器参数。
     *
     * @return 1 表示扣减成功（含不限量），0 表示已兑完或商品不存在
     */
    public int deductStock(String productId) {
        int updated = jdbc.update("UPDATE redeem_products SET stock = stock - 1 "
                + "WHERE id = ? AND stock > 0", productId);
        if (updated > 0) {
            return updated;
        }
        // 不限量（stock IS NULL）：不做递减，视为扣减成功
        List<Integer> unlimited = jdbc.query(
                "SELECT 1 FROM redeem_products WHERE id = ? AND stock IS NULL LIMIT 1",
                (rs, row) -> 1, productId);
        return unlimited.isEmpty() ? 0 : 1;
    }

    /** 该会员在该兑换品上已占用限兑名额的次数。 */
    public int countUsed(String memberId, String productId) {
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM redemptions "
                        + "WHERE member_id = ? AND product_id = ? AND status IN (" + LIMIT_STATUS_SQL + ")",
                Integer.class, memberId, productId);
        return used == null ? 0 : used;
    }

    /** 兑换单号是否已存在（取号时的冲突预检）。 */
    public boolean redeemNoExists(String redeemNo) {
        List<Integer> rows = jdbc.query("SELECT 1 FROM redemptions WHERE redeem_no = ? LIMIT 1",
                (rs, row) -> 1, redeemNo);
        return !rows.isEmpty();
    }

    // ---- 兑换单 ----

    public void insert(Redemption redemption) {
        jdbc.update("INSERT INTO redemptions(" + REDEMPTION_COLUMNS + ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                redemption.id(), redemption.redeemNo(), redemption.memberId(), redemption.productId(),
                redemption.productName(), redemption.productImage(), redemption.pointsCost(), redemption.code(),
                redemption.status().name(), toLocal(redemption.expiresAt()), toLocal(redemption.verifiedAt()),
                redemption.verifiedBy(), toLocal(redemption.cancelledAt()), toLocal(redemption.createdAt()));
    }

    public Redemption redemption(String id) {
        return jdbc.query("SELECT " + REDEMPTION_COLUMNS + " FROM redemptions WHERE id = ?",
                REDEMPTION_MAPPER, id).stream().findFirst().orElse(null);
    }

    /** 我的兑换单（分页，按下单时间倒序）。 */
    public List<Redemption> pageByMember(String memberId, long offset, int size) {
        return jdbc.query("SELECT " + REDEMPTION_COLUMNS + " FROM redemptions WHERE member_id = ? "
                + "ORDER BY created_at DESC, id LIMIT ?, ?", REDEMPTION_MAPPER, memberId, offset, size);
    }

    public long countByMember(String memberId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM redemptions WHERE member_id = ?",
                Long.class, memberId);
        return total == null ? 0 : total;
    }

    /**
     * 会员自助取消：条件更新把 PENDING 翻成 CANCELLED。
     *
     * <p>{@code member_id} 写进 WHERE 而不是「先查再判归属」：归属校验与状态翻转
     * 合成一次原子操作，既避免 TOCTOU，也天然幂等 —— 重复点击只有第一次命中。
     */
    public int cancel(String id, String memberId, OffsetDateTime cancelledAt) {
        return jdbc.update("UPDATE redemptions SET status = 'CANCELLED', cancelled_at = ? "
                + "WHERE id = ? AND member_id = ? AND status = 'PENDING'", toLocal(cancelledAt), id, memberId);
    }

    /**
     * 过期结算：条件翻转 PENDING → EXPIRED。
     *
     * <p>{@code status='PENDING'} 是幂等的关键：并发的两次结算、或「会员点取消」
     * 与「定时结算」同时发生，只有先到的那次影响行数为 1，退分因此只发生一次。
     */
    public int markExpired(String id) {
        return jdbc.update("UPDATE redemptions SET status = 'EXPIRED' WHERE id = ? AND status = 'PENDING'", id);
    }

    /** 管理端核销：只允许未过期的 PENDING 翻转，过期单必须先由结算流程退回积分。 */
    public int verify(String id, String operator, OffsetDateTime now) {
        return jdbc.update("UPDATE redemptions SET status = 'REDEEMED', verified_at = ?, verified_by = ? "
                        + "WHERE id = ? AND status = 'PENDING' AND expires_at > ?",
                toLocal(now), operator, id, toLocal(now));
    }

    /**
     * 已过期待结算的兑换单。
     *
     * <p>加 {@code LIMIT}：某个节假日堆积了几千张未核销凭证时，
     * 一次全部读回内存既没必要也会拖慢首个请求；一批 200 张，
     * 会员多刷新一次或运营多翻一页就会把剩余批次处理完。
     */
    public List<Redemption> expiredPending(OffsetDateTime now) {
        return jdbc.query("SELECT " + REDEMPTION_COLUMNS + " FROM redemptions "
                        + "WHERE status = 'PENDING' AND expires_at < ? ORDER BY expires_at LIMIT " + EXPIRE_BATCH,
                REDEMPTION_MAPPER, toLocal(now));
    }

    /** 兑换单号：RW + yyyyMMdd + 4 位日序号（与订单号同一套取号思路）。 */
    public String nextRedeemNo(LocalDate date) {
        String prefix = "RW" + NUMBER_DATE.format(date);
        // SUBSTRING 从第 11 位（下标 10）起取序号：前缀 'RW' + yyyyMMdd 恒为 10 位
        Integer maxSeq = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(redeem_no, 11) AS UNSIGNED)) FROM redemptions "
                        + "WHERE redeem_no LIKE ? AND redeem_no REGEXP CONCAT('^RW[0-9]{8}[0-9]+$')",
                Integer.class, prefix + "%");
        int next = maxSeq == null ? 1 : maxSeq + 1;
        if (next > 9999) {
            // 日序号用尽后回绕，撞上唯一索引的极窄概率由调用方重试兜住
            next = 1;
        }
        return prefix + String.format("%04d", next);
    }

    /** 核销码是否已被占用（8 位数字空间很大，冲突时重新抽即可）。 */
    public boolean codeExists(String code) {
        List<Integer> rows = jdbc.query("SELECT 1 FROM redemptions WHERE code = ? LIMIT 1",
                (rs, row) -> 1, code);
        return !rows.isEmpty();
    }

    // ---- 积分流水 ----

    public List<PointsLogEntry> pageLogs(String memberId, long offset, int size) {
        return jdbc.query("SELECT id,change_amount,balance_after,biz_type,ref_type,ref_id,remark,created_at "
                        + "FROM member_points_log WHERE member_id = ? ORDER BY id DESC LIMIT ?, ?",
                POINTS_LOG_MAPPER, memberId, offset, size);
    }

    public long countLogs(String memberId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM member_points_log WHERE member_id = ?",
                Long.class, memberId);
        return total == null ? 0 : total;
    }

    // ---- 映射 ----

    private static final RowMapper<RedeemProduct> PRODUCT_MAPPER = (rs, row) -> new RedeemProduct(
            rs.getString("id"), rs.getString("name"), rs.getString("subtitle"), rs.getString("description"),
            rs.getString("image"), rs.getInt("points_cost"),
            // getObject 而非 getInt：库存为 NULL 表示「不限量」，取原始类型会被读成 0
            rs.getObject("stock", Integer.class),
            rs.getInt("per_member_limit"), rs.getInt("sort_order"), rs.getBoolean("enabled"));

    private static final RowMapper<Redemption> REDEMPTION_MAPPER = (rs, row) -> new Redemption(
            rs.getString("id"), rs.getString("redeem_no"), rs.getString("member_id"), rs.getString("product_id"),
            rs.getString("product_name"), rs.getString("product_image"), rs.getInt("points_cost"),
            rs.getString("code"), RedemptionStatus.valueOf(rs.getString("status")),
            toOffset(rs.getTimestamp("expires_at")), toOffset(rs.getTimestamp("verified_at")),
            rs.getString("verified_by"), toOffset(rs.getTimestamp("cancelled_at")),
            toOffset(rs.getTimestamp("created_at")));

    private static final RowMapper<PointsLogEntry> POINTS_LOG_MAPPER = (rs, row) -> new PointsLogEntry(
            rs.getLong("id"), rs.getInt("change_amount"), rs.getInt("balance_after"),
            rs.getString("biz_type"), rs.getString("ref_type"), rs.getString("ref_id"),
            rs.getString("remark"), toOffset(rs.getTimestamp("created_at")));

    private static LocalDateTime toLocal(OffsetDateTime value) {
        return value == null ? null : value.toInstant().atZone(ZONE).toLocalDateTime();
    }

    /** DATETIME 列读取：库里的时间按门店时区解释，与写入侧对称。 */
    private static OffsetDateTime toOffset(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().atZone(ZONE).toOffsetDateTime();
    }
}
