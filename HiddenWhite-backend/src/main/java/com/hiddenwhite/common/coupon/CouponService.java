package com.hiddenwhite.common.coupon;

import com.hiddenwhite.common.coupon.CouponWeek.Week;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 优惠券服务：配置读取、券包视图、领取、核销、退券、惰性过期。
 *
 * <p><b>三个不变式</b>
 * <ol>
 *   <li><b>一人一周最多一张</b>：靠 {@code member_coupons} 的
 *       {@code (member_id, week_key)} 唯一索引，不靠「先查后写」——
 *       后者在并发下会双发，而双发意味着白送 6 元，是实打实的资损；</li>
 *   <li><b>一张券只能核销一次</b>：{@code markUsed} 用
 *       {@code WHERE status='UNUSED' AND expires_at>?} 条件更新，
 *       并发/重复提交时第二次影响 0 行，不会出现「一券两单」；</li>
 *   <li><b>面额与门槛是领取时的快照</b>：运营改配置不影响已发出的券，
 *       否则顾客看到的券面和结算时扣的钱会对不上。</li>
 * </ol>
 *
 * <p><b>过期用惰性结算，不加定时任务</b>：理由与
 * {@code RedeemService.settleExpired()} 一致 —— 状态翻转晚几分钟没有业务影响，
 * 而在「券包 / 可用券 / 管理端记录」这些真正会读状态的入口上结算一次，
 * 读到的一定是结算后的数据，零基础设施成本。
 */
@Service
public class CouponService {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HINT = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    /** 券列清单：单查、列表、可用券共用，避免投影漂移。 */
    private static final String COLUMNS =
            "id,member_id,week_key,title,amount,min_spend,status,claimed_at,expires_at,used_at,order_id,discount_amount";

    private final JdbcTemplate jdbc;

    public CouponService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- 配置 ----

    /** 读取配置；表为空时退回默认值（宁可按默认发，也不要整块 500）。 */
    public CouponConfig config() {
        List<CouponConfig> rows = jdbc.query(
                "SELECT enabled,amount,min_spend,valid_days,max_held,total_stock,issued_count,"
                        + "title,subtitle,description FROM coupon_config WHERE id=1",
                (rs, index) -> new CouponConfig(
                        rs.getBoolean("enabled"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("min_spend"),
                        rs.getInt("valid_days"),
                        rs.getInt("max_held"),
                        rs.getObject("total_stock", Integer.class),
                        rs.getInt("issued_count"),
                        rs.getString("title"),
                        rs.getString("subtitle"),
                        rs.getString("description")));
        return rows.isEmpty() ? CouponConfig.DEFAULT : rows.get(0);
    }

    // ---- 券包 ----

    /**
     * 会员券包视图：本周领取状态 + 券列表 + 统计。
     *
     * <p>「能不能领」由服务端算好下发（{@link WeekState}），前端不做任何日期比较。
     * 重置时间同理：周计算涉及时区与跨年，各机型各解释一遍必然出现
     * 「我这显示能领、点下去说已领过」。
     */
    @Transactional
    public Wallet wallet(String memberId) {
        CouponConfig config = config();
        LocalDateTime now = LocalDateTime.now(ZONE);
        settleExpired(memberId, now);

        Week week = CouponWeek.current();
        Optional<Coupon> claimed = findByWeek(memberId, week.key());
        List<Coupon> coupons = list(memberId);
        int unused = (int) coupons.stream()
                .filter(coupon -> coupon.status().isUnused())
                .count();

        WeekState state;
        String stateLabel;
        if (!config.enabled()) {
            state = WeekState.DISABLED;
            stateLabel = "本周好券暂未开放";
        } else if (claimed.isPresent()) {
            state = WeekState.CLAIMED;
            stateLabel = "本周已领取";
        } else if (config.soldOut()) {
            state = WeekState.SOLD_OUT;
            stateLabel = "本周好券已领完";
        } else if (config.maxHeld() > 0 && unused >= config.maxHeld()) {
            state = WeekState.HOLD_LIMIT;
            stateLabel = "手上还有 " + unused + " 张未用";
        } else {
            state = WeekState.CLAIMABLE;
            stateLabel = "本周可领取";
        }

        List<Card> cards = new ArrayList<>(coupons.size());
        for (Coupon coupon : coupons) {
            cards.add(toCard(coupon, config.subtitle(), now));
        }

        return new Wallet(
                config.enabled(), config.title(), config.subtitle(), config.description(),
                config.amount(), config.minSpend(), config.validDays(),
                state, stateLabel, week.key(),
                week.resetAt().format(DATETIME),
                "下周一 " + week.resetAt().format(HINT) + " 后可再领",
                claimed.map(coupon -> coupon.claimedAt().format(DATETIME)).orElse(null),
                config.remaining(), unused, coupons.size(), cards);
    }

    /**
     * 领取本周优惠券。
     *
     * <p><b>幂等语义：已领取时返回同一张券，不报错。</b>连点两下、或上次领完
     * 又滑回来再点，用户期待的是「看到那张券」而不是一句红色报错 ——
     * 与生日礼遇的领取保持一致的交互口径。
     *
     * <p><b>顺序是刻意的</b>：先 INSERT 再扣库存。
     * <ul>
     *   <li>先 INSERT：唯一键在任何写入之前挡下并发，第二个请求根本不会碰库存；</li>
     *   <li>再扣库存：条件更新 {@code issued_count < total_stock} 返回 0 行时抛冲突，
     *       整个事务回滚，刚插入的券记录一并消失 —— 不会留「没扣库存却发出券」的孤儿。</li>
     * </ul>
     * 反过来做（先扣库存再 INSERT）则必须在唯一键冲突时手工回补库存，
     * 一旦回补语句本身失败就永久少一张可发量。
     */
    @Transactional
    public Coupon claim(String memberId) {
        CouponConfig config = config();
        if (!config.enabled()) {
            throw CouponException.conflict("每周好券暂未开放，下次再来看看");
        }
        if (!config.usable()) {
            throw CouponException.conflict("优惠券配置不完整，请联系门店");
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        settleExpired(memberId, now);

        Week week = CouponWeek.current();
        Optional<Coupon> existing = findByWeek(memberId, week.key());
        if (existing.isPresent()) {
            return existing.get();
        }

        int held = unusedCount(memberId, now);
        if (config.maxHeld() > 0 && held >= config.maxHeld()) {
            throw CouponException.conflict("您手上还有 " + held + " 张券未使用，先用掉一张再来领");
        }

        Coupon coupon = new Coupon(UUID.randomUUID().toString(), memberId, week.key(),
                config.title(), config.amount(), config.minSpend(),
                CouponStatus.UNUSED, now, now.plusDays(config.validDays()),
                null, null, BigDecimal.ZERO);
        try {
            insert(coupon);
        } catch (DuplicateKeyException race) {
            // 并发第二人：唯一索引已拦下，等同「本周已领取」。此时尚未扣库存，无需回补。
            return findByWeek(memberId, week.key()).orElse(coupon);
        }

        if (deductStock() == 0) {
            // 库存刚好被领完：抛冲突让事务回滚，券记录随之消失
            throw CouponException.conflict("本周好券已被领完，下周一再来看看");
        }
        return coupon;
    }

    // ---- 结算页 ----

    /**
     * 结算页选券视图：把「可用 / 不可用」分好组下发。
     *
     * <p>不可用的券也要给出来（附原因），而不是直接隐藏 ——
     * 顾客明明在券包里看到一张券，结算页却消失了，会以为券丢了。
     * 显示成灰卡 + 「还差 3.5 元」比什么都不显示有用得多。
     */
    @Transactional
    public Usable usable(String memberId, BigDecimal subtotal) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        settleExpired(memberId, now);
        CouponConfig config = config();

        List<Card> usable = new ArrayList<>();
        List<Card> unusable = new ArrayList<>();
        for (Coupon coupon : listUnused(memberId)) {
            Card card = toCard(coupon, config.subtitle(), now);
            if (coupon.meetsThreshold(subtotal)) {
                usable.add(card);
            } else {
                BigDecimal gap = coupon.minSpend().subtract(subtotal).setScale(2, RoundingMode.HALF_UP);
                unusable.add(withReason(card, "还差 ¥" + gap.stripTrailingZeros().toPlainString() + " 可用"));
            }
        }
        return new Usable(subtotal, usable, unusable, usable.size());
    }

    /**
     * 下单前校验一张券，返回可用于本次订单的券。
     *
     * <p>只做校验不核销：报价（{@code /cart/quote}）也要走这里，
     * 而报价不该消耗券。真正的核销发生在订单落库之后（见 {@link #markUsed}）。
     */
    @Transactional
    public Coupon resolveForOrder(String memberId, String couponId, BigDecimal subtotal) {
        if (couponId == null || couponId.isBlank()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        settleExpired(memberId, now);

        Coupon coupon = find(couponId)
                .filter(row -> row.memberId().equals(memberId))
                .orElseThrow(() -> CouponException.notFound("优惠券不存在或已失效"));

        if (coupon.status() == CouponStatus.USED) {
            throw CouponException.conflict("该优惠券已使用（订单 "
                    + shortId(coupon.orderId()) + "），请选择其它优惠券");
        }
        if (coupon.status() == CouponStatus.EXPIRED || !coupon.expiresAt().isAfter(now)) {
            throw CouponException.conflict("该优惠券已于 " + coupon.expiresAt().format(DATETIME) + " 过期");
        }
        if (!coupon.meetsThreshold(subtotal)) {
            throw CouponException.conflict("该券满 ¥" + money(coupon.minSpend())
                    + " 可用，当前商品金额 ¥" + money(subtotal));
        }
        return coupon;
    }

    // ---- 核销与退回 ----

    /**
     * 核销：把券挂到订单上。
     *
     * <p>条件更新是唯一的互斥点。返回 0 行说明券已被别的请求用掉或刚好过期，
     * 抛 409 让下单事务回滚 —— 顾客看到的是「券已使用」，而不是一笔扣错钱的订单。
     */
    @Transactional
    public void markUsed(String couponId, String memberId, String orderId, BigDecimal discount) {
        // 只取一次 now：两次调用可能跨秒，会让「核销时间」与「过期判定」落在不同时刻
        LocalDateTime now = LocalDateTime.now(ZONE);
        int updated = jdbc.update(
                "UPDATE member_coupons SET status='USED',used_at=?,order_id=?,discount_amount=? "
                        + "WHERE id=? AND member_id=? AND status='UNUSED' AND expires_at>?",
                now, orderId, discount, couponId, memberId, now);
        if (updated == 0) {
            throw CouponException.conflict("优惠券已被使用或已过期，请重新选择");
        }
    }

    /**
     * 订单取消时把券退回。
     *
     * <p><b>为什么必须退</b>：下单即核销的模型下，顾客下单后取消（或未支付超时）
     * 如果不退券，等于「点了一下就白丢 6 元」，且没有任何痕迹可查。
     *
     * <p><b>退回时重新判定有效期</b>：券在这笔订单上停留期间可能已经过期，
     * 直接恢复成 {@code UNUSED} 会让一张过期券复活。用 {@code CASE} 在一条语句里
     * 同时决定落哪个状态，避免「先读再写」被并发插入打乱。
     *
     * <p>幂等：条件 {@code status='USED' AND order_id=?}，重复调用第二次影响 0 行。
     *
     * @return 实际退回的券数
     */
    @Transactional
    public int releaseForOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return 0;
        }
        return jdbc.update(
                "UPDATE member_coupons "
                        + "SET status=CASE WHEN expires_at<=? THEN 'EXPIRED' ELSE 'UNUSED' END,"
                        + "used_at=NULL,order_id=NULL,discount_amount=0 "
                        + "WHERE order_id=? AND status='USED'",
                LocalDateTime.now(ZONE), orderId);
    }

    // ---- 过期结算 ----

    /** 会员维度的惰性过期结算。 */
    @Transactional
    public int settleExpired(String memberId) {
        return settleExpired(memberId, LocalDateTime.now(ZONE));
    }

    private int settleExpired(String memberId, LocalDateTime now) {
        return jdbc.update(
                "UPDATE member_coupons SET status='EXPIRED' WHERE member_id=? AND status='UNUSED' AND expires_at<=?",
                memberId, now);
    }

    /**
     * 全量过期结算：管理端查发放记录前调用。
     *
     * <p>遍历全表在本项目的数据量级下可忽略（每周一人最多一张），
     * 且带 {@code status='UNUSED'} 条件只扫未使用的一小部分。
     */
    @Transactional
    public int settleAllExpired() {
        return jdbc.update(
                "UPDATE member_coupons SET status='EXPIRED' WHERE status='UNUSED' AND expires_at<=?",
                LocalDateTime.now(ZONE));
    }

    // ---- 统计（供会员资料等处复用） ----

    /** 未使用且未过期的张数：会员卡上的「优惠券」数字以此为唯一口径。 */
    public int unusedCount(String memberId) {
        return unusedCount(memberId, LocalDateTime.now(ZONE));
    }

    private int unusedCount(String memberId, LocalDateTime now) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member_coupons WHERE member_id=? AND status='UNUSED' AND expires_at>?",
                Integer.class, memberId, now);
        return count == null ? 0 : count;
    }

    // ---- 内部 ----

    /**
     * 扣减发行量。返回影响行数：0 表示已领完（不限量时恒为 1）。
     *
     * <p>写成自增 + 条件，而不是「先读 issued_count 再写回」：后者在并发下会丢更新，
     * 表现为超发 —— 库存 100 张能发出去 120 张。
     */
    private int deductStock() {
        return jdbc.update(
                "UPDATE coupon_config SET issued_count=issued_count+1 "
                        + "WHERE id=1 AND (total_stock IS NULL OR issued_count<total_stock)");
    }

    private Optional<Coupon> findByWeek(String memberId, String weekKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_coupons WHERE member_id=? AND week_key=?",
                MAPPER, memberId, weekKey).stream().findFirst();
    }

    private Optional<Coupon> find(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_coupons WHERE id=?",
                MAPPER, id).stream().findFirst();
    }

    /**
     * 会员的券列表。
     *
     * <p>排序：未使用在前、其余按领取时间倒序。用 {@code FIELD()} 而不是三次查询 ——
     * 券包是一屏数据，多打两次库没有收益。
     *
     * <p>上限 60 行：一人一周一张，60 张≈一年多，已远超任何真实使用场景；
     * 不设上限会让「从没清理过」的账号一次把整表读回。
     */
    private List<Coupon> list(String memberId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_coupons WHERE member_id=? "
                + "ORDER BY FIELD(status,'UNUSED','USED','EXPIRED'),claimed_at DESC LIMIT 60", MAPPER, memberId);
    }

    private List<Coupon> listUnused(String memberId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_coupons "
                + "WHERE member_id=? AND status='UNUSED' ORDER BY expires_at", MAPPER, memberId);
    }

    private void insert(Coupon coupon) {
        jdbc.update("INSERT INTO member_coupons(" + COLUMNS + ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                coupon.id(), coupon.memberId(), coupon.weekKey(), coupon.title(),
                coupon.amount(), coupon.minSpend(), coupon.status().name(),
                coupon.claimedAt(), coupon.expiresAt(), coupon.usedAt(), coupon.orderId(), coupon.discountAmount());
    }

    private Card toCard(Coupon coupon, String subtitle, LocalDateTime now) {
        String reason = switch (coupon.status()) {
            case USED -> "已于 " + (coupon.usedAt() == null ? "—" : coupon.usedAt().format(DATETIME)) + " 使用";
            case EXPIRED -> "已于 " + coupon.expiresAt().format(DATETIME) + " 过期";
            case UNUSED -> "";
        };
        long daysLeft = coupon.status().isUnused()
                ? Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), coupon.expiresAt().toLocalDate()))
                : 0;
        return new Card(coupon.id(), coupon.title(), subtitle,
                coupon.amount(), coupon.minSpend(),
                coupon.status().name(), coupon.status().label(), reason,
                coupon.claimedAt().format(DATETIME), coupon.expiresAt().format(DATETIME),
                coupon.usedAt() == null ? null : coupon.usedAt().format(DATETIME),
                coupon.orderId(), coupon.discountAmount(),
                (int) daysLeft, coupon.status().isUnused() && daysLeft <= 1);
    }

    private Card withReason(Card card, String reason) {
        return new Card(card.id(), card.title(), card.subtitle(), card.amount(), card.minSpend(),
                card.status(), card.statusLabel(), reason, card.claimedAt(), card.expiresAt(),
                card.usedAt(), card.orderId(), card.discountAmount(), card.daysLeft(), card.expiringSoon());
    }

    private static String money(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String shortId(String id) {
        return id == null ? "—" : id.substring(0, Math.min(8, id.length()));
    }

    private static final RowMapper<Coupon> MAPPER = (rs, index) -> new Coupon(
            rs.getString("id"), rs.getString("member_id"), rs.getString("week_key"), rs.getString("title"),
            rs.getBigDecimal("amount"), rs.getBigDecimal("min_spend"),
            CouponStatus.valueOf(rs.getString("status")),
            toLocal(rs.getTimestamp("claimed_at")), toLocal(rs.getTimestamp("expires_at")),
            toLocalOrNull(rs.getTimestamp("used_at")), rs.getString("order_id"),
            rs.getBigDecimal("discount_amount"));

    private static LocalDateTime toLocal(Timestamp value) {
        return value.toLocalDateTime();
    }

    private static LocalDateTime toLocalOrNull(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    // ---- 响应结构 ----

    /** 本周领取状态。前端据此决定领取按钮是亮、灰还是隐藏。 */
    public enum WeekState {
        /** 已领取（本周那张券就是返回值里的 claimedAt） */
        CLAIMED,
        /** 可领取 */
        CLAIMABLE,
        /** 持有已达上限，需先用掉 */
        HOLD_LIMIT,
        /** 发行量已领完 */
        SOLD_OUT,
        /** 功能未开放 */
        DISABLED
    }

    /**
     * 券包首屏。
     *
     * @param resetAt    本周重置时刻（下周一 00:00），服务端算好下发
     * @param resetHint  重置时间的中文文案，前端直接显示，不再自行格式化日期
     * @param remaining  剩余库存，null 表示不限量
     * @param unusedCount 未使用张数（会员卡数字口径）
     */
    public record Wallet(
            boolean enabled,
            String title,
            String subtitle,
            String description,
            BigDecimal amount,
            BigDecimal minSpend,
            int validDays,
            WeekState weekState,
            String weekStateLabel,
            String weekKey,
            String resetAt,
            String resetHint,
            String claimedAt,
            Integer remaining,
            int unusedCount,
            int totalCount,
            List<Card> coupons) {
    }

    /**
     * 券卡（券包与结算页共用）。
     *
     * @param reason       不可用 / 不可选的原因；可选时为空串
     * @param daysLeft     剩余天数（仅未使用券有意义）
     * @param expiringSoon 是否即将过期（≤1 天），前端据此把日期标红
     */
    public record Card(
            String id,
            String title,
            String subtitle,
            BigDecimal amount,
            BigDecimal minSpend,
            String status,
            String statusLabel,
            String reason,
            String claimedAt,
            String expiresAt,
            String usedAt,
            String orderId,
            BigDecimal discountAmount,
            int daysLeft,
            boolean expiringSoon) {
    }

    /** 结算页选券视图。 */
    public record Usable(BigDecimal subtotal, List<Card> usable, List<Card> unusable, int usableCount) {
    }
}
