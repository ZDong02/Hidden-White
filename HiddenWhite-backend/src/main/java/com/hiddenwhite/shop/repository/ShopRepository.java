package com.hiddenwhite.shop.repository;

import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.domain.ProductOption;
import com.hiddenwhite.shop.domain.StoreInfo;
import com.hiddenwhite.shop.dto.LoginRequest;
import com.hiddenwhite.shop.dto.MemberRegistrationRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.exception.NotFoundException;
import com.hiddenwhite.shop.util.PasswordHasher;
import com.hiddenwhite.shop.service.MemberLevels;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** 小程序 / H5 端数据访问（hidden_white 库）。 */
@Repository
public class ShopRepository {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final JdbcTemplate jdbc;

    public ShopRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- 商品与规格 ----

    public List<Product> products(Integer category) {
        String sql = "SELECT id,name,subtitle,description,detail_description,price,image,detail_image,category,has_milk " +
                "FROM products WHERE enabled=TRUE" + (category == null ? "" : " AND category=?") + " ORDER BY category,id";
        return category == null ? jdbc.query(sql, PRODUCT_MAPPER) : jdbc.query(sql, PRODUCT_MAPPER, category);
    }

    public Product product(String id) {
        return jdbc.query("SELECT id,name,subtitle,description,detail_description,price,image,detail_image,category,has_milk " +
                        "FROM products WHERE id=? AND enabled=TRUE", PRODUCT_MAPPER, id)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("商品不存在: " + id));
    }

    public List<ProductOption> options(String type) {
        return jdbc.query("SELECT label,extra_price FROM product_options WHERE option_type=? AND enabled=TRUE ORDER BY sort_order,id",
                (rs, row) -> new ProductOption(rs.getString("label"), rs.getBigDecimal("extra_price")), type);
    }

    // ---- 门店 ----

    public StoreInfo store() {
        return jdbc.query("SELECT name,address,short_address,latitude,longitude,distance_label,opening_time,closing_time,manual_closed,manual_open,status_note " +
                        "FROM stores ORDER BY id LIMIT 1", (rs, row) -> {
                    var opening = rs.getTime("opening_time").toLocalTime();
                    var closing = rs.getTime("closing_time").toLocalTime();
                    // 人工开启营业：强制开放，覆盖时间窗口自动判定；其余按人工打烊 + 时段判断
                    boolean open = rs.getBoolean("manual_open") || (!rs.getBoolean("manual_closed")
                            && !java.time.LocalTime.now().isBefore(opening) && java.time.LocalTime.now().isBefore(closing));
                    return new StoreInfo(rs.getString("name"), rs.getString("address"), rs.getString("short_address"),
                            rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("distance_label"),
                            opening.toString().substring(0, 5) + "–" + closing.toString().substring(0, 5), open,
                            rs.getString("status_note"));
                }).stream().findFirst().orElseThrow(() -> new NotFoundException("门店不存在"));
    }

    /** stores 表单行快照，供打烊判定使用（RowMapper 必须返回非 null 对象）。 */
    private record StoreStatus(boolean manualClosed, boolean manualOpen, String note,
                               LocalTime opening, LocalTime closing) {}

    /**
     * 下单前的营业状态校验：可下单返回 null，否则返回打烊提示语。
     * 优先级与店铺运营模块一致：人工打烊 > 人工开启（强制营业）> 当日休息 > 未到营业时间 > 已过打烊时间。
     * 判定逻辑在 mapper 之外执行，避免营业中返回 null 元素导致 findFirst() 抛 NPE。
     */
    public String closedReason() {
        List<StoreStatus> rows = jdbc.query(
                "SELECT manual_closed,manual_open,status_note,opening_time,closing_time FROM stores ORDER BY id LIMIT 1",
                (rs, row) -> new StoreStatus(rs.getBoolean("manual_closed"), rs.getBoolean("manual_open"),
                        rs.getString("status_note"),
                        rs.getTime("opening_time").toLocalTime(), rs.getTime("closing_time").toLocalTime()));
        if (rows.isEmpty()) {
            return null;
        }
        StoreStatus store = rows.get(0);
        if (store.manualClosed()) {
            return store.note() == null || store.note().isBlank() ? "店铺已打烊，期待与您再会" : store.note();
        }
        // 人工开启营业：立即生效，跳过例休与时间窗口自动判定
        if (store.manualOpen()) {
            return null;
        }
        var today = LocalDate.now(ZONE).getDayOfWeek().getValue();
        List<Boolean> dayOff = jdbc.query(
                "SELECT closed FROM store_hours WHERE store_id=1 AND day_of_week=?",
                (rs2, r2) -> rs2.getBoolean("closed"), today);
        if (!dayOff.isEmpty() && dayOff.get(0)) {
            return "今日例休，期待与您再会";
        }
        var now = LocalTime.now();
        if (now.isBefore(store.opening())) {
            return "未到营业时间，营业时间 " + store.opening().toString().substring(0, 5) + "–" + store.closing().toString().substring(0, 5);
        }
        if (!now.isBefore(store.closing())) {
            return "今日已打烊，明日 " + store.opening().toString().substring(0, 5) + " 恢复营业";
        }
        return null;
    }

    // ---- 会员 ----

    public Member member(String id) {
        return jdbc.query("SELECT id,display_name,phone,member_level,card_number,points,coupons,growth_value FROM members WHERE id=?",
                        MEMBER_MAPPER, id).stream().findFirst().orElseThrow(() -> new NotFoundException("会员不存在: " + id));
    }

    @Transactional
    public Member register(MemberRegistrationRequest request) {
        String phone = request.phone().trim();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE phone=?", Integer.class, phone) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "该手机号已注册，请直接登录");
        }
        String id = UUID.randomUUID().toString();
        String card = String.format("%04d %04d %04d", (int) (Math.random() * 10_000),
                (int) (Math.random() * 10_000), (int) (Math.random() * 10_000));
        try {
            jdbc.update("INSERT INTO members(id,display_name,phone,member_level,card_number) VALUES(?,?,?,?,?)",
                    id, request.displayName().trim(), phone, "普通会员", card);
            jdbc.update("INSERT INTO users(id,member_id,phone,password_hash) VALUES(?,?,?,?)",
                    UUID.randomUUID().toString(), id, phone, PasswordHasher.hash(request.password()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "该手机号已注册，请直接登录");
        }
        return member(id);
    }

    public Member login(LoginRequest request) {
        String phone = request.phone().trim();
        return jdbc.query("SELECT m.id,m.display_name,m.phone,m.member_level,m.card_number,m.points,m.coupons,m.growth_value,u.password_hash " +
                        "FROM users u JOIN members m ON m.id=u.member_id WHERE u.phone=?", (rs, row) -> {
                    String passwordHash = rs.getString("password_hash");
                    if (!PasswordHasher.matches(request.password(), passwordHash)) {
                        throw new BusinessException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
                    }
                    return MEMBER_MAPPER.mapRow(rs, row);
                }, phone).stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "手机号或密码错误"));
    }

    public List<MemberBenefit> benefits() {
        return jdbc.query("SELECT icon,title,description FROM member_benefits WHERE enabled=TRUE ORDER BY sort_order,id",
                (rs, row) -> new MemberBenefit(rs.getString("icon"), rs.getString("title"), rs.getString("description")));
    }

    // ---- 订单 ----

    public List<Order> orders(String memberId) {
        return jdbc.query("SELECT * FROM orders WHERE member_id=? ORDER BY created_at DESC", ORDER_MAPPER, memberId)
                .stream().map(this::withItems).toList();
    }

    public Order order(UUID id) {
        return jdbc.query("SELECT * FROM orders WHERE id=?", ORDER_MAPPER, id.toString()).stream()
                .findFirst().map(this::withItems).orElseThrow(() -> new NotFoundException("订单不存在: " + id));
    }

    /** 生成当日订单号（yyyyMMdd + 3 位序号），序号达到 999 后从头循环。 */
    public String nextOrderNumber(LocalDate date) {
        String prefix = NUMBER_DATE.format(date);
        String max = jdbc.queryForObject("SELECT MAX(order_number) FROM orders WHERE order_number LIKE ?", String.class, prefix + "%");
        int next = 100;
        if (max != null && max.length() > prefix.length()) {
            int last = Integer.parseInt(max.substring(prefix.length()));
            next = last >= 999 ? 100 : last + 1;
        }
        return prefix + String.format("%03d", next);
    }

    @Transactional
    public Order saveOrder(Order order) {
        ensureMember(order.memberId());
        jdbc.update("INSERT INTO orders(id,member_id,subtotal,discount,packaging_fee,total,item_count,pickup,order_number,created_at,ready_at,status,note) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", order.id().toString(), order.memberId(), order.subtotal(), order.discount(),
                order.packagingFee(), order.total(), order.count(), order.pickup(), order.number(), toLocal(order.createdAt()),
                toLocal(order.readyAt()), order.status().name(), order.note());
        for (OrderItem item : order.items()) {
            jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,size_label,temperature,milk,quantity,unit_price,line_total) " +
                            "VALUES(?,?,?,?,?,?,?,?,?)", order.id().toString(), item.productId(), item.name(), item.size(), item.temperature(),
                    item.milk(), item.quantity(), item.unitPrice(), item.lineTotal());
        }
        awardOrderPoints(order);
        return order;
    }

    /**
     * 下单发放积分与成长值，并按成长值晋升会员等级（等级只升不降）。
     * <p>规则：积分 = 订单实付金额整数部分（1 元 = 1 积分，向下取整）；
     * 成长值 = 积分 × 10（1 元 = 10 成长值）。金额为 0 时不发放。
     * 等级取「现有等级阶位」与「累计成长值对应阶位」中较高者。</p>
     * <p>调用时机：仅在 {@link #saveOrder} 事务内调用，下单失败时发放一并回滚。</p>
     */
    private void awardOrderPoints(Order order) {
        // 向下取整取实付金额整数部分，如 26.00 → 26 积分
        int points = order.total().setScale(0, RoundingMode.FLOOR).intValue();
        if (points <= 0) {
            return;
        }
        List<Member> rows = jdbc.query(
                "SELECT id,display_name,phone,member_level,card_number,points,coupons,growth_value FROM members WHERE id=?",
                MEMBER_MAPPER, order.memberId());
        if (rows.isEmpty()) {
            return;
        }
        Member current = rows.get(0);
        int growthValue = current.growthValue() + points * 10;
        String level = MemberLevels.maxLevel(current.level(), MemberLevels.levelFor(growthValue));
        jdbc.update("UPDATE members SET points=points+?,growth_value=?,member_level=? WHERE id=?",
                points, growthValue, level, order.memberId());
    }

    public Order complete(UUID id, OffsetDateTime completedAt) {
        jdbc.update("UPDATE orders SET status='COMPLETED',completed_at=? WHERE id=? AND status='IN_PROGRESS'",
                toLocal(completedAt), id.toString());
        return order(id);
    }

    private void ensureMember(String memberId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE id=?", Integer.class, memberId) == 0) {
            jdbc.update("INSERT INTO members(id,display_name,member_level,card_number) VALUES(?,?,?,?)",
                    memberId, "隐白客人", "普通会员", "AUTO-" + memberId.substring(0, Math.min(20, memberId.length())));
        }
    }

    private Order withItems(Order order) {
        List<OrderItem> items = jdbc.query("SELECT product_id,product_name,size_label,temperature,milk,quantity,unit_price,line_total " +
                        "FROM order_items WHERE order_id=? ORDER BY id", (rs, row) -> new OrderItem(rs.getString("product_id"),
                        rs.getString("product_name"), rs.getString("size_label"), rs.getString("temperature"), rs.getString("milk"),
                        rs.getInt("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total")), order.id().toString());
        return new Order(order.id(), order.memberId(), items, order.subtotal(), order.discount(), order.packagingFee(), order.total(),
                order.count(), order.pickup(), order.number(), order.createdAt(), order.readyAt(), order.status(), order.note(), order.completedAt());
    }

    private static LocalDateTime toLocal(OffsetDateTime value) { return value.toInstant().atZone(ZONE).toLocalDateTime(); }
    private static OffsetDateTime toOffset(Timestamp value) { return value.toLocalDateTime().atZone(ZONE).toOffsetDateTime(); }

    private static final RowMapper<Product> PRODUCT_MAPPER = (rs, row) -> new Product(rs.getString("id"), rs.getString("name"),
            rs.getString("subtitle"), rs.getString("description"), rs.getString("detail_description"), rs.getBigDecimal("price"),
            rs.getString("image"), rs.getString("detail_image"), rs.getInt("category"), rs.getBoolean("has_milk"));
    private static final RowMapper<Member> MEMBER_MAPPER = (rs, row) -> new Member(rs.getString("id"), rs.getString("display_name"),
            rs.getString("phone"), rs.getString("member_level"), rs.getString("card_number"), rs.getInt("points"),
            rs.getInt("coupons"), rs.getInt("growth_value"));
    private static final RowMapper<Order> ORDER_MAPPER = (rs, row) -> new Order(UUID.fromString(rs.getString("id")),
            rs.getString("member_id"), List.of(), rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount"),
            rs.getBigDecimal("packaging_fee"), rs.getBigDecimal("total"), rs.getInt("item_count"), rs.getString("pickup"),
            rs.getString("order_number"), toOffset(rs.getTimestamp("created_at")), toOffset(rs.getTimestamp("ready_at")),
            OrderStatus.valueOf(rs.getString("status")), rs.getString("note"),
            rs.getTimestamp("completed_at") == null ? null : toOffset(rs.getTimestamp("completed_at")));
}
