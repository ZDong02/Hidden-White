package com.hiddenwhite.admin.coupon.service;

import com.hiddenwhite.admin.coupon.domain.dto.CouponConfigWriteRequest;
import com.hiddenwhite.common.coupon.CouponConfig;
import com.hiddenwhite.common.coupon.CouponService;
import com.hiddenwhite.common.coupon.CouponWeek;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 优惠券运营端：配置维护、发放与使用记录、周期概览。
 *
 * <p><b>配置读的是 {@link CouponService#config()}，不是自己拼 SQL</b>：
 * 运营看到的开关必须与点单端实际生效的那份是同一个读法，
 * 否则「后台显示已开启、用户还是领不到」这类问题只能靠猜。
 *
 * <p><b>记录列表查询前先结算过期</b>：过期采用惰性结算（没有定时任务），
 * 不先结算的话，运营会把早就作废的券当成「待使用」统计进去，
 * 客服按这个数字答复顾客就一定是错的。
 */
@Service
public class CouponAdminService {

    /** 与 member_coupons.status 的 ENUM 严格一致，不接受其它值。 */
    private static final List<String> STATUSES = List.of("UNUSED", "USED", "EXPIRED");

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final CouponService couponService;

    public CouponAdminService(JdbcTemplate jdbc, CouponService couponService) {
        this.jdbc = jdbc;
        this.couponService = couponService;
    }

    // ---- 配置 ----

    /**
     * 配置 + 周期概览，一次给全。
     *
     * <p>「本周」按自然周（周一 00:00 起）统计，与领取周期同源（{@link CouponWeek}）——
     * 运营看到的「本周已领 37 张」必须与「本周一重置」是同一个口径。
     */
    public Map<String, Object> overview() {
        CouponConfig config = couponService.config();
        CouponWeek.Week week = CouponWeek.current();
        LocalDateTime now = LocalDateTime.now(ZONE);
        String weekKey = week.key();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", config.enabled());
        map.put("amount", config.amount());
        map.put("minSpend", config.minSpend());
        map.put("validDays", config.validDays());
        map.put("maxHeld", config.maxHeld());
        map.put("totalStock", config.totalStock());
        map.put("issuedCount", config.issuedCount());
        map.put("remaining", config.remaining());
        map.put("title", config.title());
        map.put("subtitle", config.subtitle());
        map.put("description", config.description());

        // 周期口径：给前端显示「本周 = 2026-11-02 ~ 2026-11-08，下周一 00:00 重置」
        map.put("weekKey", weekKey);
        map.put("weekFrom", week.from().toString());
        map.put("weekTo", week.to().toString());
        map.put("weekResetAt", week.resetAt().format(DATETIME));
        map.put("weeklyLimit", 1);

        map.put("claimedThisWeek", count("week_key=?", weekKey));
        map.put("usedThisWeek", count("week_key=? AND status='USED'", weekKey));
        map.put("claimedTotal", count("1=1"));
        map.put("usedTotal", count("status='USED'"));
        map.put("activeCount", count("status='UNUSED' AND expires_at>?", now));
        map.put("memberCount", countDistinctMembers());
        map.put("discountTotal", sum());
        return map;
    }

    /**
     * 保存配置。
     *
     * <p>逐项校验的意义：这些数字改一行就影响全体会员。面额写成 600 是一次真实的资损，
     * 有效期写成 0 天则让券一领出来就过期，客服会被问「为什么刚领就没了」。
     */
    @Transactional
    public void updateConfig(CouponConfigWriteRequest request) {
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("券面额必须大于 0");
        }
        if (amount.compareTo(BigDecimal.valueOf(200)) > 0) {
            throw new BusinessException("券面额不能超过 200 元，请确认是否填错");
        }
        BigDecimal minSpend = request.minSpend() == null ? BigDecimal.ZERO : request.minSpend();
        if (minSpend.signum() < 0) {
            throw new BusinessException("使用门槛不能为负数");
        }
        // 门槛低于面额意味着「减完之后订单可能为负」，虽然不会真的扣负数，但券的价值失真
        if (minSpend.signum() > 0 && minSpend.compareTo(amount) < 0) {
            throw new BusinessException("使用门槛不能低于券面额，否则等于无门槛");
        }

        int validDays = request.validDays() == null ? 0 : request.validDays();
        if (validDays < 1 || validDays > 90) {
            throw new BusinessException("有效期需在 1–90 天之间");
        }
        int maxHeld = request.maxHeld() == null ? 0 : request.maxHeld();
        if (maxHeld < 0 || maxHeld > 10) {
            throw new BusinessException("持有上限需在 0–10 张之间（0 表示不限）");
        }

        CouponConfig current = couponService.config();
        Integer totalStock = request.totalStock();
        if (totalStock != null) {
            if (totalStock < 0) {
                throw new BusinessException("发行量不能为负数");
            }
            // 不能把发行量调到已发出数量之下：那会让「剩余 -12 张」这种数字出现在页面上，
            // 也无法回滚已经发出去的券
            if (totalStock < current.issuedCount()) {
                throw new BusinessException("发行量不能小于已发出的 "
                        + current.issuedCount() + " 张；如需停发请直接关闭领取");
            }
        }

        String title = trim(request.title());
        if (title.isEmpty()) {
            throw new BusinessException("请填写券标题");
        }
        if (title.length() > 40) {
            throw new BusinessException("券标题不能超过 40 个字");
        }
        String subtitle = trim(request.subtitle());
        if (subtitle.length() > 60) {
            throw new BusinessException("券副标题不能超过 60 个字");
        }
        String description = trim(request.description());
        if (description.length() > 200) {
            throw new BusinessException("使用说明不能超过 200 个字");
        }

        jdbc.update("UPDATE coupon_config SET enabled=?,amount=?,min_spend=?,valid_days=?,max_held=?,"
                        + "total_stock=?,title=?,subtitle=?,description=? WHERE id=1",
                !Boolean.FALSE.equals(request.enabled()), amount, minSpend, validDays, maxHeld,
                totalStock, title, subtitle, description);
    }

    // ---- 记录 ----

    /**
     * 发放与使用记录（分页）。
     *
     * @param status  状态过滤（UNUSED / USED / EXPIRED），非法值按「不过滤」处理
     * @param weekKey 领取归属周（周一日期），空表示不限
     */
    @Transactional
    public PageResult<Map<String, Object>> records(PageQuery query, String status, String weekKey, String keyword) {
        couponService.settleAllExpired();
        String statusValue = normalizeStatus(status);
        String week = blankToNull(weekKey);
        String key = blankToNull(keyword);

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM member_coupons c "
                        + "JOIN members m ON m.id=c.member_id "
                        + "WHERE (? IS NULL OR c.status=?) "
                        + "AND (? IS NULL OR c.week_key=?) "
                        + "AND (? IS NULL OR m.display_name LIKE CONCAT('%',?,'%') "
                        + "OR m.phone LIKE CONCAT('%',?,'%') OR c.member_id LIKE CONCAT('%',?,'%'))",
                Long.class, statusValue, statusValue, week, week, key, key, key, key);

        int pageNum = query.normalizedPageNum();
        int pageSize = query.normalizedPageSize();
        long offset = (long) (pageNum - 1) * pageSize;

        List<Map<String, Object>> rows = total == 0 ? List.of()
                : jdbc.query("SELECT c.id,c.member_id AS memberId,m.display_name AS displayName,m.phone,"
                                + "c.week_key AS weekKey,c.title,c.amount,c.min_spend AS minSpend,c.status,"
                                + "DATE_FORMAT(c.claimed_at,'%Y-%m-%d %H:%i') AS claimedAt,"
                                + "DATE_FORMAT(c.expires_at,'%Y-%m-%d %H:%i') AS expiresAt,"
                                + "DATE_FORMAT(c.used_at,'%Y-%m-%d %H:%i') AS usedAt,"
                                + "c.order_id AS orderId,o.order_number AS orderNumber,"
                                + "c.discount_amount AS discountAmount "
                                + "FROM member_coupons c "
                                + "JOIN members m ON m.id=c.member_id "
                                + "LEFT JOIN orders o ON o.id=c.order_id "
                                + "WHERE (? IS NULL OR c.status=?) "
                                + "AND (? IS NULL OR c.week_key=?) "
                                + "AND (? IS NULL OR m.display_name LIKE CONCAT('%',?,'%') "
                                + "OR m.phone LIKE CONCAT('%',?,'%') OR c.member_id LIKE CONCAT('%',?,'%')) "
                                + "ORDER BY c.claimed_at DESC LIMIT ?,?",
                        (rs, index) -> {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getString("id"));
                            row.put("memberId", rs.getString("memberId"));
                            row.put("displayName", rs.getString("displayName"));
                            row.put("phone", rs.getString("phone"));
                            row.put("weekKey", rs.getString("weekKey"));
                            row.put("title", rs.getString("title"));
                            row.put("amount", rs.getBigDecimal("amount"));
                            row.put("minSpend", rs.getBigDecimal("minSpend"));
                            row.put("status", rs.getString("status"));
                            row.put("claimedAt", rs.getString("claimedAt"));
                            row.put("expiresAt", rs.getString("expiresAt"));
                            row.put("usedAt", rs.getString("usedAt"));
                            row.put("orderId", rs.getString("orderId"));
                            row.put("orderNumber", rs.getString("orderNumber"));
                            row.put("discountAmount", rs.getBigDecimal("discountAmount"));
                            return row;
                        },
                        statusValue, statusValue, week, week, key, key, key, key, offset, pageSize);

        return PageResult.of(rows, total, pageNum, pageSize);
    }

    /** 可选的自然周列表：最近 12 周，供筛选下拉使用。 */
    public List<Map<String, Object>> recentWeeks() {
        List<Map<String, Object>> weeks = new ArrayList<>();
        CouponWeek.Week current = CouponWeek.current();
        for (int index = 0; index < 12; index++) {
            CouponWeek.Week week = CouponWeek.of(current.from().minusWeeks(index));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", week.key());
            row.put("label", week.from() + " ~ " + week.to());
            weeks.add(row);
        }
        return weeks;
    }

    // ---- 内部 ----

    /** 计数：{@code where} 里的占位符个数必须与 {@code args} 一致，不做隐式补参。 */
    private long count(String where, Object... args) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_coupons WHERE " + where, Long.class, args);
        return value == null ? 0 : value;
    }

    private long countDistinctMembers() {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT member_id) FROM member_coupons", Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal sum() {
        BigDecimal value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(discount_amount),0) FROM member_coupons WHERE status='USED'",
                BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String normalizeStatus(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase();
        if (!STATUSES.contains(upper)) {
            throw new BusinessException("不支持的状态: " + status);
        }
        return upper;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
