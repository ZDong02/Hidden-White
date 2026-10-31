package com.hiddenwhite.admin.coffee.service;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.common.points.PointsBizType;
import com.hiddenwhite.common.points.PointsLedger;
import com.hiddenwhite.common.points.PointsRefType;
import com.hiddenwhite.admin.coffee.domain.dto.BenefitWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.MemberWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.OptionWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.ProductWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.RecommendWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.StoreWriteRequest;
import com.hiddenwhite.admin.coffee.mapper.CoffeeMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 咖啡业务服务：管理端对商品、规格选项、订单、会员、权益、门店的增删改查。
 * 数据落在 hidden_white 库，点单端（小程序 / H5）下一次请求即读到最新数据。
 */
@Service
public class CoffeeService {

    /** 游客账号由点单端下单流程自动创建，属于系统内置账号。 */
    private static final String PROTECTED_MEMBER_ID = "guest";

    private final CoffeeMapper mapper;
    private final PointsLedger pointsLedger;

    public CoffeeService(CoffeeMapper mapper, PointsLedger pointsLedger) {
        this.mapper = mapper;
        this.pointsLedger = pointsLedger;
    }

    // ---- 概览 ----

    public Map<String, Object> summary() {
        return Map.of(
                "productCount", mapper.countProducts(null, null, null),
                "memberCount", mapper.countMembers(),
                "orderCount", mapper.countAllOrders(),
                "inProgressCount", mapper.countInProgressOrders()
        );
    }

    // ---- 商品 ----

    public PageResult<Map<String, Object>> products(PageQuery query, String keyword, Integer category, Boolean enabled) {
        long total = mapper.countProducts(blankToNull(keyword), category, enabled);
        long offset = (long) (query.normalizedPageNum() - 1) * query.normalizedPageSize();
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.pageProducts(blankToNull(keyword), category, enabled, offset, query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    public Long createProduct(ProductWriteRequest request) {
        requireText(request.getId(), "商品 ID");
        requireText(request.getName(), "商品名称");
        requirePrice(request.getPrice());
        if (request.getCategory() == null) throw new BusinessException("请选择商品分类");
        if (mapper.countProductId(request.getId().trim()) > 0) {
            throw new BusinessException(409, "商品 ID 已存在: " + request.getId().trim());
        }
        mapper.insertProduct(request.getId().trim(), request.getName().trim(), trim(request.getSubtitle()),
                trim(request.getDescription()), trim(request.getDetailDescription()), request.getPrice(),
                trim(request.getImage()), trim(request.getDetailImage()), request.getCategory(),
                Boolean.TRUE.equals(request.getHasMilk()), !Boolean.FALSE.equals(request.getEnabled()));
        return null;
    }

    public void updateProduct(String id, ProductWriteRequest request) {
        requireText(request.getName(), "商品名称");
        requirePrice(request.getPrice());
        if (request.getCategory() == null) throw new BusinessException("请选择商品分类");
        int rows = mapper.updateProduct(id, request.getName().trim(), trim(request.getSubtitle()),
                trim(request.getDescription()), trim(request.getDetailDescription()), request.getPrice(),
                trim(request.getImage()), trim(request.getDetailImage()), request.getCategory(),
                Boolean.TRUE.equals(request.getHasMilk()), !Boolean.FALSE.equals(request.getEnabled()));
        if (rows == 0) throw new BusinessException(404, "商品不存在: " + id);
    }

    /**
     * 仅切换上下架状态。
     *
     * <p>不走 {@link #updateProduct}：那条路径是整行更新，要求名称 / 价格 / 分类齐全，
     * 列表页的一键上下架只带一个 enabled 字段，过去因缺必填项被拒（商品下不掉），
     * 即便放行也会把未传列覆盖成空。
     */
    public void setProductEnabled(String id, Boolean enabled) {
        if (enabled == null) throw new BusinessException("请指定上下架状态");
        int rows = mapper.updateProductEnabled(id, enabled);
        if (rows == 0) throw new BusinessException(404, "商品不存在: " + id);
    }

    /** 返回提示文案：商品被订单引用时软下架，否则物理删除返回 null。 */
    public String deleteProduct(String id) {
        if (mapper.countProductOrders(id) > 0) {
            mapper.disableProduct(id);
            return "该商品存在订单记录，已改为下架";
        }
        int rows = mapper.deleteProduct(id);
        if (rows == 0) throw new BusinessException(404, "商品不存在: " + id);
        return null;
    }

    // ---- 规格选项 ----

    public List<Map<String, Object>> options() {
        return mapper.listOptions();
    }

    public Long createOption(OptionWriteRequest request) {
        validateOption(request);
        if (mapper.countOption(request.getType(), request.getLabel().trim()) > 0) {
            throw new BusinessException(409, "同类型下已存在同名选项: " + request.getLabel().trim());
        }
        mapper.insertOption(request.getType(), request.getLabel().trim(), request.getExtraPrice(),
                request.getSortOrder(), !Boolean.FALSE.equals(request.getEnabled()));
        return mapper.findOptionId(request.getType(), request.getLabel().trim());
    }

    public void updateOption(long id, OptionWriteRequest request) {
        validateOption(request);
        int rows = mapper.updateOption(id, request.getType(), request.getLabel().trim(), request.getExtraPrice(),
                request.getSortOrder(), !Boolean.FALSE.equals(request.getEnabled()));
        if (rows == 0) throw new BusinessException(404, "规格选项不存在: " + id);
    }

    public void deleteOption(long id) {
        int rows = mapper.deleteOption(id);
        if (rows == 0) throw new BusinessException(404, "规格选项不存在: " + id);
    }

    // ---- 订单 ----

    public PageResult<Map<String, Object>> orders(PageQuery query, String keyword, String status) {
        String statusValue = normalizeStatus(status);
        long total = mapper.countOrders(blankToNull(keyword), statusValue);
        long offset = (long) (query.normalizedPageNum() - 1) * query.normalizedPageSize();
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.pageOrders(blankToNull(keyword), statusValue, offset, query.normalizedPageSize());
        attachItems(rows);
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /**
     * 给本页订单批量补齐明细。
     *
     * <p>改成一次 {@code IN} 查询后，翻页的 SQL 往返从 {@code 1 + 页大小} 降到 2 次。
     * 明细里的 {@code orderId} 只用于归位，下发前摘掉，保持前端拿到的结构不变。
     */
    private void attachItems(List<Map<String, Object>> orders) {
        if (orders.isEmpty()) {
            return;
        }
        List<String> orderIds = orders.stream().map(row -> String.valueOf(row.get("id"))).toList();
        Map<String, List<Map<String, Object>>> itemsByOrder = new HashMap<>();
        for (Map<String, Object> item : mapper.orderItemsByOrderIds(orderIds)) {
            Map<String, Object> payload = new LinkedHashMap<>(item);
            String orderId = String.valueOf(payload.remove("orderId"));
            itemsByOrder.computeIfAbsent(orderId, key -> new ArrayList<>()).add(payload);
        }
        orders.forEach(row -> row.put("items",
                itemsByOrder.getOrDefault(String.valueOf(row.get("id")), List.of())));
    }

    public void completeOrder(String id) {
        int rows = mapper.completeOrder(id);
        // 条件更新为 0 有三种可能：订单不存在、尚未支付、已被取消。
        // 区分三者便于店员判断该催顾客付款、查是否已取餐，还是报系统问题。
        if (rows == 0) {
            String status = mapper.orderStatus(id);
            if (status == null) {
                throw new BusinessException(404, "订单不存在: " + id);
            }
            if ("PENDING_PAYMENT".equals(status) || "CREATED".equals(status)) {
                throw new BusinessException(409, "订单尚未支付完成，无法标记取餐");
            }
            if ("CANCELLED".equals(status)) {
                throw new BusinessException(409, "订单已取消，无法标记完成；如已收款请联系顾客处理退款");
            }
            throw new BusinessException(409, "订单当前状态为 " + status + "，无法标记完成");
        }
    }

    public void deleteOrder(String id) {
        try {
            int rows = mapper.deleteOrder(id);
            if (rows == 0) throw new BusinessException(404, "订单不存在: " + id);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(409, "订单存在明细记录，无法删除");
        }
    }

    // ---- 会员 ----

    public PageResult<Map<String, Object>> members(PageQuery query, String keyword) {
        long total = mapper.countMembersByKeyword(blankToNull(keyword));
        long offset = (long) (query.normalizedPageNum() - 1) * query.normalizedPageSize();
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.pageMembers(blankToNull(keyword), offset, query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /**
     * 编辑会员资料。
     *
     * <p>表单仍可改积分（运营侧的历史习惯），但积分不再直接写库：
     * 先读出当前值算出差值，资料更新后再把差值交给 {@code PointsLedger}
     * 记一条 {@code ADMIN_ADJUST} 流水。这样「管理员改分」与「支付发放 / 兑换扣除」
     * 走的是同一条账本，对账时不存在无法解释的余额跳变。
     *
     * <p>整段加事务：资料写成功而账本失败（例如并发下余额已非预期值）时整体回滚，
     * 不会出现「改了昵称但积分没动」或反过来的半更新状态。
     */
    @Transactional
    public void updateMember(String id, MemberWriteRequest request) {
        requireText(request.getDisplayName(), "会员昵称");
        requireText(request.getLevel(), "会员等级");
        if (request.getPoints() == null || request.getPoints() < 0) throw new BusinessException("积分不能为负数");
        if (request.getCoupons() == null || request.getCoupons() < 0) throw new BusinessException("优惠券不能为负数");
        if (request.getGrowthValue() == null || request.getGrowthValue() < 0) throw new BusinessException("成长值不能为负数");

        Integer currentPoints = mapper.memberPoints(id);
        if (currentPoints == null) throw new BusinessException(404, "会员不存在: " + id);

        int delta = request.getPoints() - currentPoints;
        int rows = mapper.updateMember(id, request.getDisplayName().trim(), request.getLevel().trim(),
                request.getCoupons(), request.getGrowthValue());
        if (rows == 0) throw new BusinessException(404, "会员不存在: " + id);
        if (delta != 0) {
            pointsLedger.adjust(id, delta, PointsBizType.ADMIN_ADJUST, PointsRefType.ADMIN, id,
                    "管理端调整积分 " + currentPoints + " → " + request.getPoints());
        }
    }

    public void deleteMember(String id) {
        if (PROTECTED_MEMBER_ID.equals(id)) {
            throw new BusinessException(409, "游客账号是点单端系统内置账号，不能删除");
        }
        try {
            int rows = mapper.deleteMember(id);
            if (rows == 0) throw new BusinessException(404, "会员不存在: " + id);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(409, "该会员存在订单记录，无法删除");
        }
    }

    // ---- 权益 ----

    public List<Map<String, Object>> benefits() {
        return mapper.listBenefits();
    }

    public Long createBenefit(BenefitWriteRequest request) {
        validateBenefit(request);
        if (mapper.countBenefitTitle(request.getTitle().trim()) > 0) {
            throw new BusinessException(409, "同名权益已存在: " + request.getTitle().trim());
        }
        mapper.insertBenefit(request.getIcon().trim(), request.getTitle().trim(), trim(request.getDescription()),
                request.getSortOrder(), !Boolean.FALSE.equals(request.getEnabled()));
        return mapper.findBenefitId(request.getTitle().trim());
    }

    public void updateBenefit(long id, BenefitWriteRequest request) {
        validateBenefit(request);
        int rows = mapper.updateBenefit(id, request.getIcon().trim(), request.getTitle().trim(),
                trim(request.getDescription()), request.getSortOrder(), !Boolean.FALSE.equals(request.getEnabled()));
        if (rows == 0) throw new BusinessException(404, "权益不存在: " + id);
    }

    public void deleteBenefit(long id) {
        int rows = mapper.deleteBenefit(id);
        if (rows == 0) throw new BusinessException(404, "权益不存在: " + id);
    }

    // ---- 首页推荐位 ----

    public List<Map<String, Object>> recommendations() {
        return mapper.listRecommendations();
    }

    public List<Map<String, Object>> recommendCandidates() {
        return mapper.recommendCandidates();
    }

    public void createRecommend(RecommendWriteRequest request) {
        validateRecommend(request, null);
        mapper.insertRecommend(request.getProductId().trim(), trim(request.getEyebrow()),
                trim(request.getHeadline()), trim(request.getCaption()), trim(request.getLinkText()),
                request.getSortOrder(), !Boolean.FALSE.equals(request.getEnabled()));
    }

    public void updateRecommend(long id, RecommendWriteRequest request) {
        validateRecommend(request, id);
        int rows = mapper.updateRecommend(id, request.getProductId().trim(), trim(request.getEyebrow()),
                trim(request.getHeadline()), trim(request.getCaption()), trim(request.getLinkText()),
                request.getSortOrder(), !Boolean.FALSE.equals(request.getEnabled()));
        if (rows == 0) throw new BusinessException(404, "推荐位不存在: " + id);
    }

    /**
     * 仅切换推荐位开关。
     *
     * <p>与商品上下架同一条口径：整行更新要求商品与文案齐全，
     * 列表页的开关只带一个布尔位，必须走专用方法，否则会把未传列写成空串。
     */
    public void setRecommendEnabled(long id, Boolean enabled) {
        if (enabled == null) throw new BusinessException("请指定启用状态");
        int rows = mapper.updateRecommendEnabled(id, enabled);
        if (rows == 0) throw new BusinessException(404, "推荐位不存在: " + id);
    }

    public void deleteRecommend(long id) {
        int rows = mapper.deleteRecommend(id);
        if (rows == 0) throw new BusinessException(404, "推荐位不存在: " + id);
    }

    /**
     * 推荐位入参校验。
     *
     * <p>三道关都是「写库前必须挡掉」的类型：
     * <ol>
     *   <li>商品必须存在且在售 —— 推荐一个已下架的商品，点单端会直接少一屏，
     *       运营却以为已经挂上去了；</li>
     *   <li>同一商品不能占两个推荐位（表上有唯一键，先查出友好提示，
     *       不让用户看到 500）；</li>
     *   <li>文案长度按列宽上界校验 —— MySQL 严格模式下超长会抛 1406，
     *       表现为笼统的「保存失败」，运营完全不知道自己哪个字写多了。</li>
     * </ol>
     *
     * @param excludeId 编辑时排除自身，新增传 null
     */
    private void validateRecommend(RecommendWriteRequest request, Long excludeId) {
        requireText(request.getProductId(), "推荐商品");
        String productId = request.getProductId().trim();
        Boolean productEnabled = mapper.productEnabled(productId);
        if (productEnabled == null) throw new BusinessException(404, "商品不存在: " + productId);
        if (!productEnabled) throw new BusinessException(409, "商品已下架，请先在商品管理里上架: " + productId);
        if (mapper.countRecommendProduct(productId, excludeId) > 0) {
            throw new BusinessException(409, "该商品已在推荐位中，如需换文案请直接编辑");
        }
        requireMaxLength(request.getEyebrow(), 40, "眉标");
        requireMaxLength(request.getHeadline(), 40, "主标题");
        requireMaxLength(request.getCaption(), 60, "短句");
        requireMaxLength(request.getLinkText(), 40, "链接文案");
        if (request.getSortOrder() == null) request.setSortOrder(0);
    }

    // ---- 门店 ----

    public Map<String, Object> store() {
        Map<String, Object> store = mapper.store();
        if (store == null) throw new BusinessException(404, "门店信息不存在");
        return store;
    }

    public void updateStore(StoreWriteRequest request) {
        requireText(request.getName(), "门店名称");
        requireText(request.getAddress(), "门店地址");
        if (request.getLatitude() == null || request.getLongitude() == null) throw new BusinessException("请填写门店坐标");
        if (request.getOpeningTime() == null || !request.getOpeningTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$")
                || request.getClosingTime() == null || !request.getClosingTime().matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new BusinessException("营业时间格式为 HH:mm");
        }
        int rows = mapper.updateStore(request.getName().trim(), request.getAddress().trim(),
                trim(request.getShortAddress()), request.getLatitude(), request.getLongitude(),
                trim(request.getDistance()), request.getOpeningTime(), request.getClosingTime());
        if (rows == 0) throw new BusinessException(404, "门店信息不存在");
    }

    // ---- 私有辅助 ----

    private void validateOption(OptionWriteRequest request) {
        requireText(request.getLabel(), "选项名称");
        if (request.getType() == null || !List.of("SIZE", "MILK", "TEMPERATURE").contains(request.getType())) {
            throw new BusinessException("选项类型必须是 SIZE / MILK / TEMPERATURE");
        }
        requirePrice(request.getExtraPrice());
        if (request.getSortOrder() == null) request.setSortOrder(0);
    }

    private void validateBenefit(BenefitWriteRequest request) {
        requireText(request.getIcon(), "权益图标");
        requireText(request.getTitle(), "权益标题");
        requireText(request.getDescription(), "权益描述");
        if (request.getSortOrder() == null) request.setSortOrder(0);
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new BusinessException(field + "不能为空");
    }

    /** 长度上界与列宽一致：超长在 MySQL 严格模式下会抛 1406，转成可读的字段级提示。 */
    private void requireMaxLength(String value, int max, String field) {
        if (value != null && value.trim().length() > max) {
            throw new BusinessException(field + "最多 " + max + " 个字，当前 " + value.trim().length() + " 个字");
        }
    }

    private void requirePrice(BigDecimal price) {
        if (price == null || price.signum() < 0) throw new BusinessException("价格不能为空且不能为负数");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        // 与后端 OrderStatus 五态严格一致；不接受已废弃的 IN_PROGRESS
        if (!List.of("CREATED", "PENDING_PAYMENT", "PAID", "COMPLETED", "CANCELLED").contains(status)) {
            throw new BusinessException("订单状态只能是 CREATED / PENDING_PAYMENT / PAID / COMPLETED / CANCELLED");
        }
        return status;
    }
}
