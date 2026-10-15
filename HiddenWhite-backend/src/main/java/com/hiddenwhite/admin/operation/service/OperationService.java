package com.hiddenwhite.admin.operation.service;

import com.hiddenwhite.admin.operation.domain.dto.DayHoursItem;
import com.hiddenwhite.admin.operation.domain.dto.HoursWriteRequest;
import com.hiddenwhite.admin.operation.domain.dto.StatusWriteRequest;
import com.hiddenwhite.admin.operation.domain.dto.TrafficAdjustRequest;
import com.hiddenwhite.admin.operation.mapper.OperationMapper;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 店铺运营服务：营业状态（人工打烊优先）、每日运营时段、日流量统计。
 * 营业状态是随时计算的派生值：人工打烊 &gt; 当日休息 &gt; 未到营业时间 &gt; 已过打烊时间 &gt; 营业中。
 */
@Service
public class OperationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int[] WEEK_DAYS = {1, 2, 3, 4, 5, 6, 7};

    private final OperationMapper mapper;

    public OperationService(OperationMapper mapper) {
        this.mapper = mapper;
    }

    // ---- 营业状态 ----

    /** 当前状态总览：人工开关、今日/明日时段、派生状态、今日实时流量。 */
    public Map<String, Object> status() {
        Map<String, Object> store = requireStore();
        boolean manualClosed = truthy(store.get("manualClosed"));
        String note = text(store.get("statusNote"));
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Object> todayHours = hoursOfDay(today);
        Map<String, Object> tomorrowHours = hoursOfDay(today.plusDays(1));

        String openTime = text(todayHours.get("openTime"));
        String closeTime = text(todayHours.get("closeTime"));
        boolean dayOff = truthy(todayHours.get("closed"));
        LocalTime now = LocalTime.now(ZONE);

        String status;
        String reason;
        String message;
        if (manualClosed) {
            status = "CLOSED";
            reason = "MANUAL";
            message = note.isBlank() ? "店铺已打烊，期待与您再会" : note;
        } else if (dayOff) {
            status = "CLOSED";
            reason = "DAY_OFF";
            message = "今日例休，明日 " + text(tomorrowHours.get("openTime")) + " 恢复营业";
        } else if (now.isBefore(LocalTime.parse(openTime))) {
            status = "CLOSED";
            reason = "NOT_YET_OPEN";
            message = "未到营业时间，营业时间 " + openTime + "–" + closeTime;
        } else if (!now.isBefore(LocalTime.parse(closeTime))) {
            status = "CLOSED";
            reason = "AFTER_HOURS";
            message = "今日已打烊，明日 " + text(tomorrowHours.get("openTime")) + " 恢复营业";
        } else {
            status = "OPEN";
            reason = null;
            message = null;
        }

        String todayKey = DAY.format(today);
        Map<String, Object> summary = mapper.dailySummary(todayKey);
        Map<String, Object> trafficRow = mapper.findTraffic(todayKey);
        return Map.of(
                "manualClosed", manualClosed,
                "statusNote", note,
                "current", Map.of("status", status, "reason", reason == null ? "" : reason,
                        "note", message == null ? "" : message),
                "today", todayHours,
                "tomorrow", tomorrowHours,
                "traffic", Map.of(
                        "date", todayKey,
                        "orderCount", longValue(summary.get("orderCount")),
                        "revenue", summary.get("revenue"),
                        "inProgressCount", mapper.countInProgress(todayKey),
                        "settled", trafficRow != null && truthy(trafficRow.get("settled")))
        );
    }

    /**
     * 切换营业 / 打烊（幂等）：打烊即时结算当日流量；恢复营业累计人工打烊时长。
     * 状态不变时仅更新提示语，不重复结算。
     */
    @Transactional
    public Map<String, Object> switchStatus(StatusWriteRequest request) {
        if (request.getManualClosed() == null) throw new BusinessException("请指定营业状态");
        boolean closed = request.getManualClosed();
        String note = request.getStatusNote() == null ? "" : request.getStatusNote().trim();
        if (note.length() > 100) throw new BusinessException("打烊提示语不能超过 100 字");

        Object closedAtValue = requireStore().get("closedAt");
        int changed = mapper.switchStatus(closed, note);
        if (changed > 0) {
            if (closed) {
                settle(LocalDate.now(ZONE));
            } else {
                accumulateClosedMinutes(closedAtValue);
            }
        } else {
            mapper.updateStatusNote(note);
        }
        return status();
    }

    // ---- 运营时段 ----

    /** 默认时段 + 7 天按星期配置（表为空时按默认值兜底初始化）。 */
    @Transactional
    public Map<String, Object> hours() {
        if (mapper.countHours() == 0) initHoursFromDefault();
        Map<String, Object> defaults = mapper.defaultHours();
        return Map.of(
                "defaultOpen", text(defaults.get("openingTime")),
                "defaultClose", text(defaults.get("closingTime")),
                "days", mapper.listHours()
        );
    }

    /** 整批保存：默认时段 + 7 天周配置，事务包裹。 */
    @Transactional
    public void updateHours(HoursWriteRequest request) {
        String defaultOpen = requireTime(request.getDefaultOpen(), "默认开门时间");
        String defaultClose = requireTime(request.getDefaultClose(), "默认打烊时间");
        requireOpenBeforeClose(defaultOpen, defaultClose, "默认时段");

        List<DayHoursItem> days = request.getDays();
        if (days == null || days.size() != 7) throw new BusinessException("请完整配置周一至周日共 7 天的时段");
        boolean[] seen = new boolean[8];
        for (DayHoursItem item : days) {
            Integer day = item.getDayOfWeek();
            if (day == null || day < 1 || day > 7 || seen[day]) throw new BusinessException("星期配置必须是周一至周日且不重复");
            seen[day] = true;
            String openTime = requireTime(item.getOpenTime(), "星期" + day + "开门时间");
            String closeTime = requireTime(item.getCloseTime(), "星期" + day + "打烊时间");
            if (!truthy(item.getClosed())) requireOpenBeforeClose(openTime, closeTime, "星期" + day + "时段");
        }
        mapper.updateDefaultHours(defaultOpen, defaultClose);
        for (DayHoursItem item : days) {
            mapper.upsertHour(item.getDayOfWeek(), item.getOpenTime(), item.getCloseTime(), truthy(item.getClosed()));
        }
    }

    // ---- 日流量 ----

    /** 今日实时卡片：营业中只读展示，不做任何修改入口。 */
    public Map<String, Object> todayTraffic() {
        String today = DAY.format(LocalDate.now(ZONE));
        Map<String, Object> summary = mapper.dailySummary(today);
        Map<String, Object> row = mapper.findTraffic(today);
        return Map.of(
                "date", today,
                "orderCount", longValue(summary.get("orderCount")),
                "revenue", summary.get("revenue"),
                "visitorEstimate", longValue(summary.get("visitorEstimate")),
                "inProgressCount", mapper.countInProgress(today),
                "settled", row != null && truthy(row.get("settled"))
        );
    }

    /** 历史日流量分页（默认全部，按日期倒序）。 */
    public PageResult<Map<String, Object>> traffic(PageQuery query, String startDate, String endDate) {
        String from = normalizeDate(startDate, "开始日期");
        String to = normalizeDate(endDate, "结束日期");
        if (from != null && to != null && from.compareTo(to) > 0) throw new BusinessException("开始日期不能晚于结束日期");
        long total = mapper.countTraffic(from, to);
        long offset = (long) (query.normalizedPageNum() - 1) * query.normalizedPageSize();
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.pageTraffic(from, to, offset, query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /** 调整已结算日期的流量：数值非负、原因必填、原始值只快照一次。 */
    public void adjustTraffic(String date, TrafficAdjustRequest request) {
        String statDate = normalizeDate(date, "统计日期");
        if (statDate == null) throw new BusinessException("请指定统计日期");
        if (statDate.compareTo(DAY.format(LocalDate.now(ZONE))) > 0) throw new BusinessException("不能调整未来日期的数据");

        Map<String, Object> row = mapper.findTraffic(statDate);
        if (row == null || !truthy(row.get("settled"))) {
            throw new BusinessException(409, "该日期数据尚未结算（打烊后自动结算），暂不能调整");
        }
        if (request.getOrderCount() == null || request.getOrderCount() < 0) throw new BusinessException("订单数不能为空且不能为负数");
        if (request.getRevenue() == null || request.getRevenue().signum() < 0) throw new BusinessException("营业额不能为空且不能为负数");
        if (request.getVisitorCount() == null || request.getVisitorCount() < 0) throw new BusinessException("客流数不能为空且不能为负数");
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty()) throw new BusinessException("请填写调整原因");
        if (reason.length() > 200) throw new BusinessException("调整原因不能超过 200 字");

        int rows = mapper.adjustTraffic(statDate, request.getOrderCount(), request.getRevenue(), request.getVisitorCount(), reason);
        if (rows == 0) throw new BusinessException(409, "该日期数据尚未结算，暂不能调整");
    }

    /** 结算冻结某日流量（打烊时调用；次日惰性补结算亦可复用）。 */
    @Transactional
    public void settle(LocalDate date) {
        String statDate = DAY.format(date);
        Map<String, Object> summary = mapper.dailySummary(statDate);
        mapper.settleTraffic(statDate,
                (int) longValue(summary.get("orderCount")),
                (BigDecimal) summary.get("revenue"),
                (int) longValue(summary.get("visitorEstimate")));
    }

    // ---- 私有辅助 ----

    private Map<String, Object> requireStore() {
        Map<String, Object> store = mapper.storeStatusRow();
        if (store == null) throw new BusinessException(404, "门店信息不存在");
        return store;
    }

    /** 某一天的生效时段：store_hours 当日行优先，缺失时回退 stores 默认时段。 */
    private Map<String, Object> hoursOfDay(LocalDate date) {
        if (mapper.countHours() == 0) initHoursFromDefault();
        int day = date.getDayOfWeek().getValue();
        Map<String, Object> row = mapper.listHours().stream()
                .filter(item -> ((Number) item.get("dayOfWeek")).intValue() == day)
                .findFirst().orElse(null);
        if (row != null) return Map.of(
                "dayOfWeek", day,
                "openTime", text(row.get("openTime")),
                "closeTime", text(row.get("closeTime")),
                "closed", truthy(row.get("closed")));
        Map<String, Object> defaults = mapper.defaultHours();
        return Map.of(
                "dayOfWeek", day,
                "openTime", text(defaults.get("openingTime")),
                "closeTime", text(defaults.get("closingTime")),
                "closed", false);
    }

    private void initHoursFromDefault() {
        Map<String, Object> defaults = mapper.defaultHours();
        for (int day : WEEK_DAYS) {
            mapper.upsertHour(day, text(defaults.get("openingTime")), text(defaults.get("closingTime")), false);
        }
    }

    /** 恢复营业时按 closed_at → NOW() 累计当日打烊时长（跨日打烊从当日 0 点起算）。 */
    private void accumulateClosedMinutes(Object closedAtValue) {
        LocalDateTime closedAt = toDateTime(closedAtValue);
        if (closedAt == null) return;
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime start = closedAt.toLocalDate().equals(now.toLocalDate())
                ? closedAt : now.toLocalDate().atStartOfDay();
        long minutes = Math.max(0, Duration.between(start, now).toMinutes());
        if (minutes > 0) {
            String today = DAY.format(now.toLocalDate());
            mapper.ensureTrafficRow(today);
            mapper.addClosedMinutes(today, minutes);
        }
    }

    private LocalDateTime toDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

    private String requireTime(String value, String field) {
        if (value == null || !TIME_PATTERN.matcher(value.trim()).matches()) {
            throw new BusinessException(field + "格式为 HH:mm，如 08:00");
        }
        return value.trim();
    }

    private void requireOpenBeforeClose(String openTime, String closeTime, String field) {
        if (!LocalTime.parse(openTime).isBefore(LocalTime.parse(closeTime))) {
            throw new BusinessException(field + "的打烊时间必须晚于开门时间（不支持跨夜）");
        }
    }

    private String normalizeDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (!DATE_PATTERN.matcher(trimmed).matches()) throw new BusinessException(field + "格式为 yyyy-MM-dd");
        try {
            return LocalDate.parse(trimmed).format(DAY);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(field + "不是有效日期: " + trimmed);
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return false;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
