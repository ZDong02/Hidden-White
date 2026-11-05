package com.hiddenwhite.admin.leveldiscount.service;

import com.hiddenwhite.admin.leveldiscount.domain.dto.LevelDiscountWriteRequest;
import com.hiddenwhite.common.discount.LevelDiscountException;
import com.hiddenwhite.common.discount.LevelDiscountPolicy;
import com.hiddenwhite.common.discount.LevelDiscountService;
import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.shop.service.MemberLevels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 会员折扣运营端：策略总览与写入。
 *
 * <p><b>等级列表以 {@link MemberLevels} 为准，不是以表为准</b>：
 * 表里少一行（配置从未保存过 / 被清空）时，页面仍要显示出这个等级 ——
 * 运营看不到的等级等于不存在，也就永远不会被配置。
 *
 * <p><b>孤儿等级要显式暴露出来</b>：管理端会员编辑允许手写等级名，
 * 历史上出现过「金卡会员」这种既不在阶梯、也不会被成长值晋升覆盖的值。
 * 这类会员拿不到任何折扣，页面上必须提示运营去改，而不是安静地跳过。
 */
@Service
public class LevelDiscountAdminService {

    private static final String[] WEEKDAY_LABELS =
            {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final JdbcTemplate jdbc;
    private final LevelDiscountService discounts;

    public LevelDiscountAdminService(JdbcTemplate jdbc, LevelDiscountService discounts) {
        this.jdbc = jdbc;
        this.discounts = discounts;
    }

    /** 配置总览：每个等级一行 + 成员数 + 孤儿等级提示。 */
    public Map<String, Object> overview() {
        Map<String, LevelDiscountPolicy> byKey = discounts.policies().stream()
                .collect(Collectors.toMap(LevelDiscountPolicy::levelKey, Function.identity(), (a, b) -> a));
        Map<String, Integer> memberCounts = memberCounts();
        List<MemberLevels.Level> ladder = MemberLevels.levels();

        List<Map<String, Object>> levels = new ArrayList<>(ladder.size());
        List<Map<String, Object>> orphanLevels = new ArrayList<>();

        for (MemberLevels.Level level : ladder) {
            levels.add(levelView(level.name(), level.threshold(),
                    byKey.get(level.name()), memberCounts.getOrDefault(level.name(), 0)));
        }

        // 会员身上出现了阶梯外的等级：这些会员永远拿不到折扣
        for (Map.Entry<String, Integer> entry : memberCounts.entrySet()) {
            if (MemberLevels.thresholdOf(entry.getKey()) == null) {
                Map<String, Object> orphan = new LinkedHashMap<>();
                orphan.put("levelKey", entry.getKey());
                orphan.put("memberCount", entry.getValue());
                orphanLevels.add(orphan);
            }
        }

        List<Map<String, Object>> weekdays = new ArrayList<>(7);
        for (int weekday = 1; weekday <= 7; weekday++) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("weekday", weekday);
            day.put("label", WEEKDAY_LABELS[weekday - 1]);
            weekdays.add(day);
        }

        List<Map<String, Object>> ladderView = ladder.stream().map(level -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", level.name());
            row.put("minGrowth", level.threshold());
            return row;
        }).toList();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("levels", levels);
        map.put("weekdays", weekdays);
        map.put("ladder", ladderView);
        map.put("orphanLevels", orphanLevels);
        map.put("todayWeekday", java.time.LocalDate.now(com.hiddenwhite.common.domain.Constants.ZONE)
                .getDayOfWeek().getValue());
        map.put("totalMembers", memberCounts.values().stream().mapToInt(Integer::intValue).sum());
        return map;
    }

    /**
     * 保存一个等级的折扣策略。
     *
     * <p>把 {@code dailyRates} 的字符串键转成星期序号放在这一层：
     * JSON 对象键天然是字符串，让领域服务去处理这种传输格式的细节不合适。
     */
    public void save(LevelDiscountWriteRequest request) {
        if (request == null) {
            throw new BusinessException("请提交折扣配置");
        }
        Map<Integer, BigDecimal> daily = new LinkedHashMap<>();
        if (request.dailyRates() != null) {
            for (Map.Entry<String, BigDecimal> entry : request.dailyRates().entrySet()) {
                daily.put(parseWeekday(entry.getKey()), entry.getValue());
            }
        }
        try {
            discounts.save(request.levelKey(), Boolean.TRUE.equals(request.enabled()),
                    request.baseRate(), request.minSpend(), request.note(), daily);
        } catch (LevelDiscountException exception) {
            // 管理端统一走 R.error，把领域异常的状态码带进 code 字段
            throw new BusinessException(exception.status().value(), exception.getMessage());
        }
    }

    /** 单独启停：整行更新会把「只想关掉」的调用拒在必填校验上。 */
    public void setEnabled(String levelKey, Boolean enabled) {
        if (enabled == null) {
            throw new BusinessException("请指定启用状态");
        }
        try {
            discounts.setEnabled(levelKey, enabled);
        } catch (LevelDiscountException exception) {
            throw new BusinessException(exception.status().value(), exception.getMessage());
        }
    }

    // ---- 内部 ----

    private Map<String, Object> levelView(String levelKey, int minGrowth,
                                          LevelDiscountPolicy policy, int memberCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("levelKey", levelKey);
        map.put("levelName", policy == null ? levelKey : policy.levelName());
        map.put("minGrowth", minGrowth);
        map.put("enabled", policy != null && policy.enabled());
        map.put("baseRate", policy == null ? BigDecimal.ONE : policy.baseRate());
        map.put("minSpend", policy == null ? BigDecimal.ZERO : policy.minSpend());
        map.put("note", policy == null ? "" : policy.note());
        // 只回覆盖项：前端据此区分「显式配置」与「继承基准」，留空的格子就是没配
        map.put("dailyRates", policy == null ? Map.of() : policy.dailyRates());
        map.put("memberCount", memberCount);
        map.put("configured", policy != null);
        return map;
    }

    private Map<String, Integer> memberCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("SELECT member_level, COUNT(*) AS total FROM members GROUP BY member_level ORDER BY total DESC",
                rs -> {
                    counts.put(rs.getString("member_level"), rs.getInt("total"));
                });
        return counts;
    }

    private static int parseWeekday(String value) {
        try {
            int weekday = Integer.parseInt(value == null ? "" : value.trim());
            if (weekday < 1 || weekday > 7) {
                throw new NumberFormatException("out of range");
            }
            return weekday;
        } catch (NumberFormatException exception) {
            throw new BusinessException("星期取值不合法：" + value + "（应为 1-7，1 = 周一）");
        }
    }
}
