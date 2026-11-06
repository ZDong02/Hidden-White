package com.hiddenwhite.shop.service;

import com.hiddenwhite.common.coupon.Coupon;
import com.hiddenwhite.common.coupon.CouponService;
import com.hiddenwhite.common.discount.AppliedDiscount;
import com.hiddenwhite.common.discount.DiscountLine;
import com.hiddenwhite.common.discount.LevelDiscountService;
import com.hiddenwhite.common.upsize.UpsizeDecision;
import com.hiddenwhite.common.upsize.UpsizeService;
import com.hiddenwhite.common.upsize.UpsizeUnavailableReason;
import com.hiddenwhite.shop.domain.Order;
import com.hiddenwhite.shop.domain.OrderCancelReason;
import com.hiddenwhite.shop.domain.OrderItem;
import com.hiddenwhite.shop.domain.OrderStatus;
import com.hiddenwhite.shop.domain.PriceSummary;
import com.hiddenwhite.shop.domain.Product;
import com.hiddenwhite.shop.dto.CreateOrderRequest;
import com.hiddenwhite.shop.dto.OrderItemRequest;
import com.hiddenwhite.shop.exception.BusinessException;
import com.hiddenwhite.shop.pay.PayDevModeProperties;
import com.hiddenwhite.shop.repository.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hiddenwhite.common.domain.Constants.ZONE;

/** 订单服务：下单、报价、查询与完成。 */
@Service
public class OrderService {
    public static final String GUEST = "guest";
    private static final BigDecimal PACKAGING_FEE = BigDecimal.ZERO;
    private static final BigDecimal FIRST_ORDER_DISCOUNT = BigDecimal.valueOf(6);
    /** 账单行上的优惠名称：首单礼遇与优惠券互斥，前端靠它显示「减的是哪一笔」。 */
    private static final String FIRST_ORDER_LABEL = "首单礼遇";
    private static final int PREPARATION_MINUTES = 5;

    /** 开发模式下的支付渠道标记。对账时一眼可辨不是真实微信支付。 */
    private static final String DEV_PAY_CHANNEL = "DEV_MODE";

    private final CatalogService catalog;
    private final ShopRepository repository;
    private final PayDevModeProperties devMode;
    private final CouponService coupons;
    private final LevelDiscountService levelDiscounts;
    private final UpsizeService upsizes;

    public OrderService(CatalogService catalog, ShopRepository repository, PayDevModeProperties devMode,
                        CouponService coupons, LevelDiscountService levelDiscounts, UpsizeService upsizes) {
        this.catalog = catalog;
        this.repository = repository;
        this.devMode = devMode;
        this.coupons = coupons;
        this.levelDiscounts = levelDiscounts;
        this.upsizes = upsizes;
    }

    public List<Order> findAll(String memberId) {
        return repository.orders(normalizeMemberId(memberId));
    }

    public Order find(UUID id) {
        return repository.order(id);
    }

    /** 读取订单并校验归属：非本人订单直接 403，避免越权读单。 */
    public Order findOwned(UUID id, String memberId) {
        Order order = find(id);
        assertOwned(order, memberId);
        return order;
    }

    /** 当前占名额的订单；状态过滤下推到 SQL，不读整段历史。 */
    public Order current(String memberId) {
        return repository.activeOrder(normalizeMemberId(memberId));
    }

    /**
     * 下单。
     *
     * <p>归属以服务端解析出的 {@code callerId}（来自 token）为准，
     * 请求体里的 memberId 一律忽略，避免伪造他人身份下单 / 冒领首单减免。
     *
     * <p><b>为什么这里必须 synchronized</b>：订单号与取餐号都是「先查当日最大值再 +1」
     * 的读—写序列，这两步之间没有数据库层面的唯一约束替我们兜底（{@code pickup} 列上
     * 没有唯一索引，{@code order_number} 上虽有却只会报错而非自动换号）。
     * 单实例部署下用方法级锁把取号串起来最省事。
     *
     * <p>代价是下单彼此串行，因此方法体内只能做必要的数据库访问 ——
     * 首单判定与名额判定都改成了 {@code LIMIT 1} 探测（见 {@code calculate} 与本方法首段），
     * 不再在锁内读整段订单历史。
     *
     * <p><b>已知边界</b>：多实例部署时该锁不跨进程，取号需改为数据库序列或
     * 「唯一索引 + 重试」。当前为单店单实例，暂不需要。
     *
     * <p><b>优惠券为什么在这里核销，而不是下单前</b>：券必须挂在订单号上
     * （{@code member_coupons.order_id}），而订单 id 要到这里才生成。
     * 方法加了 {@code @Transactional}，订单落库与券核销同一事务：
     * 核销失败（券刚好被别处用掉）会连订单一起回滚，不会出现
     * 「钱按优惠算了、券却没扣」的资损。取消时由 {@link CouponService#releaseForOrder} 退回。
     */
    @Transactional
    public synchronized Order create(CreateOrderRequest request, String callerId) {
        String closedReason = repository.closedReason();
        if (closedReason != null) {
            throw new BusinessException(HttpStatus.CONFLICT, closedReason);
        }
        String owner = normalizeMemberId(callerId);
        // 只探测「是否存在占名额的订单」，不再把整段订单历史连同明细读回来做 null 判断。
        if (repository.hasActiveOrder(owner)) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前有订单待支付或待取餐，请先处理或等待订单超时");
        }
        String level = repository.memberLevel(owner);
        // 升杯必须先于算价：它改的是订单行的杯型，不是账单上的一笔减免
        UpsizeDecision upsize = resolveUpsize(owner, level, request);
        assertUpsizeUsable(upsize);
        List<OrderItem> items = buildItems(request.items(), upsize);
        // 先算商品金额：升杯会把目标杯型的差价计入商品金额（账面必须是真实杯型的价格），
        // 折扣与券则按「扣掉升杯差价」的基数判定 —— 白送的 6 元不该被再让利一次。
        BigDecimal subtotal = subtotalOf(items);
        BigDecimal upsizeValue = upsize.effective() ? upsize.sizeDiff() : BigDecimal.ZERO;
        BigDecimal discountBase = subtotal.subtract(upsizeValue);
        Coupon coupon = coupons.resolveForOrder(owner, request.couponId(), discountBase);
        OffsetDateTime created = OffsetDateTime.now(ZONE);
        OffsetDateTime ready = created.plusMinutes(PREPARATION_MINUTES);
        LocalDate orderDate = LocalDate.ofInstant(created.toInstant(), ZONE);
        // 会员折扣按「下单当天是星期几」判定，所以日期要先于算价确定
        AppliedDiscount levelDiscount = levelDiscounts.evaluate(level, discountBase, orderDate);
        PriceSummary summary = calculate(subtotal, upsize, items, owner, coupon, levelDiscount);
        String number = repository.nextOrderNumber(orderDate);
        // 取餐号独立取号：1~9999 每日循环，不再从订单号尾部派生。
        // 原实现 orderNumber.substring(len-3) 有两个硬伤：上限只有 999，
        // 且取餐号随订单号跨日重置，门店叫号时分不清是哪一天的号。
        String pickup = repository.nextPickupNumber(orderDate);
        Order order = new Order(UUID.randomUUID(), owner, List.copyOf(items), summary.subtotal(), summary.discount(),
                PACKAGING_FEE, summary.total(),
                items.stream().mapToInt(OrderItem::quantity).sum(), pickup,
                number, created, ready, OrderStatus.CREATED,
                request.note() == null ? "" : request.note().trim(), null,
                null, null, null, null, null, null, null, null, null);
        Order saved = repository.saveOrder(order);

        // 升杯占用：写记录 + 扣分。与订单同一事务，任一步失败整体回滚，
        // 不会留下「扣了分没升杯」或「升了杯没扣分」的中间态。
        // 记录 id 要回填到账单行的 ref_id 上，客诉时才能从「免费升杯 −6」跳回具体那一次额度。
        long upsizeRecordId = upsize.effective()
                ? upsizes.record(owner, saved.id().toString(), upsize)
                : 0L;

        // 优惠明细落库：一单可能有「会员折扣 + 优惠券」多条，逐条记下来才能对账
        List<DiscountLine> lines = upsizeRecordId > 0
                ? bindUpsizeRef(summary.discounts(), upsizeRecordId)
                : summary.discounts();
        if (!lines.isEmpty()) {
            repository.saveDiscounts(saved.id().toString(), lines);
        }

        // 只有「券真的被采用」时才核销。首单礼遇更划算时 couponId 为 null，券原样保留到下次。
        if (summary.couponId() != null) {
            coupons.markUsed(summary.couponId(), owner, saved.id().toString(), summary.couponDiscount());
        }

        // 开发模式：跳过真实支付，直接推进到 PAID，
        // 让「下单 → 制作中 → 已取餐」的完整流程在没有支付凭据时也能跑通。
        if (devMode.isDevMode()) {
            saved = repository.markPaid(saved.id(), DEV_PAY_CHANNEL,
                    PayDevModeProperties.DEV_TX_PREFIX + UUID.randomUUID(), created);
        }
        return saved;
    }

    /**
     * 报价。首单减免依赖「该会员是否已有订单」，归属以 token 判定，
     * 忽略请求体中的 memberId，避免被用来探测他人是否下过单。
     *
     * <p>报价<b>不核销优惠券</b>：只要不落单就不该消耗券。
     * 这里走的是同一套 {@link CouponService#resolveForOrder} 校验，
     * 所以「报价能减、下单却报错」这类不一致不会发生。
     */
    public PriceSummary quote(CreateOrderRequest request, String callerId) {
        String owner = normalizeMemberId(callerId);
        String level = repository.memberLevel(owner);
        // 报价不抛「不可用」：结算页要能拿着这个结论把开关置灰并显示原因，
        // 而不是让顾客一点报价就收到 409。真正的拒绝发生在下单（见 create）。
        UpsizeDecision upsize = resolveUpsize(owner, level, request);
        List<OrderItem> items = buildItems(request.items(), upsize);
        BigDecimal subtotal = subtotalOf(items);
        BigDecimal upsizeValue = upsize.effective() ? upsize.sizeDiff() : BigDecimal.ZERO;
        BigDecimal discountBase = subtotal.subtract(upsizeValue);
        Coupon coupon = coupons.resolveForOrder(owner, request.couponId(), discountBase);
        LocalDate today = LocalDate.now(ZONE);
        AppliedDiscount levelDiscount = levelDiscounts.evaluate(level, discountBase, today);
        return calculate(subtotal, upsize, items, owner, coupon, levelDiscount);
    }

    /**
     * 解析本次是否使用升杯。
     *
     * <p>只在顾客勾选时才去判定；没勾选返回 {@link UpsizeDecision#skipped()}，
     * 不去查库（会员页/游客下单不该为「没勾的功能」付一次查询）。
     */
    private UpsizeDecision resolveUpsize(String owner, String level, CreateOrderRequest request) {
        if (!request.useUpsize()) {
            return UpsizeDecision.skipped();
        }
        return upsizes.decide(owner, level, policy -> hasRowAt(request.items(), policy.fromSize()));
    }

    /**
     * 下单时对「勾了却不可用」的兜底拒绝。
     *
     * <p>前端在结算页就会按 {@code reason} 置灰，正常流程走不到这里；
     * 能走到这里说明状态在提交瞬间变了（额度被另一台设备用掉、积分被兑换掉）。
     * 这种情况下<b>报错比静默降级诚实</b>：顾客明明勾了升杯，却拿到一杯标准杯还付了原价，
     * 是最难解释的一种结果。
     */
    private void assertUpsizeUsable(UpsizeDecision upsize) {
        if (upsize.reason() == UpsizeUnavailableReason.NOT_REQUESTED || upsize.available()) {
            return;
        }
        throw new BusinessException(HttpStatus.CONFLICT, upsizeMessage(upsize.reason()));
    }

    /** 不可用原因 → 顾客能看懂的句子。文案集中在服务端，各端不必各写一份。 */
    private static String upsizeMessage(UpsizeUnavailableReason reason) {
        if (reason == null) {
            return "免费升杯当前不可用";
        }
        return switch (reason) {
            case INSUFFICIENT_POINTS -> "积分不足，无法用积分升杯；可取消升杯后按原杯型下单";
            case LIMIT_REACHED -> "本月升杯次数已用完";
            case NO_POINTS_COST -> "免费升杯次数已用完，当前等级不支持积分升杯";
            case DISABLED -> "免费升杯活动已暂停";
            case NO_ELIGIBLE_ITEM -> "本单已是最大杯型，无需升杯";
            case SIZE_MISSING -> "升杯杯型配置有误，请稍后再试";
            case NO_POLICY -> "当前等级暂未开放免费升杯";
            case NOT_REQUESTED -> "";
        };
    }

    /** 请求里是否有一行处在指定的起始杯型（空 size 走默认标准杯，与 buildItems 的兜底一致）。 */
    private static boolean hasRowAt(List<OrderItemRequest> items, String fromSize) {
        if (fromSize == null || fromSize.isBlank()) {
            return false;
        }
        return items.stream().anyMatch(item -> fromSize.equals(effectiveSize(item.size())));
    }

    /** 未填杯型时的默认值；报价 / 下单 / 前置判定三处必须共用，否则会出现「判了能升、构建时对不上」。 */
    private static String effectiveSize(String size) {
        return size == null || size.isBlank() ? CatalogService.STANDARD_SIZE : size;
    }

    /**
     * 完成订单：强制校验归属，非本人订单一律 403（不再允许 memberId 缺省来跳过校验）。
     *
     * <p>不加 {@code synchronized}：并发安全由 {@code ShopRepository.complete} 的
     * 条件更新（{@code WHERE status='PAID'}）保证，两个并发请求只有一个能命中，
     * 另一个拿到 0 行后按「已是 COMPLETED」幂等返回。原先的锁只是把不同顾客的
     * 取餐动作排到同一把锁上，还会被正在下单的请求挡住。
     */
    public Order complete(UUID id, String callerId) {
        Order existing = find(id);
        assertOwned(existing, callerId);
        return markCompleted(existing);
    }

    /**
     * 主动取消订单。
     *
     * <p><b>为什么必须有这条路径</b>：改造前只有超时任务能取消订单，意味着顾客下错单、
     * 支付未接入、或临时不想要了，唯一出路是<b>被动等待 35 分钟</b>超时
     * （制作 5 分钟 + 30 分钟缓冲）。那段时间里该会员被「仅一张进行中订单」
     * 的名额锁住，既不能重新下单，也不能确认取餐——形成一个体验极差的死等。
     * 提供主动取消让顾客立刻释放名额。
     *
     * <p><b>已付款的订单不能由前端直接取消</b>：钱已经到账，再取消等于让门店白做、
     * 顾客白花钱。此类订单必须走退款流程（{@code ShopRepository.markRefunded}），
     * 由店员介入，不给前端开这个口子。
     *
     * <p>不加 {@code synchronized}，理由同 {@link #complete}：真正的互斥在
     * {@code ShopRepository.cancel} 的条件更新上，重复取消会幂等返回当前状态。
     */
    public Order cancel(UUID id, String callerId) {
        Order existing = find(id);
        assertOwned(existing, callerId);

        if (existing.status() == OrderStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单已完成取餐，无法取消");
        }
        // 兼容已取消：重复点击直接返回当前状态，不报错
        if (existing.status() == OrderStatus.CANCELLED) {
            return existing;
        }
        if (existing.status().isPaid()) {
            // PAID 分支走到这里说明是已付款未取餐，属于需要人工对账的异常场景
            throw new BusinessException(HttpStatus.CONFLICT,
                    "订单已支付，请联系门店协助取消与退款");
        }
        existing.status().assertCanTransitionTo(OrderStatus.CANCELLED);
        Order cancelled = repository.cancel(existing.id(), OrderCancelReason.MANUAL, OffsetDateTime.now(ZONE));
        // 下单即核销的模型下，取消必须把券退回，否则顾客点一下取消就白丢 6 元，
        // 且账面上看不出任何痕迹。releaseForOrder 自带幂等与有效期重判。
        coupons.releaseForOrder(cancelled.id().toString());
        // 升杯同理：没喝到这杯咖啡，额度和积分都不该被扣。两次释放都由条件更新保证幂等。
        upsizes.releaseForOrder(cancelled.id().toString());
        return cancelled;
    }

    /** 归属校验：订单必须属于当前登录会员。 */
    private void assertOwned(Order order, String memberId) {
        if (!order.memberId().equals(normalizeMemberId(memberId))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该订单");
        }
    }

    /**
     * 完成取餐。
     *
     * <p>仅 {@code PAID} 可完成——「没付钱不能标记取餐」是状态机的核心不变式，
     * 防止漏收。已超时取消的订单给出明确提示，区分「钱没收到」与「钱收到了需退款」。
     */
    private Order markCompleted(Order existing) {
        if (existing.status() == OrderStatus.COMPLETED) {
            return existing;
        }
        if (existing.status() == OrderStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "TIMEOUT_UNPICKED".equals(existing.cancelReason())
                            ? "订单已超时取消，如已完成付款请联系门店协助退款"
                            : "订单已取消，无法完成取餐");
        }
        if (existing.status().isAwaitingPayment()) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单尚未支付完成，无法标记取餐");
        }
        existing.status().assertCanTransitionTo(OrderStatus.COMPLETED);
        return repository.complete(existing.id(), OffsetDateTime.now(ZONE));
    }

    /**
     * 构建订单行，并可把其中一行的杯型升到目标杯型。
     *
     * <p><b>为什么在这里改杯型，而不是在账单上加一行减免</b>：制作单读的是
     * {@code order_items.size_label}。只在账单减 6 元、库里仍写标准杯，店员会照标准杯做——
     * 顾客付了升杯的钱却拿到小杯。杯型必须是真实值，账单上的「免费升杯 −6」只是它的对账行。
     *
     * <p><b>一次只升一杯</b>：命中的行数量大于 1 时，把它拆成
     * 「数量-1 的原杯型 + 1 杯目标杯型」，而不是整行升。整行升会让差价变成 6×N，
     * 但额度只允许免一杯，多出来的部分没有价目依据。
     */
    private List<OrderItem> buildItems(List<OrderItemRequest> requests, UpsizeDecision upsize) {
        List<OrderItem> items = new ArrayList<>();
        boolean upsized = false;
        for (OrderItemRequest request : requests) {
            Product product = catalog.product(request.productId());
            String size = effectiveSize(request.size());
            String temperature = request.temperature() == null || request.temperature().isBlank() ? CatalogService.HOT : request.temperature();
            catalog.temperature(temperature);
            BigDecimal unitPrice = product.price().add(catalog.size(size).price());
            String milk;
            if (product.hasMilk()) {
                milk = request.milk() == null || request.milk().isBlank() ? "全脂奶" : request.milk();
                unitPrice = unitPrice.add(catalog.milk(milk).price());
            } else {
                milk = CatalogService.NO_MILK;
            }

            // 只升第一杯：额度按杯计算，一单多杯时升哪几杯会引入大量边界（见方案 3.2）
            boolean canUpsize = !upsized && upsize.effective() && upsize.fromSize().equals(size);
            if (canUpsize) {
                upsized = true;
                // 换杯型等价于「减去原杯型差价、加上目标杯型差价」，奶与温度不变
                BigDecimal upUnit = unitPrice.subtract(catalog.size(size).price())
                        .add(catalog.size(upsize.toSize()).price());
                int rest = request.quantity() - 1;
                if (rest > 0) {
                    items.add(new OrderItem(product.id(), product.name(), size, temperature, milk,
                            rest, unitPrice, unitPrice.multiply(BigDecimal.valueOf(rest))));
                }
                items.add(new OrderItem(product.id(), product.name(), upsize.toSize(), temperature, milk,
                        1, upUnit, upUnit));
                continue;
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
            items.add(new OrderItem(product.id(), product.name(), size, temperature, milk,
                    request.quantity(), unitPrice, lineTotal));
        }
        return items;
    }

    /**
     * 计算报价。
     *
     * <p><b>三档优惠的组合规则</b>（本方法唯一需要记住的事）：
     * <ol>
     *   <li><b>会员折扣 与 首单礼遇互斥</b>，取优惠金额更大的一边。两者都是「身份带来的让利」，
     *       叠加会让新客与老客的价差失去意义。新客通常是普通会员（默认不打折），
     *       所以实际冲突极少，但等级被手工调整过的新客会遇到，这里必须给出一致的结论。</li>
     *   <li><b>优惠券与会员折扣叠加</b>：券是营销工具（每周领、会过期、有门槛），
     *       与身份折扣是两回事。若也让它们互斥，8.8 折的钻石会员就永远用不上券，
     *       券功能对他等于不存在。券在<b>折后金额</b>上抵扣。</li>
     *   <li><b>优惠券与首单礼遇仍互斥</b>（取优者）：首单礼遇是无门槛、与券同面额的「见面礼」，
     *       它本来就是为「还没有券可用」的新客准备的。让它和券叠加，等于把它的定位抹掉，
     *       且新客首单会白拿双份（32 元单减 12 元）。</li>
     * </ol>
     *
     * <p>一句话概括：<b>身份权益（首单/折扣）只能拿一个；营销权益（券）能叠加在折扣上，
     * 但不能叠加在首单上</b>。
     *
     * <p>首单判定只需一个布尔值，走 {@code LIMIT 1} 探测（见 {@code ShopRepository#hasOrders}），
     * 不再把整段订单历史连同明细读回来。
     *
     * <p>返回的 {@code discounts} 是账单要逐行渲染的明细；{@code discountLabel} 只是
     * 主优惠名，为兼容旧调用方保留。
     */
    private PriceSummary calculate(BigDecimal subtotal, UpsizeDecision upsize, List<OrderItem> items,
                                   String owner, Coupon coupon, AppliedDiscount levelDiscount) {
        int count = items.stream().mapToInt(OrderItem::quantity).sum();
        BigDecimal upsizeValue = upsize.effective() ? upsize.sizeDiff() : BigDecimal.ZERO;
        // 折扣基数 = 商品金额 − 升杯差价。升杯是白送的实物，不该被再让利一次，
        // 也不该用来把券的门槛凑够（否则送 6 元等于送两次）。
        BigDecimal discountBase = subtotal.subtract(upsizeValue);
        BigDecimal firstOrder = repository.hasOrders(owner)
                ? BigDecimal.ZERO
                : FIRST_ORDER_DISCOUNT.min(discountBase);
        BigDecimal levelAmount = levelDiscount == null ? BigDecimal.ZERO : levelDiscount.amount();

        List<DiscountLine> lines = new ArrayList<>(3);
        BigDecimal baseDiscount = BigDecimal.ZERO;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        String appliedCouponId = null;
        String baseLabel = "";

        // 会员折扣胜过首单礼遇：走折扣，且券可以叠在折后金额上
        if (levelAmount.compareTo(firstOrder) > 0) {
            baseDiscount = levelAmount;
            baseLabel = levelDiscount.label();
            lines.add(DiscountLine.level(baseLabel, levelAmount));
            couponDiscount = coupon == null
                    ? BigDecimal.ZERO
                    : coupon.discountFor(discountBase.subtract(levelAmount));
        } else if (firstOrder.signum() > 0) {
            // 首单礼遇与券互斥：券在「原价」上比，谁省得多用谁
            BigDecimal onSubtotal = coupon == null ? BigDecimal.ZERO : coupon.discountFor(discountBase);
            if (onSubtotal.compareTo(firstOrder) > 0) {
                couponDiscount = onSubtotal;
            } else {
                baseDiscount = firstOrder;
                baseLabel = FIRST_ORDER_LABEL;
                lines.add(DiscountLine.firstOrder(baseLabel, firstOrder));
            }
        } else if (coupon != null) {
            couponDiscount = coupon.discountFor(discountBase);
        }

        if (couponDiscount.signum() > 0 && coupon != null) {
            appliedCouponId = coupon.id();
            lines.add(DiscountLine.coupon(coupon.title(), couponDiscount, coupon.id()));
        }

        // 升杯行放在最后：它是赠品的对账行，不影响前面任何一项的计算
        if (upsizeValue.signum() > 0) {
            lines.add(DiscountLine.upSize("免费升杯", upsizeValue, null));
        }

        BigDecimal totalDiscount = baseDiscount.add(couponDiscount).add(upsizeValue);
        String label = baseLabel.isEmpty()
                ? (appliedCouponId == null ? "" : coupon.title())
                : baseLabel;
        return new PriceSummary(subtotal, totalDiscount, PACKAGING_FEE,
                subtotal.subtract(totalDiscount).add(PACKAGING_FEE), count,
                label, appliedCouponId, couponDiscount, List.copyOf(lines), upsize);
    }

    /** 把升杯记录的 id 回填到账单行上（其余行原样返回）。 */
    private static List<DiscountLine> bindUpsizeRef(List<DiscountLine> lines, long recordId) {
        String ref = String.valueOf(recordId);
        return lines.stream()
                .map(line -> DiscountLine.TYPE_UP_SIZE.equals(line.type())
                        ? DiscountLine.upSize(line.label(), line.amount(), ref)
                        : line)
                .toList();
    }

    private static BigDecimal subtotalOf(List<OrderItem> items) {
        return items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalizeMemberId(String memberId) {
        return memberId == null || memberId.isBlank() ? GUEST : memberId;
    }
}
