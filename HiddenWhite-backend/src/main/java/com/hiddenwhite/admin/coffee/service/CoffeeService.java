package com.hiddenwhite.admin.coffee.service;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.admin.coffee.domain.dto.BenefitWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.MemberWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.OptionWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.ProductWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.StoreWriteRequest;
import com.hiddenwhite.admin.coffee.mapper.CoffeeMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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

    public CoffeeService(CoffeeMapper mapper) {
        this.mapper = mapper;
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

    public void updateMember(String id, MemberWriteRequest request) {
        requireText(request.getDisplayName(), "会员昵称");
        requireText(request.getLevel(), "会员等级");
        if (request.getPoints() == null || request.getPoints() < 0) throw new BusinessException("积分不能为负数");
        if (request.getCoupons() == null || request.getCoupons() < 0) throw new BusinessException("优惠券不能为负数");
        if (request.getGrowthValue() == null || request.getGrowthValue() < 0) throw new BusinessException("成长值不能为负数");
        int rows = mapper.updateMember(id, request.getDisplayName().trim(), request.getLevel().trim(),
                request.getPoints(), request.getCoupons(), request.getGrowthValue());
        if (rows == 0) throw new BusinessException(404, "会员不存在: " + id);
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
