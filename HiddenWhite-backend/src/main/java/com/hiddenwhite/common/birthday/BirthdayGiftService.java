package com.hiddenwhite.common.birthday;

import com.hiddenwhite.common.birthday.BirthdayWindow.Window;
import com.hiddenwhite.common.domain.Constants;
import com.hiddenwhite.common.points.PointsBizType;
import com.hiddenwhite.common.points.PointsLedger;
import com.hiddenwhite.common.points.PointsRefType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 生日礼遇：配置读取、状态计算、领取、生日维护。
 *
 * <p><b>不引入定时任务</b>：礼遇不做「到期自动发放」，只在会员打开会员页时惰性计算状态，
 * 领取是用户主动动作。这与 {@code RedeemService.settleExpired()} 的既有取舍一致 ——
 * 一个日活几十人的点单端不值得为一次「每年一次的发放」引入调度基础设施，
 * 而自动发放还有个致命问题：用户看不见的权益等于没有。
 *
 * <p><b>幂等靠唯一键，不靠先查后写</b>：{@code member_birthday_gifts} 的
 * {@code (member_id, gift_year)} 唯一索引就是锁。先查后写在并发下会双发，
 * 而捕获 {@link DuplicateKeyException} 后原样返回「已领取」对用户来说是无感的。
 *
 * <p><b>归属年份取生日所在年，不取领取当天所在年</b>：生日 1 月 2 日的会员可能在
 * 前一年 12 月 30 日就进入窗口（AROUND 模式），若按领取当天记 2026，
 * 次年 1 月又能再领一次 —— 一年两次。
 */
@Service
public class BirthdayGiftService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final PointsLedger ledger;

    public BirthdayGiftService(JdbcTemplate jdbc, PointsLedger ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    /** 读取配置；表为空时退回默认值（宁可按默认发，也不要整块 500）。 */
    public BirthdayGiftConfig config() {
        List<BirthdayGiftConfig> rows = jdbc.query(
                "SELECT enabled, gift_type, points_amount, redeem_product_id, window_mode, window_days,"
                        + " title, subtitle, description FROM birthday_gift_config WHERE id = 1",
                (rs, index) -> new BirthdayGiftConfig(
                        rs.getBoolean("enabled"),
                        rs.getString("gift_type"),
                        rs.getInt("points_amount"),
                        rs.getString("redeem_product_id"),
                        rs.getString("window_mode"),
                        rs.getInt("window_days"),
                        rs.getString("title"),
                        rs.getString("subtitle"),
                        rs.getString("description")));
        return rows.isEmpty() ? BirthdayGiftConfig.DEFAULT : rows.get(0);
    }

    /** 会员页卡片状态。 */
    public BirthdayStatus status(String memberId) {
        BirthdayGiftConfig config = config();
        MemberBirthday birthday = birthday(memberId);
        LocalDate today = LocalDate.now(Constants.ZONE);
        LocalDateTime now = LocalDateTime.now(Constants.ZONE);

        boolean editable = birthday.updatedAt() == null || !birthday.updatedAt().plusYears(1).isAfter(now);
        String nextEditable = editable ? null : birthday.updatedAt().plusYears(1).toLocalDate().format(DATE);

        if (birthday.month() == null || birthday.day() == null) {
            return new BirthdayStatus(config.enabled(), BirthdayStatus.NO_BIRTHDAY, null, null,
                    config.title(), config.subtitle(), config.description(), config.giftType(),
                    config.pointsAmount(), null, null, null, null, editable, nextEditable);
        }

        Window window = BirthdayWindow.of(birthday.month(), birthday.day(), today,
                config.windowMode(), config.windowDays());
        ClaimRow claim = claim(memberId, window.target().getYear());
        String state;
        if (!config.enabled()) {
            state = BirthdayStatus.DISABLED;
        } else if (claim != null) {
            state = BirthdayStatus.CLAIMED;
        } else if (window.claimable()) {
            state = BirthdayStatus.CLAIMABLE;
        } else {
            state = BirthdayStatus.NOT_IN_WINDOW;
        }

        return new BirthdayStatus(
                config.enabled(), state,
                birthday.month(), birthday.day(),
                config.title(), config.subtitle(), config.description(), config.giftType(),
                config.pointsAmount(),
                window.from() == null ? null : window.from().format(DATE),
                window.to() == null ? null : window.to().format(DATE),
                window.daysUntil(),
                claim == null ? null : new BirthdayStatus.Claim(
                        claim.year(), claim.amount(), claim.giftType(), claim.createdAt().format(DATETIME)),
                editable, nextEditable);
    }

    /**
     * 领取。
     *
     * <p>事务边界覆盖「写领取记录 → 加分 → 回填流水 id」三件事：
     * 任一步失败整体回滚，不会出现「记录建了、积分没到账」的孤儿凭证。
     */
    @Transactional
    public BirthdayStatus claim(String memberId) {
        BirthdayGiftConfig config = config();
        if (!config.enabled()) {
            throw BirthdayException.conflict("生日礼遇暂未开放");
        }
        if (!"POINTS".equalsIgnoreCase(config.giftType())) {
            // V1 只交付积分发放；赠饮模式要等 0 元兑换单与核销联动落地
            throw BirthdayException.conflict("生日赠饮尚未开通，请联系门店");
        }
        if (!config.usable()) {
            throw BirthdayException.conflict("生日礼遇配置不完整，请联系门店");
        }

        MemberBirthday birthday = birthday(memberId);
        if (birthday.month() == null || birthday.day() == null) {
            throw BirthdayException.conflict("请先填写生日再领取");
        }

        LocalDate today = LocalDate.now(Constants.ZONE);
        Window window = BirthdayWindow.of(birthday.month(), birthday.day(), today,
                config.windowMode(), config.windowDays());
        if (!window.claimable()) {
            throw BirthdayException.conflict("生日礼遇在 " + window.from().format(DATE)
                    + " 至 " + window.to().format(DATE) + " 开放，现在还领不了");
        }

        int giftYear = window.target().getYear();
        if (claim(memberId, giftYear) != null) {
            // 连点、或上次已经领过：返回同一份状态，不让用户看到红色报错
            return status(memberId);
        }

        long giftId;
        try {
            giftId = insertGift(memberId, giftYear, config);
        } catch (DuplicateKeyException race) {
            // 并发下的第二个人走到这里：唯一索引已经拦下，等同「已领取」
            return status(memberId);
        }

        int points = config.pointsAmount();
        ledger.adjust(memberId, points, PointsBizType.BIRTHDAY_GIFT, PointsRefType.BIRTHDAY_GIFT,
                String.valueOf(giftId), "生日礼遇");
        backfillLogId(giftId, memberId);

        return status(memberId);
    }

    /**
     * 填写 / 修改生日。一年一改。
     *
     * <p>月日都没变时不动 {@code birthday_updated_at}：把「确认一遍」也算作一次修改，
     * 会让真正写错的用户白等一年。
     */
    @Transactional
    public BirthdayStatus updateBirthday(String memberId, Integer month, Integer day) {
        if (month == null || day == null) {
            throw BirthdayException.invalid("请选择生日月份与日期");
        }
        if (month < 1 || month > 12) {
            throw BirthdayException.invalid("生日月份必须在 1-12 之间");
        }
        // 用闰年做上界判定，允许 2 月 29 日
        int maxDay = YearMonth.of(2000, month).lengthOfMonth();
        if (day < 1 || day > maxDay) {
            throw BirthdayException.invalid("生日日期超出当月天数");
        }

        MemberBirthday birthday = birthday(memberId);
        if (month.equals(birthday.month()) && day.equals(birthday.day())) {
            return status(memberId);
        }

        LocalDateTime now = LocalDateTime.now(Constants.ZONE);
        if (birthday.updatedAt() != null && birthday.updatedAt().plusYears(1).isAfter(now)) {
            throw BirthdayException.conflict("生日每年可修改一次，下次可修改时间："
                    + birthday.updatedAt().plusYears(1).toLocalDate().format(DATE));
        }

        jdbc.update("UPDATE members SET birthday_month = ?, birthday_day = ?, birthday_updated_at = ? WHERE id = ?",
                month, day, now, memberId);
        return status(memberId);
    }

    // ---- 内部 ----

    private record MemberBirthday(Integer month, Integer day, LocalDateTime updatedAt) {
    }

    private record ClaimRow(int year, int amount, String giftType, LocalDateTime createdAt) {
    }

    private MemberBirthday birthday(String memberId) {
        List<MemberBirthday> rows = jdbc.query(
                "SELECT birthday_month, birthday_day, birthday_updated_at FROM members WHERE id = ?",
                (rs, index) -> new MemberBirthday(
                        rs.getObject("birthday_month", Integer.class),
                        rs.getObject("birthday_day", Integer.class),
                        rs.getObject("birthday_updated_at", LocalDateTime.class)),
                memberId);
        if (rows.isEmpty()) {
            throw BirthdayException.memberNotFound(memberId);
        }
        return rows.get(0);
    }

    private ClaimRow claim(String memberId, int giftYear) {
        List<ClaimRow> rows = jdbc.query(
                "SELECT gift_year, amount, gift_type, created_at FROM member_birthday_gifts"
                        + " WHERE member_id = ? AND gift_year = ?",
                (rs, index) -> new ClaimRow(
                        rs.getInt("gift_year"),
                        rs.getInt("amount"),
                        rs.getString("gift_type"),
                        rs.getObject("created_at", LocalDateTime.class)),
                memberId, giftYear);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long insertGift(String memberId, int giftYear, BirthdayGiftConfig config) {
        jdbc.update("INSERT INTO member_birthday_gifts(member_id, gift_year, gift_type, amount, created_at)"
                        + " VALUES(?,?,?,?,?)",
                memberId, giftYear, config.giftType(), config.pointsAmount(),
                LocalDateTime.now(Constants.ZONE));
        // 同一事务同一条连接，LAST_INSERT_ID() 就是刚插入的礼遇记录 id；
        // 比 GeneratedKeyHolder 少一层 JDBC 细节，也让这个方法可以用纯 mock 单测。
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("生日礼遇记录写入后未返回主键");
        }
        return id;
    }

    /** 回填积分流水 id：客诉时能从礼遇记录直接跳到账本核对，不用靠时间戳猜。 */
    private void backfillLogId(long giftId, String memberId) {
        List<Long> ids = jdbc.query(
                "SELECT id FROM member_points_log WHERE member_id = ? AND ref_type = ? AND ref_id = ?"
                        + " ORDER BY id DESC LIMIT 1",
                (rs, index) -> rs.getLong("id"),
                memberId, PointsRefType.BIRTHDAY_GIFT.name(), String.valueOf(giftId));
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE member_birthday_gifts SET points_log_id = ? WHERE id = ?", ids.get(0), giftId);
        }
    }
}
