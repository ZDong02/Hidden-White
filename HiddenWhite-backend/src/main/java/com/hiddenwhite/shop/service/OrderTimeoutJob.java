package com.hiddenwhite.shop.service;

import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderCancelReason;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 订单超时取消任务。
 *
 * <p><b>修复的真实故障</b>：改造前项目没有任何定时任务，顾客下单后不取餐，
 * 订单会永远停在进行中。而「同一会员仅一张进行中订单」的校验会因此长期
 * 阻塞该会员再次下单——顾客下次想点单会被告知「有订单待处理」，
 * 却无法自行解决，只能联系店员手工处理。
 *
 * <p>现在超时订单自动置为 {@code CANCELLED}，释放下单名额。
 *
 * <p><b>取消原因必须区分</b>：
 * <ul>
 *   <li>未支付超时 → {@code TIMEOUT_UNPAID}：钱没到账，直接取消无损失；</li>
 *   <li>已支付未取餐 → {@code TIMEOUT_UNPICKED}：<b>钱已经收了</b>，属异常场景。
 *       此时保留 paidAt 与微信交易号（{@code ShopRepository.cancel} 只改状态与取消列，
 *       天然满足），供店员对账与退款。混用取消原因会导致运营误以为钱没到账而漏退。</li>
 * </ul>
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    /** 取消原因常量统一由 {@link com.hiddenwhite.shop.domain.OrderCancelReason} 提供，此处仅保留兼容别名。 */
    public static final String REASON_UNPAID = OrderCancelReason.TIMEOUT_UNPAID;

    public static final String REASON_UNPICKED = OrderCancelReason.TIMEOUT_UNPICKED;

    private final ShopRepository repository;

    public OrderTimeoutJob(ShopRepository repository) {
        this.repository = repository;
    }

    /**
     * 每分钟扫描一次。
     *
     * <p>有效期为「制作时长 + 30 分钟缓冲」，通常 35 分钟左右，分钟级精度足够；
     * 扫描只走 {@code (status, created_at)} 索引，单店订单量级下开销可忽略。
     */
    @Scheduled(cron = "0 * * * * *")
    public void cancelOverdueOrders() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<Order> expired;
        try {
            expired = repository.findExpired(now);
        } catch (RuntimeException e) {
            // 单次扫描失败不应中断调度：下一分钟的触发会重试
            log.warn("扫描超时订单失败，将在下一分钟重试", e);
            return;
        }
        if (expired.isEmpty()) {
            return;
        }
        cancelAll(expired, now.atZone(ZONE).toOffsetDateTime());
    }

    /**
     * 取消一批过期订单。抽出以便测试直接调用，不必等待定时触发。
     *
     * @return 成功取消的数量
     */
    public int cancelAll(List<Order> expired, OffsetDateTime cancelledAt) {
        int unpaid = 0;
        int picked = 0;
        for (Order order : expired) {
            boolean wasPaid = order.status() == OrderStatus.PAID;
            if (cancelOne(order.id(), wasPaid, cancelledAt)) {
                if (wasPaid) {
                    picked++;
                } else {
                    unpaid++;
                }
            }
        }
        if (picked > 0) {
            // 已支付却超时未取餐是需要人工介入的异常，必须显式告警而非只记普通日志
            log.warn("有 {} 笔已支付订单超时未取餐被自动取消，请核对门店情况并安排退款", picked);
        }
        if (unpaid > 0 || picked > 0) {
            log.info("超时订单清理完成：未支付 {} 笔，已支付 {} 笔", unpaid, picked);
        }
        return unpaid + picked;
    }

    /**
     * 取消单条。订单可能已被并发操作改为终态，此时跳过而非抛错。
     *
     * @return 本次是否真的取消了
     */
    private boolean cancelOne(UUID orderId, boolean wasPaid, OffsetDateTime cancelledAt) {
        try {
            repository.cancel(orderId, wasPaid ? REASON_UNPICKED : REASON_UNPAID, cancelledAt);
            return true;
        } catch (IllegalStateException e) {
            // 已被店员或另一轮任务处理，属正常并发，不记 error
            log.debug("订单 {} 已是终态，跳过超时取消", orderId);
            return false;
        } catch (RuntimeException e) {
            // 单条失败不影响其余订单
            log.warn("取消超时订单失败 id={}", orderId, e);
            return false;
        }
    }
}