package com.hiddenwhite.shop.repository;

import com.hiddenwhite.shop.domain.Member;
import com.hiddenwhite.shop.domain.MemberBenefit;
import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.domain.PickupNumber;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/** 小程序 / H5 端数据访问（hidden_white 库）。 */
@Repository
public class ShopRepository {
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 占用「同一会员仅一张进行中订单」名额的状态集合。
     *
     * <p>由 {@link OrderStatus#isBlocking()} 反推生成，不在 SQL 里另抄一份状态列表 ——
     * 否则新增状态时两处必然走偏（漏掉 SQL 这侧就会多占名额或提前放行）。
     */
    private static final String BLOCKING_STATUS_SQL = Arrays.stream(OrderStatus.values())
            .filter(OrderStatus::isBlocking)
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(","));

    /**
     * 订单全列清单。
     *
     * <p>刻意不用 {@code SELECT *}：{@code ORDER_MAPPER} 依赖的列一旦增删，
     * 显式清单能让编译期之外的差异在 code review 里一眼可见；
     * 同时避免后续给表加宽列（大文本 / JSON）时被无谓地全量读回。
     */
    private static final String ORDER_COLUMNS =
            "id,member_id,subtotal,discount,packaging_fee,total,item_count,pickup,order_number," +
                    "created_at,ready_at,status,note,completed_at,pay_channel,transaction_id,prepay_id," +
                    "prepay_expire_at,paid_at,refund_id,refunded_at,cancelled_at,cancel_reason";

    /** 明细列清单，供单查与批量查共用，保证两条路径映射结果一致。 */
    private static final String ORDER_ITEM_COLUMNS =
            "order_id,product_id,product_name,size_label,temperature,milk,quantity,unit_price,line_total";

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
                    // 取一次当前时刻：两次 now() 跨过打烊整点时会算出自相矛盾的结果。
                    // 必须带上门店时区：LocalTime.now() 取的是 JVM 默认时区，
                    // 服务器跑在 UTC 时「现在」会比门店时间早 8 小时，营业中会被判成未开门。
                    var now = LocalTime.now(ZONE);
                    // 人工开启营业：强制开放，覆盖时间窗口自动判定；其余按人工打烊 + 时段判断
                    boolean open = rs.getBoolean("manual_open") || (!rs.getBoolean("manual_closed")
                            && !now.isBefore(opening) && now.isBefore(closing));
                    return new StoreInfo(rs.getString("name"), rs.getString("address"), rs.getString("short_address"),
                            rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("distance_label"),
                            opening.toString().substring(0, 5) + "–" + closing.toString().substring(0, 5), open,
                            rs.getString("status_note"));
                }).stream().findFirst().orElseThrow(() -> new NotFoundException("门店不存在"));
    }

    /** stores 表单行快照，供打烊判定使用（RowMapper 必须返回非 null 对象）。 */
    private record StoreStatus(long id, boolean manualClosed, boolean manualOpen, String note,
                               LocalTime opening, LocalTime closing) {}

    /**
     * 下单前的营业状态校验：可下单返回 null，否则返回打烊提示语。
     * 优先级与店铺运营模块一致：人工打烊 > 人工开启（强制营业）> 当日休息 > 未到营业时间 > 已过打烊时间。
     * 判定逻辑在 mapper 之外执行，避免营业中返回 null 元素导致 findFirst() 抛 NPE。
     *
     * <p>门店 id 取自本次读到的行，而不是写死 {@code store_id=1}：
     * {@link #store()} 用的是 {@code ORDER BY id LIMIT 1}，两处若对「哪家门店」的假设不同，
     * 一旦出现第二家门店，就会出现「按 A 店的营业时间判断、读 B 店的排班」这种错位。
     */
    public String closedReason() {
        List<StoreStatus> rows = jdbc.query(
                "SELECT id,manual_closed,manual_open,status_note,opening_time,closing_time FROM stores ORDER BY id LIMIT 1",
                (rs, row) -> new StoreStatus(rs.getLong("id"), rs.getBoolean("manual_closed"), rs.getBoolean("manual_open"),
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
                "SELECT closed FROM store_hours WHERE store_id=? AND day_of_week=?",
                (rs2, r2) -> rs2.getBoolean("closed"), store.id(), today);
        if (!dayOff.isEmpty() && dayOff.get(0)) {
            return "今日例休，期待与您再会";
        }
        var now = LocalTime.now(ZONE);
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

    /**
     * 会员订单列表（含明细）。
     *
     * <p><b>为什么明细要批量取</b>：早期实现是「先查订单、再对每张订单查一次 order_items」，
     * 历史有 N 张就是 N+1 次往返。改造前 {@code OrderService.create} 也拿本方法做
     * 「是否有订单 / 是否有进行中订单」的判断，于是单次下单的查询数高达 {@code 2(1+N)}。
     * 现在下单改走 {@code LIMIT 1} 探测，本方法只剩「列表查询」一个用途；
     * 明细改为一次 {@code IN} 批量取后，整次列表查询恒定为 2 次。
     */
    public List<Order> orders(String memberId) {
        List<Order> rows = jdbc.query(
                "SELECT " + ORDER_COLUMNS + " FROM orders WHERE member_id=? ORDER BY created_at DESC",
                ORDER_MAPPER, memberId);
        return attachItems(rows);
    }

    public Order order(UUID id) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM orders WHERE id=?", ORDER_MAPPER, id.toString())
                .stream().findFirst().map(this::withItems)
                .orElseThrow(() -> new NotFoundException("订单不存在: " + id));
    }

    /**
     * 该会员当前占名额的那张订单（没有则 null）。
     *
     * <p>与 {@link #orders} 的区别：状态条件直接下推到 SQL 并 {@code LIMIT 1}，
     * 不必把全部历史订单（含明细）读回内存再筛。老顾客历史越长，差异越明显。
     */
    public Order activeOrder(String memberId) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM orders WHERE member_id=? AND status IN ("
                                + BLOCKING_STATUS_SQL + ") ORDER BY created_at DESC LIMIT 1",
                        ORDER_MAPPER, memberId)
                .stream().findFirst().map(this::withItems).orElse(null);
    }

    /**
     * 该会员是否下过单（首单减免判定）。
     *
     * <p>只需要一个布尔值，却去把整段订单历史连同明细读回来，是把「存在性判断」
     * 当成了「列表查询」；{@code LIMIT 1} 的探测走 {@code member_id} 索引，
     * 与订单量无关。
     */
    public boolean hasOrders(String memberId) {
        return exists("SELECT 1 FROM orders WHERE member_id=? LIMIT 1", memberId);
    }

    /**
     * 该会员是否有进行中订单（占名额的那几个状态）。
     *
     * <p>状态集合来自 {@link OrderStatus#isBlocking()}，与
     * {@code OrderService} 的判定同源，避免 SQL 与 Java 各写一份。
     */
    public boolean hasActiveOrder(String memberId) {
        return exists("SELECT 1 FROM orders WHERE member_id=? AND status IN (" + BLOCKING_STATUS_SQL + ") LIMIT 1",
                memberId);
    }

    private boolean exists(String sql, Object... args) {
        List<Integer> rows = jdbc.query(sql, (rs, row) -> 1, args);
        return !rows.isEmpty();
    }

    /**
     * 生成当日订单号（yyyyMMdd + 4 位序号），序号达到 9999 后从头循环。
     *
     * <p><b>为何必须用 CAST 转数值比较</b>：早期实现写的是
     * {@code MAX(order_number)}——那是<b>字符串</b>比较。当历史数据存在
     * 3 位序号（11 位号码，如 {@code 20261018101}）与 4 位序号（12 位号码，
     * 如 {@code 202610180102}）两种长度时，字符串排序会把 3 位序号那批
     * 判为"更大"，于是每次都拿它当基准，生成的号反复撞唯一索引
     * {@code uk_orders_order_number}，表现为「第一单能下、第二单必失败」。
     * 转成数值后再比较，11 位与 12 位号码能被正确解析成 101 与 102。
     */
    public String nextOrderNumber(LocalDate date) {
        String prefix = NUMBER_DATE.format(date);
        // SUBSTRING 从第 9 位（下标 8）起取序号：前缀 yyyyMMdd 恒为 8 位
        Integer maxSeq = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(order_number, 9) AS UNSIGNED)) FROM orders "
                        + "WHERE order_number LIKE ? AND order_number REGEXP CONCAT('^[0-9]{8}[0-9]+$')",
                Integer.class, prefix + "%");
        int lastSeq = maxSeq == null ? 0 : maxSeq;
        // 序号下限固定 1000（4 位），避免与 3 位旧号混淆
        int next = lastSeq >= ORDER_SEQ_MAX ? 1000 : lastSeq + 1;
        if (next < 1000) {
            next = 1000;
        }
        return prefix + String.format("%04d", next);
    }

    /** 订单号序号上限（4 位共 9999 个）。 */
    private static final int ORDER_SEQ_MAX = 9999;

    /**
     * 分配当日取餐号（1~999 循环）。
     *
     * <p><b>与 {@link #nextOrderNumber} 的区别</b>：订单号是对账凭据，跨日重置但当日严格递增；
     * 取餐号是门店叫号凭据，同样跨日重置，但必须<b>从 1 开始</b>，
     * 且当日取餐号互不相同。两者不再互相派生——改造前取餐号取自订单号末 3 位，
     * 上限只有 999，且与订单号序号强耦合。
     *
     * <p>取号基准是<b>当日全店已分配过的最大取餐号</b>（含已取消订单）：
     * 只要门店不存在两笔同号进行中订单，就不会拿错餐；递增扫描天然保证这一点。
     */
    public String nextPickupNumber(LocalDate date) {
        // 声明为 Integer 而非 int：当日无任何订单时 MAX(...) 返回 NULL，
        // 用原始类型会导致 jdbc 取值时抛类型转换异常而非返回 null。
        //
        // 这里用「左闭右开区间」而不是 DATE(created_at)=?：后者对列套函数，
        // created_at 上的索引会直接失效，退化为全表扫描——订单表越跑越慢。
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        Integer max = jdbc.queryForObject(
                "SELECT MAX(CAST(pickup AS UNSIGNED)) FROM orders "
                        + "WHERE created_at >= ? AND created_at < ? AND pickup REGEXP '^[0-9]+$'",
                Integer.class, dayStart, dayEnd);
        int lastUsed = max == null ? 0 : max;
        return PickupNumber.format(PickupNumber.nextAfter(lastUsed));
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
        return order;
    }

    /**
     * 下单发放积分与成长值，并按成长值晋升会员等级（等级只升不降）。
     * <p>规则：积分 = 订单实付金额整数部分（1 元 = 1 积分，向下取整）；
     * 成长值 = 积分 × 10（1 元 = 10 成长值）。金额为 0 时不发放。
     * 等级取「现有等级阶位」与「累计成长值对应阶位」中较高者。</p>
     * <p>调用时机：仅在 {@link #markPaid} 的事务内调用——收款成功才发放，
     * 未支付即取消的订单不会产生积分。</p>
     *
     * <p><b>为什么成长值必须写成自增而不是直接赋值</b>：早期实现先 {@code SELECT growth_value}
     * 再把 {@code 旧值 + 增量} 整体写回，同一会员并发下两单（连点、双端同时下单）时，
     * 后提交的那笔会用自己的计算结果覆盖掉另一笔的增量 —— 典型丢失更新，
     * 顾客会少拿成长值且无从对账。改成 {@code growth_value = growth_value + ?} 后，
     * 累加由数据库保证原子性。
     *
     * <p>等级是「读—算—写」，仍存在极窄的并发窗口。这里用 {@code growth_value=?} 作为
     * 写入条件：若期间被别的订单改过，本次写入自然不生效，而那个把成长值推高的订单
     * 会按更高的成长值算出不低于本次的等级，因此等级只会「最多滞后一单」，
     * 不会算错或降级，下一单即刻追平。
     */
    private void awardOrderPoints(Order order) {
        // 向下取整取实付金额整数部分，如 26.00 → 26 积分
        int points = order.total().setScale(0, RoundingMode.FLOOR).intValue();
        if (points <= 0) {
            return;
        }
        int growthGain = points * 10;
        int updated = jdbc.update(
                "UPDATE members SET points=points+?,growth_value=growth_value+? WHERE id=?",
                points, growthGain, order.memberId());
        if (updated == 0) {
            return;
        }
        List<MemberGrowth> rows = jdbc.query(
                "SELECT member_level,growth_value FROM members WHERE id=?",
                (rs, row) -> new MemberGrowth(rs.getString("member_level"), rs.getInt("growth_value")),
                order.memberId());
        if (rows.isEmpty()) {
            return;
        }
        MemberGrowth current = rows.get(0);
        String level = MemberLevels.maxLevel(current.level(), MemberLevels.levelFor(current.growthValue()));
        jdbc.update("UPDATE members SET member_level=? WHERE id=? AND growth_value=?",
                level, order.memberId(), current.growthValue());
    }

    /** 晋升判定只需要等级与成长值两列，不为它去构造一个字段残缺的 {@link Member}。 */
    private record MemberGrowth(String level, int growthValue) {}

    /**
     * 完成取餐。
     *
     * <p>仅允许从 {@code PAID} 推进——「没付钱不能标记取餐」是状态机的核心不变式。
     * 条件更新天然防并发：若订单已被超时任务改为 CANCELLED，WHERE 不命中。
     */
    public Order complete(UUID id, OffsetDateTime completedAt) {
        int updated = jdbc.update(
                "UPDATE orders SET status='COMPLETED',completed_at=? WHERE id=? AND status='PAID'",
                toLocal(completedAt), id.toString());
        if (updated == 0) {
            // 未命中说明状态不是 PAID：让状态机给出准确原因（未收款 / 已取消 / 不存在）
            order(id).status().assertCanTransitionTo(OrderStatus.COMPLETED);
        }
        return order(id);
    }

    /**
     * 记录 JSAPI 预支付会话。
     *
     * <p>预支付会话有时效（默认 2 小时），过期后 {@code prepayId} 无法再唤起支付，
     * 需重新统一下单。因此这里同时写入过期时间，供超时任务清理。
     *
     * <p><b>保留说明</b>：微信支付接入尚未启用，本方法当前零调用方，
     * 但下单链路已实现，接入支付时直接启用。
     */
    public Order markAwaitingPayment(UUID id, String prepayId, OffsetDateTime expireAt) {
        int updated = jdbc.update(
                "UPDATE orders SET status='PENDING_PAYMENT',prepay_id=?,prepay_expire_at=? "
                        + "WHERE id=? AND status IN ('CREATED','PENDING_PAYMENT')",
                prepayId, toLocal(expireAt), id.toString());
        if (updated == 0) {
            Order existing = order(id);
            existing.status().assertCanTransitionTo(OrderStatus.PENDING_PAYMENT);
        }
        return order(id);
    }

    /**
     * 确认支付成功。
     *
     * <p><b>幂等的关键</b>：条件里限定 {@code status='PENDING_PAYMENT'}，
     * 且 {@code transaction_id} 上有唯一索引。微信支付的通知可能重复投递，
     * 第二次调用会因条件不命中而返回 0，不会重复入账。
     *
     * <p><b>为什么积分发放挂在这里而不是 {@link #saveOrder}</b>：下单不等于收款。
     * 改造前下单事务里就发放积分与成长值，于是「下单后未支付、超时取消」的订单
     * 也会让会员白拿积分，且取消链路没有回收动作——是实打实的资损。
     * 改为状态真正推进到 {@code PAID} 之后发放，条件更新保证只发一次：
     * 重复回调走上面的幂等分支直接返回，不会二次累加。
     *
     * @param channel 支付渠道，如 WECHAT_JSAPI
     * @param transactionId 微信支付交易号
     */
    @Transactional
    public Order markPaid(UUID id, String channel, String transactionId, OffsetDateTime paidAt) {
        int updated = jdbc.update(
                "UPDATE orders SET status='PAID',pay_channel=?,transaction_id=?,paid_at=?,prepay_id=NULL "
                        + "WHERE id=? AND status IN ('CREATED','PENDING_PAYMENT')",
                channel, transactionId, toLocal(paidAt), id.toString());
        if (updated == 0) {
            Order existing = order(id);
            if (existing.status() == OrderStatus.PAID && transactionId.equals(existing.transactionId())) {
                return existing; // 重复通知，幂等返回
            }
            existing.status().assertCanTransitionTo(OrderStatus.PAID);
        }
        Order paid = order(id);
        awardOrderPoints(paid);
        return paid;
    }

    /** 取消订单。{@code reason} 见 init.sql 注释。 */
    public Order cancel(UUID id, String reason, OffsetDateTime cancelledAt) {
        int updated = jdbc.update(
                "UPDATE orders SET status='CANCELLED',cancel_reason=?,cancelled_at=? "
                        + "WHERE id=? AND status IN ('CREATED','PENDING_PAYMENT','PAID')",
                reason, toLocal(cancelledAt), id.toString());
        if (updated == 0) {
            order(id).status().assertCanTransitionTo(OrderStatus.CANCELLED);
        }
        return order(id);
    }

    /**
     * 记录退款结果。仅更新退款相关列，不改动状态（退款后的终态由业务决定）。
     *
     * <p><b>保留说明</b>：微信支付接入尚未启用，本方法当前零调用方，
     * 但退款回调落地时需要它，且对应 orders 表的 refund_id / refunded_at 列。
     * 接入支付时直接启用，不需重写。
     */
    public Order markRefunded(UUID id, String refundId, OffsetDateTime refundedAt) {
        jdbc.update("UPDATE orders SET refund_id=?,refunded_at=? WHERE id=?",
                refundId, toLocal(refundedAt), id.toString());
        return order(id);
    }

    /**
     * 按商户订单号查订单：支付回调按 out_trade_no 定位业务订单。
     *
     * <p><b>保留说明</b>：同 {@link #markRefunded}，为支付回调预留，当前零调用方。
     */
    public Order findByNumber(String orderNumber) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM orders WHERE order_number=?", ORDER_MAPPER, orderNumber).stream()
                .findFirst().orElseThrow(() -> new NotFoundException("订单不存在: " + orderNumber));
    }

    /**
     * 找出已超过有效期仍未进入终态的订单，供超时取消任务扫描。
     *
     * <p>逐条判断有效期而非在 SQL 里比较 {@code prepay_expire_at}，是为了让
     * 「制作时长 + 30 分钟缓冲」这条业务规则只存在于
     * {@link OrderStatus#timeoutAfter} 一处，避免 SQL 与 Java 各写一套阈值而不一致。
     *
     * <p><b>为什么 SQL 里仍要带一个时间下界</b>：判定规则留在 Java，但候选集不能无界——
     * 改造前这条语句把<b>全部</b>进行中订单（含刚下的单）读进内存再逐条过滤，
     * 活跃订单越多，每分钟一次的扫描就越重，而真正可能过期的永远只有最早那一批。
     * 这里用「最短有效期」（{@link OrderStatus#TIMEOUT_BUFFER}）回推一个绝对下界：
     * 比它更年轻的订单不可能过期，交给下一分钟的扫描即可。
     */
    public List<Order> findExpired(LocalDateTime now) {
        LocalDateTime lowerBound = now.minus(OrderStatus.TIMEOUT_BUFFER);
        List<Order> candidates = jdbc.query(SELECT_ACTIVE_ORDERS_SINCE, ORDER_MAPPER, lowerBound);
        OffsetDateTime moment = now.atZone(ZONE).toOffsetDateTime();
        return candidates.stream()
                .filter(order -> order.createdAt()
                        .plus(OrderStatus.timeoutAfter(order.createdAt(), order.readyAt()))
                        .plusSeconds(60)
                        .isBefore(moment))
                .toList();
    }

    /** 未进入终态、且创建时间早于下界的订单扫描语句。列清单与 {@link #orders} 同源，避免两处投影漂移。 */
    private static final String SELECT_ACTIVE_ORDERS_SINCE =
            "SELECT " + ORDER_COLUMNS + " FROM orders WHERE status IN (" + BLOCKING_STATUS_SQL + ") AND created_at < ?";

    private void ensureMember(String memberId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE id=?", Integer.class, memberId) == 0) {
            jdbc.update("INSERT INTO members(id,display_name,member_level,card_number) VALUES(?,?,?,?)",
                    memberId, "隐白客人", "普通会员", "AUTO-" + memberId.substring(0, Math.min(20, memberId.length())));
        }
    }

    /** 单张订单补明细。 */
    private Order withItems(Order order) {
        List<OrderItem> items = jdbc.query(
                "SELECT " + ORDER_ITEM_COLUMNS + " FROM order_items WHERE order_id=? ORDER BY id",
                ORDER_ITEM_MAPPER, order.id().toString());
        return copyWithItems(order, items);
    }

    /**
     * 批量补明细：一次 {@code IN} 查询取回整批订单的明细，再按 order_id 归位。
     *
     * <p>返回顺序与入参一致（订单列表按 created_at DESC，不能因为 Map 的遍历顺序被打乱）。
     */
    private List<Order> attachItems(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<String> orderIds = orders.stream().map(order -> order.id().toString()).toList();
        String placeholders = orderIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<String, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();
        jdbc.query("SELECT " + ORDER_ITEM_COLUMNS + " FROM order_items WHERE order_id IN (" + placeholders + ") ORDER BY id",
                rs -> {
                    itemsByOrder
                            .computeIfAbsent(rs.getString("order_id"), key -> new ArrayList<>())
                            .add(ORDER_ITEM_MAPPER.mapRow(rs, 0));
                }, orderIds.toArray());

        List<Order> result = new ArrayList<>(orders.size());
        for (Order order : orders) {
            result.add(copyWithItems(order, itemsByOrder.getOrDefault(order.id().toString(), List.of())));
        }
        return List.copyOf(result);
    }

    /** 订单是不可变记录：补明细只能整条重建，这里是唯一的重建点，避免字段漏拷。 */
    private static Order copyWithItems(Order order, Collection<OrderItem> items) {
        return new Order(order.id(), order.memberId(), List.copyOf(items), order.subtotal(), order.discount(),
                order.packagingFee(), order.total(), order.count(), order.pickup(), order.number(),
                order.createdAt(), order.readyAt(), order.status(), order.note(), order.completedAt(),
                order.payChannel(), order.transactionId(), order.prepayId(), order.prepayExpireAt(), order.paidAt(),
                order.refundId(), order.refundedAt(), order.cancelledAt(), order.cancelReason());
    }

    private static LocalDateTime toLocal(OffsetDateTime value) { return value.toInstant().atZone(ZONE).toLocalDateTime(); }
    private static OffsetDateTime toOffset(Timestamp value) { return value.toLocalDateTime().atZone(ZONE).toOffsetDateTime(); }

    /** 可空时间列的读取：paid_at / refunded_at 等未发生时为 NULL。 */
    private static OffsetDateTime toOffsetOrNull(Timestamp value) {
        return value == null ? null : toOffset(value);
    }

    private static final RowMapper<Product> PRODUCT_MAPPER = (rs, row) -> new Product(rs.getString("id"), rs.getString("name"),
            rs.getString("subtitle"), rs.getString("description"), rs.getString("detail_description"), rs.getBigDecimal("price"),
            rs.getString("image"), rs.getString("detail_image"), rs.getInt("category"), rs.getBoolean("has_milk"));
    private static final RowMapper<Member> MEMBER_MAPPER = (rs, row) -> new Member(rs.getString("id"), rs.getString("display_name"),
            rs.getString("phone"), rs.getString("member_level"), rs.getString("card_number"), rs.getInt("points"),
            rs.getInt("coupons"), rs.getInt("growth_value"));

    /** 订单明细映射：单查与批量查共用，保证两条路径读出的明细完全一致。 */
    private static final RowMapper<OrderItem> ORDER_ITEM_MAPPER = (rs, row) -> new OrderItem(rs.getString("product_id"),
            rs.getString("product_name"), rs.getString("size_label"), rs.getString("temperature"), rs.getString("milk"),
            rs.getInt("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total"));
    private static final RowMapper<Order> ORDER_MAPPER = (rs, row) -> new Order(UUID.fromString(rs.getString("id")),
            rs.getString("member_id"), List.of(), rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount"),
            rs.getBigDecimal("packaging_fee"), rs.getBigDecimal("total"), rs.getInt("item_count"), rs.getString("pickup"),
            rs.getString("order_number"), toOffset(rs.getTimestamp("created_at")), toOffset(rs.getTimestamp("ready_at")),
            OrderStatus.valueOf(rs.getString("status")), rs.getString("note"),
            toOffsetOrNull(rs.getTimestamp("completed_at")), rs.getString("pay_channel"), rs.getString("transaction_id"),
            rs.getString("prepay_id"), toOffsetOrNull(rs.getTimestamp("prepay_expire_at")), toOffsetOrNull(rs.getTimestamp("paid_at")),
            rs.getString("refund_id"), toOffsetOrNull(rs.getTimestamp("refunded_at")), toOffsetOrNull(rs.getTimestamp("cancelled_at")),
            rs.getString("cancel_reason"));
}
