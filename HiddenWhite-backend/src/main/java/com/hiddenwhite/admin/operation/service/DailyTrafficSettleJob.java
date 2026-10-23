package com.hiddenwhite.admin.operation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/**
 * 日客流结算任务。
 *
 * <p><b>修复的真实缺陷</b>：{@code store_daily_traffic} 的「打烊结算」此前只挂在
 * 人工点击「打烊」的动作上（{@code OperationService.switchStatus}）。这意味着：
 * <ul>
 *   <li>店员忘记点打烊，当天数据永远不结算；</li>
 *   <li>{@code adjustTraffic} 要求「已结算」才允许人工修正，因此未结算的日子
 *       连手工补救的入口都没有——运营会看到一个空白报表且无法修正。</li>
 * </ul>
 *
 * <p>本任务在每日凌晨补结算「昨天及更早未结算的日期」，让报表不再依赖人的记忆。
 * {@code settle} 内部对同一日期重复调用是幂等的（写入固定统计值），
 * 因此与手动打烊结算并存不会冲突。
 */
@Component
public class DailyTrafficSettleJob {

    private static final Logger log = LoggerFactory.getLogger(DailyTrafficSettleJob.class);

    /** 一次补结算回溯的天数上限。门店若长期未结算，回溯过多会造成不必要的全表聚合。 */
    private static final int BACKFILL_DAYS = 7;

    private final OperationService operationService;

    public DailyTrafficSettleJob(OperationService operationService) {
        this.operationService = operationService;
    }

    /**
     * 每日 00:10 补结算。
     *
     * <p>选 00:10 而非 00:00，避开整点的定时器高峰，同时保证昨日全天数据已完整。
     */
    @Scheduled(cron = "0 10 0 * * *")
    public void settleBacklog() {
        LocalDate today = LocalDate.now(ZONE);
        int settled = 0;
        // 从昨天往前回溯：昨天通常因忘记打烊而未结算，更早的日期属于异常积压。
        // 已结算的日期直接跳过 —— settle 虽然幂等，但每晚把 7 天全量重算一遍
        // 会持续改写已冻结的历史数据，也让「本次补结算了几天」的日志失去意义。
        for (int back = 1; back <= BACKFILL_DAYS; back++) {
            LocalDate target = today.minusDays(back);
            try {
                if (operationService.isSettled(target)) {
                    continue;
                }
                operationService.settle(target);
                settled++;
            } catch (RuntimeException e) {
                // 单日失败不阻断其余日期；下一天会再次尝试
                log.warn("补结算 {} 的日客流失败", target, e);
            }
        }
        if (settled > 0) {
            log.info("日客流补结算完成：共处理 {} 天（回溯上限 {} 天）", settled, BACKFILL_DAYS);
        }
    }
}