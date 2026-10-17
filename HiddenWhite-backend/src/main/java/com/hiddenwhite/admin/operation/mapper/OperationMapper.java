package com.hiddenwhite.admin.operation.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 店铺运营数据访问：读写 hidden_white 库的 stores、store_hours、store_daily_traffic，
 * 并从 orders 实时聚合当日流量。库名由 application.yml 的
 * mybatis-plus.configuration-properties.coffeeDb 注入，与 CoffeeMapper 共用同一占位。
 */
@Mapper
public interface OperationMapper {

    // ---- 营业状态 ----

    @Select("""
            SELECT manual_closed AS manualClosed, manual_open AS manualOpen,
                   status_note AS statusNote, closed_at AS closedAt
            FROM ${coffeeDb}.stores ORDER BY id LIMIT 1
            """)
    Map<String, Object> storeStatusRow();

    /**
     * 切换人工开关（三态语义）：closed=TRUE → 人工打烊（manual_closed=1, manual_open=0）；
     * closed=FALSE → 人工开启营业（manual_closed=0, manual_open=1），强制覆盖例休与时间窗口。
     * 目标状态已是当前状态时不更新（幂等，closed_at 不重复写入）；存量的
     * “自动判断”行（manual_closed=0 且 manual_open=0）切换为人工开启时必须命中。
     */
    @Update("""
            UPDATE ${coffeeDb}.stores
            SET manual_closed = #{closed}, manual_open = NOT #{closed}, status_note = #{note},
                closed_at = CASE WHEN #{closed} THEN NOW() ELSE NULL END
            WHERE id = (SELECT id FROM (SELECT id FROM ${coffeeDb}.stores ORDER BY id LIMIT 1) t)
              AND (manual_closed <> #{closed} OR manual_open = #{closed})
            """)
    int switchStatus(@Param("closed") boolean closed, @Param("note") String note);

    /** 状态不变时仅更新提示语。 */
    @Update("""
            UPDATE ${coffeeDb}.stores SET status_note = #{note}
            WHERE id = (SELECT id FROM (SELECT id FROM ${coffeeDb}.stores ORDER BY id LIMIT 1) t)
            """)
    int updateStatusNote(@Param("note") String note);

    // ---- 运营时段 ----

    @Select("""
            SELECT DATE_FORMAT(opening_time, '%H:%i') AS openingTime,
                   DATE_FORMAT(closing_time, '%H:%i') AS closingTime
            FROM ${coffeeDb}.stores ORDER BY id LIMIT 1
            """)
    Map<String, Object> defaultHours();

    @Update("""
            UPDATE ${coffeeDb}.stores SET opening_time = #{openTime}, closing_time = #{closeTime}
            WHERE id = (SELECT id FROM (SELECT id FROM ${coffeeDb}.stores ORDER BY id LIMIT 1) t)
            """)
    int updateDefaultHours(@Param("openTime") String openTime, @Param("closeTime") String closeTime);

    @Select("""
            SELECT day_of_week AS dayOfWeek, DATE_FORMAT(open_time, '%H:%i') AS openTime,
                   DATE_FORMAT(close_time, '%H:%i') AS closeTime, closed
            FROM ${coffeeDb}.store_hours WHERE store_id = 1 ORDER BY day_of_week
            """)
    List<Map<String, Object>> listHours();

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.store_hours WHERE store_id = 1")
    long countHours();

    @Insert("""
            INSERT INTO ${coffeeDb}.store_hours (store_id, day_of_week, open_time, close_time, closed)
            VALUES (1, #{dayOfWeek}, #{openTime}, #{closeTime}, #{closed})
            ON DUPLICATE KEY UPDATE open_time = VALUES(open_time), close_time = VALUES(close_time), closed = VALUES(closed)
            """)
    int upsertHour(@Param("dayOfWeek") int dayOfWeek,
                   @Param("openTime") String openTime,
                   @Param("closeTime") String closeTime,
                   @Param("closed") boolean closed);

    // ---- 日流量 ----

    /** 当日实时汇总：订单数 / 营业额 / 下单会员去重数（客流估算下限）。 */
    @Select("""
            SELECT COUNT(*) AS orderCount, COALESCE(SUM(total), 0) AS revenue,
                   COUNT(DISTINCT member_id) AS visitorEstimate
            FROM ${coffeeDb}.orders WHERE DATE(created_at) = #{date}
            """)
    Map<String, Object> dailySummary(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM ${coffeeDb}.orders WHERE status = 'IN_PROGRESS' AND DATE(created_at) = #{date}")
    long countInProgress(@Param("date") String date);

    @Select("""
            SELECT DATE_FORMAT(stat_date, '%Y-%m-%d') AS statDate, order_count AS orderCount, revenue,
                   visitor_count AS visitorCount, closed_minutes AS closedMinutes,
                   origin_order_count AS originOrderCount, origin_revenue AS originRevenue,
                   origin_visitor_count AS originVisitorCount, adjusted,
                   adjust_reason AS adjustReason, settled
            FROM ${coffeeDb}.store_daily_traffic WHERE stat_date = #{date}
            """)
    Map<String, Object> findTraffic(@Param("date") String date);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ${coffeeDb}.store_daily_traffic
            WHERE (#{from} IS NULL OR stat_date &gt;= #{from})
              AND (#{to} IS NULL OR stat_date &lt;= #{to})
            </script>
            """)
    long countTraffic(@Param("from") String from, @Param("to") String to);

    @Select("""
            <script>
            SELECT DATE_FORMAT(stat_date, '%Y-%m-%d') AS statDate, order_count AS orderCount, revenue,
                   visitor_count AS visitorCount, closed_minutes AS closedMinutes,
                   origin_order_count AS originOrderCount, origin_revenue AS originRevenue,
                   origin_visitor_count AS originVisitorCount, adjusted,
                   adjust_reason AS adjustReason, settled
            FROM ${coffeeDb}.store_daily_traffic
            WHERE (#{from} IS NULL OR stat_date &gt;= #{from})
              AND (#{to} IS NULL OR stat_date &lt;= #{to})
            ORDER BY stat_date DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> pageTraffic(@Param("from") String from,
                                          @Param("to") String to,
                                          @Param("offset") long offset,
                                          @Param("size") int size);

    /**
     * 结算冻结某日流量：新日期直接插入；已有行只刷新汇总值（保留人工累计的
     * closed_minutes，且已被人工调整过的列不覆盖），并将 settled 置 TRUE。
     */
    @Insert("""
            INSERT INTO ${coffeeDb}.store_daily_traffic
                (stat_date, order_count, revenue, visitor_count, closed_minutes, settled)
            VALUES (#{date}, #{orderCount}, #{revenue}, #{visitorCount}, 0, TRUE)
            ON DUPLICATE KEY UPDATE
                order_count = IF(adjusted, order_count, VALUES(order_count)),
                revenue = IF(adjusted, revenue, VALUES(revenue)),
                visitor_count = IF(adjusted, visitor_count, VALUES(visitor_count)),
                settled = TRUE
            """)
    int settleTraffic(@Param("date") String date,
                      @Param("orderCount") int orderCount,
                      @Param("revenue") BigDecimal revenue,
                      @Param("visitorCount") int visitorCount);

    @Insert("INSERT IGNORE INTO ${coffeeDb}.store_daily_traffic (stat_date) VALUES (#{date})")
    int ensureTrafficRow(@Param("date") String date);

    /** 累计当日人工打烊时长（恢复营业时按 closed_at → NOW() 计算）。 */
    @Update("""
            UPDATE ${coffeeDb}.store_daily_traffic SET closed_minutes = closed_minutes + #{minutes}
            WHERE stat_date = #{date}
            """)
    int addClosedMinutes(@Param("date") String date, @Param("minutes") long minutes);

    /**
     * 调整某日流量：origin_* 三列只在首次调整时快照原始值；
     * 仅已结算（settled=TRUE）的日期允许调整，WHERE 条件兜底。
     */
    @Update("""
            UPDATE ${coffeeDb}.store_daily_traffic
            SET origin_order_count = COALESCE(origin_order_count, order_count),
                origin_revenue = COALESCE(origin_revenue, revenue),
                origin_visitor_count = COALESCE(origin_visitor_count, visitor_count),
                order_count = #{orderCount}, revenue = #{revenue}, visitor_count = #{visitorCount},
                adjusted = TRUE, adjust_reason = #{reason}
            WHERE stat_date = #{date} AND settled = TRUE
            """)
    int adjustTraffic(@Param("date") String date,
                      @Param("orderCount") int orderCount,
                      @Param("revenue") BigDecimal revenue,
                      @Param("visitorCount") int visitorCount,
                      @Param("reason") String reason);
}
