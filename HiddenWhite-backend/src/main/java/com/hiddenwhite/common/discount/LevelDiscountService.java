package com.hiddenwhite.common.discount;

import com.hiddenwhite.shop.service.MemberLevels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 会员等级折扣：策略读取、每日折扣解析、配置写入、会员视角展示。
 *
 * <p><b>三条不变式</b>
 * <ol>
 *   <li><b>等级由代码定义，策略由数据定义</b>：等级阶梯在 {@link MemberLevels}（晋升只升不降、
 *       有成长值门槛），折扣率在 {@code member_level_policies}。表里不重复定义等级，
 *       否则「涨了成长值却没升等级」这类问题会出现在两处而只修一处。</li>
 *   <li><b>折扣只在前端不可见的服务端计算</b>：报价与下单走同一个方法，
 *       前端拿到的只是结论（折扣率 + 金额 + 名称），不参与任何乘法。</li>
 *   <li><b>未知等级一律无折扣</b>：会员的 {@code member_level} 允许被手工改成阶梯外的值，
 *       查不到策略时安静地不打折，而不是抛错让顾客下不了单。</li>
 * </ol>
 *
 * <p><b>与其它优惠的关系</b>（判定见 {@code OrderService.calculate}）：
 * 会员折扣与首单礼遇互斥（取优惠更大者），与优惠券叠加（券在折后金额上抵扣）。
 */
@Service
public class LevelDiscountService {

    /**
     * 折扣率下限（1 折）。
     *
     * <p>不是业务上限，而是防误填：0.1 已经意味着 10 元的咖啡卖 1 元，
     * 再低几乎只可能是把「8.5」写成「0.85」之类的笔误。真要低于 1 折的活动，
     * 应该走优惠券或专门的活动，而不是改会员等级折扣。
     */
    private static final BigDecimal MIN_RATE = new BigDecimal("0.100");

    private static final BigDecimal ONE = BigDecimal.ONE;

    /** 折扣率统一保留 3 位小数：0.850 = 8.5 折，0.875 = 8.75 折。 */
    private static final int RATE_SCALE = 3;

    private static final String POLICY_COLUMNS =
            "level_key,level_name,min_growth,discount_enabled,base_rate,min_spend,note,sort_order";

    private static final String[] WEEKDAY_LABELS =
            {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /**
     * 详情页底部的规则说明。
     *
     * <p>放在服务端而不是前端硬编码：这些句子描述的是<b>服务端的计算规则</b>
     * （互斥、叠加、门槛基数），规则变了而文案没改，就是让顾客按错的预期下单。
     */
    private static final List<String> RULES = List.of(
            "折扣按订单商品金额计算，不含包装费",
            "会员折扣与首单礼遇互斥，系统自动取更优惠的一边",
            "可与优惠券叠加：券在折后金额上抵扣，券的门槛仍按原价判定",
            "单笔订单只按当前等级的一档折扣结算，不叠加其它等级");

    private final JdbcTemplate jdbc;

    public LevelDiscountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- 读取 ----

    /**
     * 全部策略，按阶梯顺序。
     *
     * <p>表为空时返回空列表而不是 500：折扣是「锦上添花」的功能，
     * 配置表被清空应该表现为「不打折」，而不是整个点单链路挂掉。
     */
    public List<LevelDiscountPolicy> policies() {
        List<PolicyRow> rows = jdbc.query(
                "SELECT " + POLICY_COLUMNS + " FROM member_level_policies ORDER BY sort_order,level_key",
                (rs, index) -> new PolicyRow(
                        rs.getString("level_key"), rs.getString("level_name"),
                        rs.getInt("min_growth"), rs.getBoolean("discount_enabled"),
                        rs.getBigDecimal("base_rate"), rs.getBigDecimal("min_spend"),
                        rs.getString("note"), rs.getInt("sort_order")));
        if (rows.isEmpty()) {
            return List.of();
        }

        // 每日覆盖一次读完再归位：逐等级查会变成 N+1（5 个等级 = 5 次往返）
        Map<String, Map<Integer, BigDecimal>> daily = new LinkedHashMap<>();
        jdbc.query("SELECT level_key,weekday,rate FROM member_level_daily_rates",
                rs -> {
                    daily.computeIfAbsent(rs.getString("level_key"), key -> new LinkedHashMap<>())
                            .put(rs.getInt("weekday"), rs.getBigDecimal("rate"));
                });

        List<LevelDiscountPolicy> result = new ArrayList<>(rows.size());
        for (PolicyRow row : rows) {
            result.add(row.toPolicy(daily.getOrDefault(row.levelKey(), Map.of())));
        }
        return List.copyOf(result);
    }

    /** 单个等级的策略；未配置返回空。 */
    public Optional<LevelDiscountPolicy> policy(String levelKey) {
        if (levelKey == null || levelKey.isBlank()) {
            return Optional.empty();
        }
        return policies().stream()
                .filter(policy -> policy.levelKey().equals(levelKey))
                .findFirst();
    }

    // ---- 计算 ----

    /**
     * 计算某等级在某天的折扣。
     *
     * @param levelKey 会员等级（可空，空则无折扣）
     * @param subtotal 订单商品金额（折扣按商品金额算，不含包装费）
     * @param date     归属日期（门店时区）
     * @return 不打折时返回 {@code null}；否则返回折扣率、金额与展示文案
     */
    public AppliedDiscount evaluate(String levelKey, BigDecimal subtotal, LocalDate date) {
        if (levelKey == null || levelKey.isBlank() || subtotal == null || subtotal.signum() <= 0) {
            return null;
        }
        Optional<LevelDiscountPolicy> found = policy(levelKey);
        if (found.isEmpty()) {
            // 阶梯外的等级（如被手工改成「金卡会员」）：安静地不打折
            return null;
        }
        LevelDiscountPolicy policy = found.get();
        int weekday = date.getDayOfWeek().getValue();
        if (!policy.activeOn(weekday)) {
            return null;
        }
        if (subtotal.compareTo(policy.minSpend()) < 0) {
            return null;
        }

        BigDecimal rate = normalize(policy.rateOn(weekday));
        BigDecimal amount = subtotal.multiply(ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            return null;
        }
        return new AppliedDiscount(policy.levelKey(), policy.levelName(), rate, weekday, amount,
                label(policy.levelName(), rate));
    }

    /**
     * 折扣金额的展示文案，如「黄金会员 8.5 折」。
     *
     * <p>换算在服务端做：{@code 0.85 × 10 = 8.5} 这种乘法交给各端各写一遍，
     * 迟早会出现一处显示「8.5 折」、另一处显示「85 折」。
     */
    public static String label(String levelName, BigDecimal rate) {
        BigDecimal tenths = rate.multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return levelName + " " + tenths.toPlainString() + " 折";
    }

    // ---- 会员视角（点单端展示） ----

    /**
     * 当前会员的折扣视图：今日是否打折、整周日历、距下一等级还差多少。
     *
     * <p>「今天打不打折」由服务端判定，前端不做任何日期比较 ——
     * 星期几与时区在客户端各解释一遍，必然出现「App 说 8.5 折、结算却是原价」。
     *
     * @param growthValue 当前成长值，用于算「距下一等级还差多少」
     */
    public MemberView view(String levelKey, int growthValue) {
        int todayWeekday = LocalDate.now(ZONE).getDayOfWeek().getValue();
        Optional<LevelDiscountPolicy> found = policy(levelKey);
        String levelName = found.map(LevelDiscountPolicy::levelName)
                .orElseGet(() -> levelKey == null ? "" : levelKey);
        WeekView week = weekView(found.orElse(null), levelName, todayWeekday);

        return new MemberView(levelKey, levelName,
                found.isPresent() && found.get().enabled(),
                week.todayRate(), week.todayLabel(),
                found.map(LevelDiscountPolicy::note).orElse(""),
                found.map(LevelDiscountPolicy::minSpend).orElse(BigDecimal.ZERO),
                week.todayActive(), week.week(), nextLevel(levelKey, growthValue));
    }

    /**
     * 全部等级的折扣方案（点单端「会员折扣」详情页）。
     *
     * <p><b>一次读全表再组装</b>，不是循环调用 {@link #view}：后者每个等级都要读一遍策略与
     * 每日覆盖（5 个等级 = 5 轮往返），而页面上这些数据本来就该一次给全。
     *
     * <p>每个等级都带上 {@code current}（是否当前等级）与 {@code reached}（成长值是否已达标）：
     * 「我现在是哪档、还差多少到下一档」是这类页面被打开的主要原因，
     * 让前端拿 {@code member_level} 与门槛自己比，等于把判定逻辑复制一份到客户端。
     */
    public Catalog overview(String currentLevel, int growthValue) {
        int todayWeekday = LocalDate.now(ZONE).getDayOfWeek().getValue();
        Map<String, LevelDiscountPolicy> byKey = policies().stream()
                .collect(Collectors.toMap(LevelDiscountPolicy::levelKey, policy -> policy, (a, b) -> a));

        List<LevelCard> cards = new ArrayList<>(MemberLevels.levels().size());
        for (MemberLevels.Level level : MemberLevels.levels()) {
            LevelDiscountPolicy policy = byKey.get(level.name());
            WeekView week = weekView(policy, level.name(), todayWeekday);
            boolean reached = growthValue >= level.threshold();
            cards.add(new LevelCard(
                    level.name(), level.name(), level.threshold(),
                    policy != null && policy.enabled(),
                    policy == null ? ONE : policy.baseRate(),
                    policy == null ? BigDecimal.ZERO : policy.minSpend(),
                    policy == null ? "" : policy.note(),
                    week.todayRate(), week.todayLabel(), week.todayActive(), week.week(),
                    level.name().equals(currentLevel),
                    reached,
                    reached ? 0 : level.threshold() - growthValue));
        }
        return new Catalog(currentLevel, growthValue, todayWeekday, List.of(WEEKDAY_LABELS), cards, RULES);
    }

    /**
     * 一周视图。没有策略时也要给满 7 天 —— 缺格会让前端的日历塌成半行。
     */
    private WeekView weekView(LevelDiscountPolicy policy, String levelName, int todayWeekday) {
        List<DayRate> week = new ArrayList<>(7);
        BigDecimal todayRate = ONE;
        boolean todayActive = false;
        for (int weekday = 1; weekday <= 7; weekday++) {
            BigDecimal rate = policy == null ? ONE : normalize(policy.rateOn(weekday));
            boolean active = policy != null && policy.activeOn(weekday);
            week.add(new DayRate(weekday, WEEKDAY_LABELS[weekday - 1], rate,
                    policy != null && policy.overriddenOn(weekday), weekday == todayWeekday, active));
            if (weekday == todayWeekday) {
                todayRate = rate;
                todayActive = active;
            }
        }
        return new WeekView(week, todayRate, todayActive,
                todayActive ? label(levelName, todayRate) : "");
    }

    /** 一周视图的中转：{@code view} 与 {@code overview} 共用，避免两处各算一遍。 */
    private record WeekView(List<DayRate> week, BigDecimal todayRate, boolean todayActive, String todayLabel) {
    }

    /**
     * 距下一等级还差多少成长值。
     *
     * <p>已在最高等级、或等级不在阶梯内时返回 {@code null} —— 前者没有「下一级」，
     * 后者连当前阶位都无法确定，编一个数字只会误导顾客。
     */
    private NextLevel nextLevel(String levelKey, int growthValue) {
        List<MemberLevels.Level> ladder = MemberLevels.levels();
        int index = -1;
        for (int i = 0; i < ladder.size(); i++) {
            if (ladder.get(i).name().equals(levelKey)) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= ladder.size() - 1) {
            return null;
        }
        MemberLevels.Level next = ladder.get(index + 1);
        int left = Math.max(0, next.threshold() - growthValue);
        return new NextLevel(next.name(), next.threshold(), left);
    }

    // ---- 配置写入（管理端） ----

    /**
     * 保存一个等级的折扣策略。
     *
     * <p>校验都放在服务端：折扣率多写一个小数点就是全体该等级会员的资损，
     * 前端那道「明显填错」的拦截只能当第一道，不能当唯一一道。
     *
     * <p><b>dailyRates 是「覆盖集」不是「全量」</b>：传进来的天会被写入，
     * 没传的天对应的行会被删掉（回落到基准价）。这样运营把某天改回「跟随基准」
     * 只需要清空那个格子，不需要手工填一个与基准相同的数字。
     */
    @Transactional
    public void save(String levelKey, boolean enabled, BigDecimal baseRate, BigDecimal minSpend,
                     String note, Map<Integer, BigDecimal> dailyRates) {
        String key = requireLadderLevel(levelKey);
        BigDecimal rate = requireRate(baseRate, "基准折扣率");
        BigDecimal threshold = minSpend == null ? BigDecimal.ZERO : minSpend;
        if (threshold.signum() < 0) {
            throw LevelDiscountException.invalid("折扣门槛不能为负数");
        }
        String text = note == null ? "" : note.trim();
        if (text.length() > 120) {
            throw LevelDiscountException.invalid("说明不能超过 120 个字");
        }

        Map<Integer, BigDecimal> clean = new LinkedHashMap<>();
        if (dailyRates != null) {
            for (Map.Entry<Integer, BigDecimal> entry : dailyRates.entrySet()) {
                Integer weekday = entry.getKey();
                if (weekday == null || weekday < 1 || weekday > 7) {
                    throw LevelDiscountException.invalid("星期取值必须在 1-7 之间（1 = 周一）");
                }
                clean.put(weekday, requireRate(entry.getValue(), WEEKDAY_LABELS[weekday - 1] + "折扣率"));
            }
        }

        // 等级名与门槛以 MemberLevels 为准：表里这两列只是展示快照，
        // 每次保存时用代码里的阶梯覆盖，避免运营改了名字后与晋升逻辑对不上
        Integer minGrowth = MemberLevels.thresholdOf(key);
        String levelName = key;
        int growth = minGrowth == null ? 0 : minGrowth;
        int order = ladderIndex(key);

        int updated = jdbc.update(
                "UPDATE member_level_policies SET discount_enabled=?, base_rate=?, min_spend=?, note=?, "
                        + "level_name=?, min_growth=?, sort_order=? WHERE level_key=?",
                enabled, rate, threshold, text, levelName, growth, order, key);
        if (updated == 0) {
            // 策略行缺失（表被清空 / 后续新增了等级）：补一行，而不是让运营对着 404 猜
            jdbc.update("INSERT INTO member_level_policies(" + POLICY_COLUMNS + ") VALUES(?,?,?,?,?,?,?,?)",
                    key, levelName, growth, enabled, rate, threshold, text, order);
        }

        jdbc.update("DELETE FROM member_level_daily_rates WHERE level_key=?", key);
        for (Map.Entry<Integer, BigDecimal> entry : clean.entrySet()) {
            jdbc.update("INSERT INTO member_level_daily_rates(level_key,weekday,rate) VALUES(?,?,?)",
                    key, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 单独启停一个等级。
     *
     * <p>沿用「单字段变更开专用端点」的约定：整行更新带必填校验，
     * 会把「只想关掉折扣」的调用直接拒掉。
     */
    @Transactional
    public void setEnabled(String levelKey, boolean enabled) {
        String key = requireLadderLevel(levelKey);
        int updated = jdbc.update(
                "UPDATE member_level_policies SET discount_enabled=? WHERE level_key=?", enabled, key);
        if (updated == 0) {
            throw LevelDiscountException.notFound("等级策略不存在: " + key + "，请先保存一次配置");
        }
    }

    // ---- 内部 ----

    /**
     * 只允许阶梯内的等级建策略。
     *
     * <p>拒绝阶梯外的值（如被手工改成「金卡会员」的会员）而不是默默接受：
     * 这种等级永远不会被成长值晋升覆盖，给它配折扣等于把资损固化成配置 ——
     * 正确做法是先把该会员的等级改回阶梯内。
     */
    private static String requireLadderLevel(String levelKey) {
        String key = levelKey == null ? "" : levelKey.trim();
        if (key.isEmpty()) {
            throw LevelDiscountException.invalid("请指定会员等级");
        }
        if (MemberLevels.thresholdOf(key) == null) {
            throw LevelDiscountException.invalid("等级「" + key + "」不在成长值阶梯中。"
                    + "请先在会员管理里把该会员改回阶梯内的等级（普通/白银/黄金/铂金/钻石）");
        }
        return key;
    }

    private static BigDecimal requireRate(BigDecimal value, String label) {
        if (value == null) {
            throw LevelDiscountException.invalid(label + "不能为空");
        }
        BigDecimal rate = normalize(value);
        if (rate.compareTo(MIN_RATE) < 0) {
            throw LevelDiscountException.invalid(label + "不能低于 0.1（1 折）；低于 1 折请走优惠券或专门活动");
        }
        if (rate.compareTo(ONE) > 0) {
            throw LevelDiscountException.invalid(label + "不能大于 1（1 表示不打折，折扣价不可能高于原价）");
        }
        return rate;
    }

    private static BigDecimal normalize(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static int ladderIndex(String levelKey) {
        List<MemberLevels.Level> ladder = MemberLevels.levels();
        for (int i = 0; i < ladder.size(); i++) {
            if (ladder.get(i).name().equals(levelKey)) {
                return i;
            }
        }
        return ladder.size();
    }

    /** 策略行中转：先读行，再补上每日覆盖。 */
    private record PolicyRow(String levelKey, String levelName, int minGrowth, boolean enabled,
                             BigDecimal baseRate, BigDecimal minSpend, String note, int sortOrder) {
        LevelDiscountPolicy toPolicy(Map<Integer, BigDecimal> daily) {
            return new LevelDiscountPolicy(levelKey, levelName, minGrowth, enabled,
                    baseRate, minSpend, note == null ? "" : note, sortOrder, daily);
        }
    }

    // ---- 响应结构 ----

    /**
     * 全部等级的折扣方案。
     *
     * @param currentLevel 当前会员的等级键（可能不在阶梯内）
     * @param growthValue  当前成长值：前端据此画「距下一档」进度条，不必反推
     * @param weekdays     周一到周日的中文标签，供前端表头使用
     * @param rules        计算规则说明（服务端下发，与实现同源）
     */
    public record Catalog(String currentLevel, int growthValue, int todayWeekday,
                          List<String> weekdays, List<LevelCard> levels, List<String> rules) {
    }

    /**
     * 单个等级的方案卡片。
     *
     * @param baseRate    基准折扣率；当天有覆盖时以 {@code todayRate} 为准
     * @param note        适用范围/说明（运营可配）
     * @param current     是否当前会员所在等级
     * @param reached     成长值是否已达该等级门槛
     * @param growthLeft  距该门槛还差多少成长值；已达则为 0
     */
    public record LevelCard(
            String levelKey, String levelName, int minGrowth,
            boolean enabled, BigDecimal baseRate, BigDecimal minSpend, String note,
            BigDecimal todayRate, String todayLabel, boolean todayActive, List<DayRate> week,
            boolean current, boolean reached, int growthLeft) {
    }

    /**
     * 点单端会员折扣视图。
     *
     * @param todayRate  今天适用的折扣率（不打折时为 1.000）
     * @param todayLabel 今天折扣的展示文案；不打折为空串
     * @param active     今天是否真的打折
     * @param week       整周日历（固定 7 项，周一开头）
     * @param nextLevel  距下一等级的信息；已最高或等级异常时为 null
     */
    public record MemberView(String levelKey, String levelName, boolean enabled,
                             BigDecimal todayRate, String todayLabel, String note, BigDecimal minSpend,
                             boolean active, List<DayRate> week, NextLevel nextLevel) {
    }

    /**
     * 单日折扣。
     *
     * @param overridden 该天是否有单独配置（否则继承基准）
     * @param today      是否今天
     * @param active     该天是否打折
     */
    public record DayRate(int weekday, String label, BigDecimal rate, boolean overridden,
                          boolean today, boolean active) {
    }

    /** 距下一等级：{@code minGrowth} 为门槛，{@code growthLeft} 为还差多少。 */
    public record NextLevel(String name, int minGrowth, int growthLeft) {
    }
}
