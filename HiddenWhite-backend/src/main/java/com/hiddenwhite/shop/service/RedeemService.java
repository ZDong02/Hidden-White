package com.hiddenwhite.shop.service;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.points.PointsBizType;
import com.hiddenwhite.common.points.PointsLedger;
import com.hiddenwhite.common.points.PointsRefType;
import com.hiddenwhite.shop.domain.PointsLogEntry;
import com.hiddenwhite.shop.domain.RedeemProduct;
import com.hiddenwhite.shop.domain.Redemption;
import com.hiddenwhite.shop.domain.RedemptionStatus;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.exception.NotFoundException;
import com.hiddenwhite.shop.repository.RedeemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 积分兑换服务（点单端）。
 *
 * <p><b>积分闭环</b>：兑换扣分（{@code REDEEM}）、自助取消（{@code REDEEM_CANCEL}）、
 * 逾期退回（{@code REDEEM_EXPIRE_REFUND}）三笔流水成对出现，任何一张兑换单都能
 * 从流水还原「扣了多少、退了多少」。所有余额变动都经 {@link PointsLedger}，
 * 本类不自己拼 {@code UPDATE members}。
 *
 * <p><b>兑换事务的顺序是有讲究的</b>：先扣分（锁住 members 行）→ 再校验限兑 →
 * 再递减库存 → 最后建单。先扣分不只是为了「余额不够就不必往下走」，
 * 更关键的是让同一会员的并发兑换在这一行上串行化 ——
 * 否则两次并发请求都会读到「尚未超限」的快照，双双越过 limit 校验。
 * 任一步失败整个事务回滚，积分、库存与流水一起复原，不会留下孤儿记录。
 */
@Service
public class RedeemService {

    /** 核销有效期：兑换后 30 天内到店出示核销码。 */
    public static final int VALID_DAYS = 30;

    /** 取号锁：同一 JVM 内串行分配兑换单号，唯一索引仍是最终兜底。 */
    private static final Object NUMBER_LOCK = new Object();

    /** 8 位核销码的空间（00000000–99999999）。 */
    private static final int CODE_BOUND = 100_000_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedeemRepository repository;
    private final PointsLedger pointsLedger;

    public RedeemService(RedeemRepository repository, PointsLedger pointsLedger) {
        this.repository = repository;
        this.pointsLedger = pointsLedger;
    }

    // ---- 目录 ----

    /**
     * 可兑列表：上架兑换品 + 会员当前积分 + 每一项能否兑换的原因。
     *
     * <p>「能否兑换」由服务端判定而不是交给前端算：积分、库存、限兑三件事里
     * 有两件随时会变（别人也在兑），前端算出来的结论只可能是过期快照。
     */
    public Catalog catalog(String memberId) {
        int points = pointsLedger.balance(memberId);
        Map<String, Integer> used = repository.usedCounts(memberId);
        List<Card> cards = repository.enabledProducts().stream()
                .map(product -> toCard(product, points, used.getOrDefault(product.id(), 0)))
                .toList();
        return new Catalog(points, cards);
    }

    private Card toCard(RedeemProduct product, int points, int used) {
        String reason = "";
        if (product.soldOut()) {
            reason = "已兑完";
        } else if (product.perMemberLimit() > 0 && used >= product.perMemberLimit()) {
            reason = "已达限兑次数（" + product.perMemberLimit() + " 次）";
        } else if (points < product.pointsCost()) {
            reason = "还差 " + (product.pointsCost() - points) + " 积分";
        }
        return new Card(product.id(), product.name(), product.subtitle(), product.description(), product.image(),
                product.pointsCost(), product.stock(), product.perMemberLimit(), used, reason.isEmpty(), reason);
    }

    // ---- 兑换 ----

    /**
     * 兑换一件兑换品。
     *
     * @return 新建的兑换单（含核销码）
     */
    @Transactional
    public Redemption exchange(String memberId, String productId) {
        RedeemProduct product = repository.product(productId);
        if (product == null || !product.enabled()) {
            throw new NotFoundException("兑换品不存在或已下架");
        }
        if (product.soldOut()) {
            throw new BusinessException(HttpStatus.CONFLICT, "该兑换品已兑完");
        }
        int balance = pointsLedger.balance(memberId);
        if (balance < product.pointsCost()) {
            // 预检只为给出「还差多少」的准确文案；真正的防超扣是账本的条件更新
            throw new BusinessException(HttpStatus.CONFLICT,
                    "积分不足，兑换需要 " + product.pointsCost() + " 积分，当前 " + balance + " 积分");
        }

        OffsetDateTime now = OffsetDateTime.now(ZONE);
        String id = UUID.randomUUID().toString();
        // 先取号再扣分：流水要挂 ref_id，兑换单 id 必须先存在
        String redeemNo = allocateRedeemNo(now.toLocalDate());
        String code = allocateCode();

        pointsLedger.adjust(memberId, -product.pointsCost(), PointsBizType.REDEEM,
                PointsRefType.REDEMPTION, id, "兑换「" + product.name() + "」");

        if (product.perMemberLimit() > 0 && repository.countUsed(memberId, productId) >= product.perMemberLimit()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "该兑换品每位会员限兑 " + product.perMemberLimit() + " 次");
        }
        if (repository.deductStock(productId) == 0) {
            // 不限量商品的 UPDATE 也能命中；返回 0 只可能是「库存扣到 0 了」
            throw new BusinessException(HttpStatus.CONFLICT, "该兑换品已兑完");
        }

        Redemption redemption = new Redemption(id, redeemNo, memberId, product.id(), product.name(),
                product.image(), product.pointsCost(), code, RedemptionStatus.PENDING,
                now.plusDays(VALID_DAYS), null, null, null, now);
        repository.insert(redemption);
        return redemption;
    }

    // ---- 兑换单 ----

    /** 我的兑换单（分页）。查询前先做一次惰性过期结算，避免把已过期单显示成「待核销」。 */
    @Transactional
    public PageResult<Redemption> orders(String memberId, PageQuery query) {
        settleExpired();
        int pageNum = query.normalizedPageNum();
        int pageSize = query.normalizedPageSize();
        long total = repository.countByMember(memberId);
        long offset = (long) (pageNum - 1) * pageSize;
        List<Redemption> rows = total == 0 ? List.of() : repository.pageByMember(memberId, offset, pageSize);
        return PageResult.of(rows, total, pageNum, pageSize);
    }

    /** 单张兑换单（仅本人可见，用于兑换成功后回看核销码）。 */
    public Redemption order(String memberId, String id) {
        Redemption redemption = repository.redemption(id);
        if (redemption == null || !redemption.memberId().equals(memberId)) {
            throw new NotFoundException("兑换单不存在: " + id);
        }
        return redemption;
    }

    /**
     * 会员自助取消（仅待核销）。
     *
     * <p>取消与退分在同一事务：条件更新命中才退分，
     * 重复点击或与过期结算撞车时第二次不会命中，积分不会被退两遍。
     */
    @Transactional
    public Redemption cancel(String memberId, String id) {
        settleExpired();
        Redemption redemption = repository.redemption(id);
        if (redemption == null || !redemption.memberId().equals(memberId)) {
            throw new NotFoundException("兑换单不存在: " + id);
        }
        if (repository.cancel(id, memberId, OffsetDateTime.now(ZONE)) == 0) {
            Redemption latest = repository.redemption(id);
            throw new BusinessException(HttpStatus.CONFLICT,
                    "该兑换单" + statusLabel(latest.status()) + "，无法取消");
        }
        pointsLedger.adjust(memberId, redemption.pointsCost(), PointsBizType.REDEEM_CANCEL,
                PointsRefType.REDEMPTION, id, "取消兑换「" + redemption.productName() + "」退回积分");
        return repository.redemption(id);
    }

    /**
     * 惰性过期结算：把超过 {@code expires_at} 的待核销单翻转为已过期并退分。
     *
     * <p><b>为什么不定时任务</b>：项目里 {@code @Scheduled} 只在单实例下可靠，
     * 而「过期退分」晚几分钟没有任何业务影响。把结算挂在「会员查我的兑换单」
     * 「运营查兑换记录」「核销前」这三个真正会读到状态的入口上，
     * 零基础设施成本，且读到的一定是结算后的数据。
     *
     * <p><b>幂等</b>：{@code markExpired} 用 {@code WHERE status='PENDING'} 条件翻转，
     * 只有翻转成功的那一次才退分；并发触发时后来的调用影响行数为 0，直接跳过。
     *
     * @return 本次结算的兑换单数量
     */
    @Transactional
    public int settleExpired() {
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        int settled = 0;
        for (Redemption redemption : repository.expiredPending(now)) {
            if (repository.markExpired(redemption.id()) == 0) {
                continue; // 已被并发结算 / 被会员取消，跳过
            }
            pointsLedger.adjust(redemption.memberId(), redemption.pointsCost(),
                    PointsBizType.REDEEM_EXPIRE_REFUND, PointsRefType.REDEMPTION, redemption.id(),
                    "兑换「" + redemption.productName() + "」逾期未核销，积分已退回");
            settled++;
        }
        return settled;
    }

    // ---- 积分明细 ----

    public PageResult<PointsLogEntry> pointsLog(String memberId, PageQuery query) {
        int pageNum = query.normalizedPageNum();
        int pageSize = query.normalizedPageSize();
        long total = repository.countLogs(memberId);
        long offset = (long) (pageNum - 1) * pageSize;
        List<PointsLogEntry> rows = total == 0 ? List.of() : repository.pageLogs(memberId, offset, pageSize);
        return PageResult.of(rows, total, pageNum, pageSize);
    }

    // ---- 取号 ----

    /** 兑换单号：RW + yyyyMMdd + 4 位日序号。同 JVM 内串行，冲突再重取。 */
    private String allocateRedeemNo(LocalDate date) {
        synchronized (NUMBER_LOCK) {
            for (int attempt = 0; attempt < 3; attempt++) {
                String candidate = repository.nextRedeemNo(date);
                if (!repository.redeemNoExists(candidate)) {
                    return candidate;
                }
            }
        }
        throw new BusinessException(HttpStatus.CONFLICT, "兑换单号生成冲突，请重试");
    }

    /** 8 位核销码：全局唯一索引兜底，抽到重复就重抽。 */
    private String allocateCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = String.format("%08d", RANDOM.nextInt(CODE_BOUND));
            if (!repository.codeExists(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(HttpStatus.CONFLICT, "核销码生成冲突，请重试");
    }

    private static String statusLabel(RedemptionStatus status) {
        return switch (status) {
            case PENDING -> "待核销";
            case REDEEMED -> "已核销";
            case CANCELLED -> "已取消";
            case EXPIRED -> "已过期";
        };
    }

    // ---- 响应结构 ----

    /**
     * 兑换页首屏数据。
     *
     * @param points   会员当前积分（兑换后前端以此为准回写展示）
     * @param products 可兑列表
     */
    public record Catalog(int points, List<Card> products) {
    }

    /**
     * 兑换品卡片。
     *
     * @param stock         库存，null 表示不限量
     * @param perMemberLimit 每人限兑次数，0 表示不限
     * @param usedCount     该会员已占用名额次数
     * @param exchangeable  当前是否可兑换（服务端判定）
     * @param reason        不可兑换原因；可兑换时为空串
     */
    public record Card(String id, String name, String subtitle, String description, String image,
                       int pointsCost, Integer stock, int perMemberLimit, int usedCount,
                       boolean exchangeable, String reason) {
    }
}
