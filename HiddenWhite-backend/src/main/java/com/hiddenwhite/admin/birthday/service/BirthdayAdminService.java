package com.hiddenwhite.admin.birthday.service;

import com.hiddenwhite.admin.birthday.domain.dto.BirthdayConfigWriteRequest;
import com.hiddenwhite.common.birthday.BirthdayGiftService;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hiddenwhite.admin.support.Texts.isBlank;
import static com.hiddenwhite.admin.support.Texts.trim;

/**
 * 生日礼遇的运营端：配置维护、领取记录、年度统计。
 *
 * <p>读配置复用 {@link BirthdayGiftService#config()} —— 运营看到的开关必须与
 * 点单端实际生效的那一份是同一个读法，否则「后台显示已开启、用户还是领不到」
 * 这类问题只能靠猜。
 */
@Service
public class BirthdayAdminService {

    private final JdbcTemplate jdbc;
    private final BirthdayGiftService giftService;

    public BirthdayAdminService(JdbcTemplate jdbc, BirthdayGiftService giftService) {
        this.jdbc = jdbc;
        this.giftService = giftService;
    }

    /** 配置 + 本年度概览，一次给全，避免页面开两个接口。 */
    public Map<String, Object> overview() {
        var config = giftService.config();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", config.enabled());
        map.put("giftType", config.giftType());
        map.put("pointsAmount", config.pointsAmount());
        map.put("windowMode", config.windowMode());
        map.put("windowDays", config.windowDays());
        map.put("title", config.title());
        map.put("subtitle", config.subtitle());
        map.put("description", config.description());
        map.putAll(stats(java.time.LocalDate.now().getYear()));
        return map;
    }

    @Transactional
    public void updateConfig(BirthdayConfigWriteRequest request) {
        if (request.getEnabled() == null) throw new BusinessException("请选择是否开启生日礼遇");
        // 空串按「没选」处理，与 null 同义。
        // 之前统一走 trim(value, fallback)，但那个兜底只在 null 时生效：
        // 运营把下拉框清空后提交 ""，会一路走到「赠饮模式将在下一版本开放」这条
        // 与事实无关的报错上，看起来像功能坏了。
        String giftType = isBlank(request.getGiftType()) ? "POINTS" : request.getGiftType().trim().toUpperCase();
        if (!"POINTS".equals(giftType)) {
            throw new BusinessException("赠饮模式将在下一版本开放，当前只能发放积分");
        }
        if (request.getPointsAmount() == null || request.getPointsAmount() < 1) {
            throw new BusinessException("发放积分必须大于 0");
        }
        if (request.getPointsAmount() > 10000) {
            throw new BusinessException("单次发放积分不能超过 10000，请确认是否填错");
        }
        String windowMode = isBlank(request.getWindowMode()) ? "MONTH" : request.getWindowMode().trim().toUpperCase();
        if (!"MONTH".equals(windowMode) && !"AROUND".equals(windowMode)) {
            throw new BusinessException("窗口模式只能是 MONTH 或 AROUND");
        }
        int windowDays = request.getWindowDays() == null ? 3 : request.getWindowDays();
        if (windowDays < 0 || windowDays > 30) {
            throw new BusinessException("前后天数必须在 0-30 之间");
        }
        String title = trim(request.getTitle(), "生日礼遇");
        if (title.isEmpty() || title.length() > 40) throw new BusinessException("标题需在 1-40 字之间");
        String subtitle = trim(request.getSubtitle(), "");
        if (subtitle.length() > 60) throw new BusinessException("副标题最多 60 字");
        String description = trim(request.getDescription(), "");
        if (description.length() > 200) throw new BusinessException("说明最多 200 字");

        jdbc.update("""
                INSERT INTO birthday_gift_config
                    (id, enabled, gift_type, points_amount, window_mode, window_days, title, subtitle, description)
                VALUES (1,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    enabled = VALUES(enabled), gift_type = VALUES(gift_type),
                    points_amount = VALUES(points_amount), window_mode = VALUES(window_mode),
                    window_days = VALUES(window_days), title = VALUES(title),
                    subtitle = VALUES(subtitle), description = VALUES(description)
                """,
                request.getEnabled(), giftType, request.getPointsAmount(), windowMode, windowDays,
                title, subtitle, description);
    }

    public PageResult<Map<String, Object>> gifts(PageQuery query, Integer year, String keyword) {
        int pageSize = query.normalizedPageSize();
        long offset = (long) (query.normalizedPageNum() - 1) * pageSize;
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        String like = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";

        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM member_birthday_gifts g
                JOIN members m ON m.id = g.member_id
                WHERE g.gift_year = ?
                  AND (? IS NULL OR m.display_name LIKE ? OR m.phone LIKE ? OR g.member_id LIKE ?)
                """, Long.class, y, like, like, like, like);

        List<Map<String, Object>> rows = jdbc.query("""
                SELECT g.id, g.member_id AS memberId, m.display_name AS displayName, m.phone,
                       g.gift_year AS giftYear, g.gift_type AS giftType, g.amount,
                       g.points_log_id AS pointsLogId,
                       DATE_FORMAT(g.created_at, '%Y-%m-%d %H:%i') AS createdAt
                FROM member_birthday_gifts g
                JOIN members m ON m.id = g.member_id
                WHERE g.gift_year = ?
                  AND (? IS NULL OR m.display_name LIKE ? OR m.phone LIKE ? OR g.member_id LIKE ?)
                ORDER BY g.created_at DESC, g.id DESC
                LIMIT ?, ?
                """, (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("memberId", rs.getString("memberId"));
            row.put("displayName", rs.getString("displayName"));
            row.put("phone", rs.getString("phone"));
            row.put("giftYear", rs.getInt("giftYear"));
            row.put("giftType", rs.getString("giftType"));
            row.put("amount", rs.getInt("amount"));
            row.put("pointsLogId", rs.getObject("pointsLogId"));
            row.put("createdAt", rs.getString("createdAt"));
            return row;
        }, y, like, like, like, like, offset, pageSize);

        return PageResult.of(rows, total == null ? 0 : total,
                query.normalizedPageNum(), pageSize);
    }

    /** 年度概览：发放人次、积分合计、可发人群（已填生日的会员数）。 */
    public Map<String, Object> stats(int year) {
        Map<String, Object> map = new LinkedHashMap<>();
        Long gifts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_birthday_gifts WHERE gift_year = ?", Long.class, year);
        Long points = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM member_birthday_gifts WHERE gift_year = ? AND gift_type = 'POINTS'",
                Long.class, year);
        Long withBirthday = jdbc.queryForObject(
                "SELECT COUNT(*) FROM members WHERE birthday_month IS NOT NULL AND birthday_day IS NOT NULL",
                Long.class);
        map.put("year", year);
        map.put("giftCount", gifts == null ? 0 : gifts);
        map.put("pointsTotal", points == null ? 0 : points);
        map.put("birthdayMemberCount", withBirthday == null ? 0 : withBirthday);
        return map;
    }
}
