package com.hiddenwhite.admin.upsize.service;

import com.hiddenwhite.admin.upsize.domain.dto.UpsizePolicyWriteRequest;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.upsize.UpsizeMonth;
import com.hiddenwhite.common.upsize.UpsizePolicy;
import com.hiddenwhite.common.upsize.UpsizeService;
import com.hiddenwhite.shop.service.MemberLevels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 免费升杯运营接口的服务层。
 *
 * <p><b>额度与积分价改动的影响面</b>：一次改动会影响该等级全体会员的下一单，
 * 所以写入前要做两道校验——等级必须存在于阶梯、杯型必须真实存在于
 * {@code product_options}。放行的错误配置不会报错，只会让升杯静默失效，
 * 而运营在页面上看不出任何异常。
 */
@Service
public class UpsizeAdminService {

    /** 允许的杯型来源：点单端实际可选的 SIZE 选项，避免手写出不存在的杯型。 */
    private static final String SIZE_SQL =
            "SELECT label FROM product_options WHERE option_type='SIZE' AND enabled=1 ORDER BY sort_order";

    private final JdbcTemplate jdbc;
    private final UpsizeService upsizeService;

    public UpsizeAdminService(JdbcTemplate jdbc, UpsizeService upsizeService) {
        this.jdbc = jdbc;
        this.upsizeService = upsizeService;
    }

    /**
     * 五个等级的策略 + 本月使用量 + 可选杯型。
     *
     * <p>{@code usedThisMonth} 按 {@code level_key} 统计而不是按会员：
     * 运营看的是「这一档这个月被用了多少次」，用来判断额度定得松不松。
     */
    public Map<String, Object> overview() {
        String month = UpsizeMonth.currentKey();
        List<Map<String, Object>> levels = new ArrayList<>();
        for (UpsizePolicy policy : upsizeService.policies()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("levelKey", policy.levelKey());
            row.put("levelName", policy.levelName());
            row.put("monthlyLimit", policy.monthlyLimit());
            row.put("freeQuota", policy.freeQuota());
            row.put("pointsCost", policy.pointsCost());
            row.put("fromSize", policy.fromSize());
            row.put("toSize", policy.toSize());
            row.put("enabled", policy.enabled());
            row.put("note", policy.note());
            row.put("sortOrder", policy.sortOrder());
            BigDecimal diff = upsizeService.sizeDiffOf(policy);
            row.put("sizeDiff", diff);
            // 杯型配错时差价为 null，页面上要能看见，而不是显示一个 0 元升杯
            row.put("sizeValid", diff != null);
            row.put("memberCount", count(
                    "SELECT COUNT(*) FROM members WHERE member_level = ?", policy.levelKey()));
            row.put("usedThisMonth", count(
                    "SELECT COUNT(*) FROM member_upsize_records WHERE level_key=? AND month_key=? AND status='USED'",
                    policy.levelKey(), month));
            levels.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monthKey", month);
        result.put("levels", levels);
        result.put("sizes", jdbc.queryForList(SIZE_SQL, String.class));
        result.put("ladder", MemberLevels.levels());
        result.put("totalMembers", count("SELECT COUNT(*) FROM members WHERE id <> 'guest'"));
        return result;
    }

    /**
     * 保存一个等级的升杯策略。
     *
     * <p>字段可空：为 null 的字段保留原值。这样管理端只改一个字段时不必回传整行，
     * 也避免「前端没传的列被写成 null」这种典型事故。
     */
    public void save(UpsizePolicyWriteRequest request) {
        String levelKey = request.levelKey() == null ? "" : request.levelKey().trim();
        if (levelKey.isEmpty()) {
            throw new IllegalArgumentException("等级不能为空");
        }
        if (MemberLevels.thresholdOf(levelKey) == null) {
            // 等级阶梯是代码常量，管理端不能凭空造一个「金卡会员」
            throw new IllegalArgumentException("等级不存在: " + levelKey);
        }

        UpsizePolicy current = upsizeService.policyOf(levelKey)
                .orElseThrow(() -> new IllegalArgumentException("该等级还没有升杯策略: " + levelKey));

        // -1 表示不限，允许；小于 -1 只可能是手抖多打了一个负号
        int monthlyLimit = clampUnlimited(request.monthlyLimit(), current.monthlyLimit(), "每月总次数");
        int freeQuota = clampUnlimited(request.freeQuota(), current.freeQuota(), "每月免费次数");
        int pointsCost = request.pointsCost() == null ? current.pointsCost() : request.pointsCost();
        if (pointsCost < 0) {
            throw new IllegalArgumentException("积分价不能为负");
        }
        // 总次数是硬上限：配得比免费次数还小，会让「还有免费额度」的等级实际用不了
        if (monthlyLimit != UpsizePolicy.UNLIMITED && freeQuota != UpsizePolicy.UNLIMITED
                && monthlyLimit < freeQuota) {
            throw new IllegalArgumentException("每月总次数不能小于免费次数");
        }

        String fromSize = blankTo(request.fromSize(), current.fromSize());
        String toSize = blankTo(request.toSize(), current.toSize());
        ensureSizeExists(fromSize);
        ensureSizeExists(toSize);
        if (fromSize.equals(toSize)) {
            throw new IllegalArgumentException("起始杯型与目标杯型不能相同");
        }

        jdbc.update("UPDATE member_upsize_policies SET monthly_limit=?,free_quota=?,points_cost=?,"
                        + "from_size=?,to_size=?,enabled=?,note=? WHERE level_key=?",
                monthlyLimit, freeQuota, pointsCost, fromSize, toSize,
                request.enabled() == null ? current.enabled() : request.enabled(),
                request.note() == null ? current.note() : request.note().trim(),
                levelKey);
    }

    /**
     * 使用记录（分页）。
     *
     * <p>默认按创建时间倒序：运营看记录通常是为了查「刚刚那笔为什么扣了分」，
     * 而不是从头翻。
     */
    public PageResult<Map<String, Object>> records(PageQuery query, String monthKey, String payType,
                                                   String status, String keyword) {
        int pageNum = query.normalizedPageNum();
        int pageSize = query.normalizedPageSize();

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (monthKey != null && !monthKey.isBlank()) {
            where.append(" AND r.month_key = ? ");
            args.add(monthKey.trim());
        }
        if (payType != null && !payType.isBlank()) {
            where.append(" AND r.pay_type = ? ");
            args.add(payType.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ? ");
            args.add(status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            // 昵称或订单号：两种最常见的「我只记得其中一样」
            where.append(" AND (m.display_name LIKE ? OR o.order_number LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }

        String base = " FROM member_upsize_records r "
                + "LEFT JOIN members m ON m.id = r.member_id "
                + "LEFT JOIN orders o ON o.id = r.order_id ";

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + base + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id, r.member_id, m.display_name, m.phone, r.level_key, r.month_key, r.pay_type,"
                        + " r.points_cost, r.size_from, r.size_to, r.order_id, o.order_number,"
                        + " r.status, r.created_at, r.released_at "
                        + base + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());

        return PageResult.of(rows, total == null ? 0 : total, pageNum, pageSize);
    }

    // ---- 辅助 ----

    private int clampUnlimited(Integer value, int fallback, String label) {
        if (value == null) {
            return fallback;
        }
        if (value < UpsizePolicy.UNLIMITED) {
            throw new IllegalArgumentException(label + "只能是 -1（不限）或非负整数");
        }
        return value;
    }

    private void ensureSizeExists(String size) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_options WHERE option_type='SIZE' AND label=?",
                Integer.class, size);
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("杯型不存在: " + size);
        }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
