package com.hiddenwhite.common.upsize;

import com.hiddenwhite.common.points.PointsBizType;
import com.hiddenwhite.common.points.PointsLedger;
import com.hiddenwhite.common.points.PointsRefType;
import com.hiddenwhite.shop.domain.ProductOption;
import com.hiddenwhite.shop.service.CatalogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 免费升杯：策略读取、额度判定、占用与释放。
 *
 * <p><b>三条不变式</b>
 * <ol>
 *   <li><b>等级不在本类定义</b>：阶梯在 {@code MemberLevels}，本类只按 {@code level_key}
 *       查策略。查不到就安静地不可用，而不是抛错——会员等级允许被管理端手工改成阶梯外的值，
 *       那种情况下顾客仍然应该能正常下单（只是没有升杯权益）。</li>
 *   <li><b>额度以「本月 USED 记录数」为准</b>，不另存计数器。存计数器就要在
 *       取消、超时、补偿等每条路径上同步减，漏一处就会永久少一次额度；
 *       而记录本身就是账，数一下就知道了。</li>
 *   <li><b>报价不占用额度</b>：{@link #decide} 纯查询，只有下单成功才 {@link #record}。
 *       否则顾客在结算页反复刷新就能把本月额度刷没。</li>
 * </ol>
 *
 * <p><b>与积分账本的关系</b>：升杯是积分账本的又一个调用方，
 * 扣分/退分一律走 {@link PointsLedger#adjust}，不直接 UPDATE {@code members.points}。
 * 事务传播为 {@code REQUIRED}，会自然加入下单/取消的外层事务——
 * 任一步失败整体回滚，不会留下「扣了分没升杯」的孤儿流水。
 */
@Service
public class UpsizeService {

    /** 策略列清单：单查与列表共用，避免投影漂移。 */
    private static final String POLICY_COLUMNS =
            "level_key,level_name,monthly_limit,free_quota,points_cost,from_size,to_size,enabled,note,sort_order";

    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    private final PointsLedger pointsLedger;

    public UpsizeService(JdbcTemplate jdbc, CatalogService catalog, PointsLedger pointsLedger) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.pointsLedger = pointsLedger;
    }

    // ---- 策略 ----

    /** 某等级的策略；等级不在表里（含空等级）返回空。 */
    public Optional<UpsizePolicy> policyOf(String levelKey) {
        if (levelKey == null || levelKey.isBlank()) {
            return Optional.empty();
        }
        List<UpsizePolicy> rows = jdbc.query(
                "SELECT " + POLICY_COLUMNS + " FROM member_upsize_policies WHERE level_key = ?",
                this::mapPolicy, levelKey);
        return rows.stream().findFirst();
    }

    /** 全部策略（管理端配置页，按运营排序）。 */
    public List<UpsizePolicy> policies() {
        return jdbc.query("SELECT " + POLICY_COLUMNS + " FROM member_upsize_policies ORDER BY sort_order, level_key",
                this::mapPolicy);
    }

    // ---- 额度 ----

    /** 本月已占用的次数（{@code status='USED'}）。 */
    public int usedThisMonth(String memberId) {
        return usedIn(memberId, UpsizeMonth.currentKey());
    }

    /** 指定自然月已占用的次数。 */
    public int usedIn(String memberId, String monthKey) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_upsize_records WHERE member_id=? AND month_key=? AND status='USED'",
                Integer.class, memberId, monthKey);
        return count == null ? 0 : count;
    }

    /**
     * 判定「这一次能不能升、怎么结算」。
     *
     * <p>判定顺序：策略存在 → 启用 → 杯型能取到差价 → 额度未耗尽 → 本单确有可升的行。
     * 前四项任一不满足都直接返回不可用，<b>不会</b>再看 {@code hasEligibleItem}——
     * 顾客本月次数用完时，告诉他「本月已用完」比「本单已是最大杯型」有用得多。
     *
     * <p><b>为什么用 {@link Predicate} 而不是传订单行</b>：本类只管额度，不该知道
     * {@code OrderItemRequest} 长什么样。谓词的入参是查到的策略（含 {@code fromSize}），
     * 由调用方自己判断「本单有没有起始杯型的行」。这样本类既能服务下单，
     * 也能服务「会员页看一眼状态」（传 {@code p -> true}）。
     *
     * @param memberId        会员 id
     * @param levelKey        会员当前等级（由调用方从 members 读取）
     * @param hasEligibleItem 入参是策略，返回「本单是否存在处于起始杯型的商品行」
     */
    public UpsizeDecision decide(String memberId, String levelKey, Predicate<UpsizePolicy> hasEligibleItem) {
        UpsizePolicy policy = policyOf(levelKey).orElse(null);
        if (policy == null) {
            return UpsizeDecision.unavailable(UpsizeUnavailableReason.NO_POLICY, null, BigDecimal.ZERO, 0);
        }

        BigDecimal diff = sizeDiffOf(policy);
        int balance = pointsLedger.balance(memberId);

        if (!policy.enabled()) {
            return UpsizeDecision.unavailable(UpsizeUnavailableReason.DISABLED, policy, diff, balance);
        }
        if (diff == null) {
            // 策略里的杯型 label 在 product_options 里找不到：配置漂移，使用侧安静降级
            return UpsizeDecision.unavailable(UpsizeUnavailableReason.SIZE_MISSING, policy, BigDecimal.ZERO, balance);
        }

        int used = usedThisMonth(memberId);
        if (policy.payTypeAt(used) == UpsizePayType.UNAVAILABLE) {
            return UpsizeDecision.unavailable(policy.reasonAt(used), policy, diff, balance);
        }
        if (hasEligibleItem != null && !hasEligibleItem.test(policy)) {
            return UpsizeDecision.unavailable(UpsizeUnavailableReason.NO_ELIGIBLE_ITEM, policy, diff, balance);
        }
        return UpsizeDecision.usable(policy, used, diff, balance);
    }

    /**
     * 起始杯型 → 目标杯型的差价；任一 label 不存在或差价不为正时返回 {@code null}。
     *
     * <p>返回 {@code null} 而不是抛异常：杯型是运营数据，改一次名字就会让这里失效，
     * 但「升杯不可用」不该演变成「顾客下不了单」。
     * 配置漂移由 {@code UpsizePolicyTest} 在构建期拦住，不靠运行时崩溃暴露。
     */
    public BigDecimal sizeDiffOf(UpsizePolicy policy) {
        if (policy == null) {
            return null;
        }
        try {
            ProductOption from = catalog.size(policy.fromSize());
            ProductOption to = catalog.size(policy.toSize());
            BigDecimal diff = to.price().subtract(from.price());
            return diff.signum() > 0 ? diff : null;
        } catch (IllegalArgumentException notFound) {
            return null;
        }
    }

    // ---- 占用与释放 ----

    /**
     * 占用一次升杯额度并扣分。
     *
     * <p>必须在<b>下单事务内</b>调用：写记录与扣分要么一起成功，要么一起回滚。
     *
     * <p>{@code orderId} 在订单落库之后才存在，所以本方法收的是「已落库的订单 id」，
     * 而不是在这里生成订单——订单号与取餐号必须在同一个 {@code synchronized} 块里取。
     *
     * @return 新记录的 id，供积分流水 {@code ref_id} 反查
     * @throws com.hiddenwhite.common.points.PointsException 积分不足（409）
     */
    @Transactional
    public long record(String memberId, String orderId, UpsizeDecision decision) {
        UpsizePayType payType = decision.payType();
        int cost = decision.pointsCost();
        LocalDateTime now = LocalDateTime.now(ZONE);

        jdbc.update("INSERT INTO member_upsize_records"
                        + "(member_id,level_key,month_key,pay_type,points_cost,size_from,size_to,order_id,status,created_at,remark) "
                        + "VALUES(?,?,?,?,?,?,?,?, 'USED', ?, ?)",
                memberId, decision.levelKey(), UpsizeMonth.currentKey(), payType.name(), cost,
                decision.fromSize(), decision.toSize(), orderId, now,
                payType == UpsizePayType.FREE ? "本月免费额度" : "积分升杯");

        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        long recordId = id == null ? 0L : id;

        if (cost > 0) {
            // 与订单同一事务：扣分失败会连带订单一起回滚，不会出现「升了杯没扣分」
            pointsLedger.adjust(memberId, -cost, PointsBizType.UP_SIZE, PointsRefType.UP_SIZE,
                    String.valueOf(recordId), "升杯至" + decision.toSize());
        }
        return recordId;
    }

    /**
     * 订单取消时释放升杯额度并退分。
     *
     * <p><b>为什么必须退</b>：顾客没喝到这杯咖啡，额度与积分都不该被消耗。
     * 与优惠券的「取消即退回」是同一条原则。
     *
     * <p><b>幂等</b>：条件更新 {@code status='USED'}，重复调用第二次影响 0 行，
     * 不会重复退分。手动取消与超时取消都会走到这里，两处重复触发也安全。
     *
     * @return 实际退回的积分总额
     */
    @Transactional
    public int releaseForOrder(String orderId) {
        List<UsedRecord> rows = jdbc.query(
                "SELECT id, member_id, points_cost FROM member_upsize_records WHERE order_id=? AND status='USED'",
                (rs, row) -> new UsedRecord(rs.getLong("id"), rs.getString("member_id"), rs.getInt("points_cost")),
                orderId);

        int refunded = 0;
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (UsedRecord row : rows) {
            int updated = jdbc.update(
                    "UPDATE member_upsize_records SET status='RELEASED', released_at=? WHERE id=? AND status='USED'",
                    now, row.id());
            if (updated == 0) {
                // 已被并发路径释放，跳过退分，避免重复加积分
                continue;
            }
            if (row.pointsCost() > 0) {
                pointsLedger.adjust(row.memberId(), row.pointsCost(), PointsBizType.UP_SIZE_REFUND,
                        PointsRefType.UP_SIZE, String.valueOf(row.id()), "订单取消退回升杯积分");
                refunded += row.pointsCost();
            }
        }
        return refunded;
    }

    // ---- 映射 ----

    private UpsizePolicy mapPolicy(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UpsizePolicy(
                rs.getString("level_key"),
                rs.getString("level_name"),
                rs.getInt("monthly_limit"),
                rs.getInt("free_quota"),
                rs.getInt("points_cost"),
                rs.getString("from_size"),
                rs.getString("to_size"),
                rs.getBoolean("enabled"),
                rs.getString("note"),
                rs.getInt("sort_order"));
    }

    /** 释放时需要的三个字段。 */
    private record UsedRecord(long id, String memberId, int pointsCost) {
    }
}
