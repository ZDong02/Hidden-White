package com.hiddenwhite.admin.redeem.service;

import com.hiddenwhite.admin.redeem.domain.dto.RedeemProductWriteRequest;
import com.hiddenwhite.admin.redeem.domain.dto.RedeemVerifyRequest;
import com.hiddenwhite.admin.redeem.mapper.RedeemMapper;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.shop.service.RedeemService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分运营服务：兑换品目录维护 + 到店核销。
 *
 * <p><b>为什么核销前要调 {@link RedeemService#settleExpired()}</b>：
 * 过期结算采用惰性策略（没有定时任务），若运营端直接核销一张已过期但尚未结算的凭证，
 * 就会出现「凭证作废了、积分也没退」的死角。核销与列表都先结算一次，
 * 保证运营看到与判定的状态就是结算后的真实状态。
 *
 * <p>结算逻辑只有 {@code RedeemService} 一处实现：管理端复用同一入口，
 * 避免两端各写一份阈值与退分逻辑，日后改规则漏掉一侧。
 */
@Service
public class RedeemAdminService {

    /** 与 RedemptionsStatus 的 ENUM 严格一致；不接受其它值。 */
    private static final List<String> STATUSES = List.of("PENDING", "REDEEMED", "CANCELLED", "EXPIRED");

    /** 兑换品 id 允许的形态：进 URL 路径，必须无空格无特殊字符。 */
    private static final String ID_PATTERN = "^[A-Za-z0-9_-]{2,40}$";

    private final RedeemMapper mapper;
    private final RedeemService redeemService;

    public RedeemAdminService(RedeemMapper mapper, RedeemService redeemService) {
        this.mapper = mapper;
        this.redeemService = redeemService;
    }

    // ---- 兑换品 ----

    public PageResult<Map<String, Object>> products(PageQuery query, String keyword, Boolean enabled) {
        long total = mapper.countProducts(blankToNull(keyword), enabled);
        long offset = (long) (query.normalizedPageNum() - 1) * query.normalizedPageSize();
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.pageProducts(blankToNull(keyword), enabled, offset, query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    public void createProduct(RedeemProductWriteRequest request) {
        String id = requireText(request.getId(), "兑换品 ID").trim();
        if (!id.matches(ID_PATTERN)) {
            throw new BusinessException("兑换品 ID 只能包含字母、数字、下划线与短横线，长度 2–40，如 rw-latte");
        }
        if (mapper.countProductId(id) > 0) {
            throw new BusinessException(409, "兑换品 ID 已存在: " + id);
        }
        validate(request);
        mapper.insertProduct(id, request.getName().trim(), trim(request.getSubtitle()),
                trim(request.getDescription()), trim(request.getImage()), request.getPointsCost(),
                request.getStock(), request.getPerMemberLimit(), request.getSortOrder(),
                !Boolean.FALSE.equals(request.getEnabled()));
    }

    public void updateProduct(String id, RedeemProductWriteRequest request) {
        validate(request);
        int rows = mapper.updateProduct(id, request.getName().trim(), trim(request.getSubtitle()),
                trim(request.getDescription()), trim(request.getImage()), request.getPointsCost(),
                request.getStock(), request.getPerMemberLimit(), request.getSortOrder(),
                !Boolean.FALSE.equals(request.getEnabled()));
        if (rows == 0) throw new BusinessException(404, "兑换品不存在: " + id);
    }

    /** 上下架：独立端点，不复用整行更新（同商品管理的约定）。 */
    public void setProductEnabled(String id, Boolean enabled) {
        if (enabled == null) throw new BusinessException("请指定上下架状态");
        int rows = mapper.updateProductEnabled(id, enabled);
        if (rows == 0) throw new BusinessException(404, "兑换品不存在: " + id);
    }

    /**
     * 删除兑换品。
     *
     * <p>被兑换单引用时拒绝物理删除（409）而不是像商品那样自动下架：
     * 兑换单是顾客手里的核销凭证，商品行被删会让历史凭证的外键断掉、
     * 兑换记录失去指向。这里直接告诉运营「改成下架」，语义更明确。
     */
    public void deleteProduct(String id) {
        if (mapper.countRedemptionsByProduct(id) > 0) {
            throw new BusinessException(409, "该兑换品已有兑换记录，无法删除；如不再提供请改为下架");
        }
        int rows = mapper.deleteProduct(id);
        if (rows == 0) throw new BusinessException(404, "兑换品不存在: " + id);
    }

    // ---- 兑换记录 ----

    /** 兑换记录（分页）。查询前先做一次惰性过期结算，避免列表里混着早已作废的「待核销」。 */
    @Transactional
    public PageResult<Map<String, Object>> orders(PageQuery query, String keyword, String status) {
        redeemService.settleExpired();
        String statusValue = normalizeStatus(status);
        long total = mapper.countRedemptions(blankToNull(keyword), statusValue);
        long offset = (long) (query.normalizedPageNum() - 1) * query.normalizedPageSize();
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : mapper.pageRedemptions(blankToNull(keyword), statusValue, offset, query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /**
     * 按核销码核销。
     *
     * @return 核销结果（单号 / 商品 / 扣分 / 会员昵称），供前端直接提示
     */
    @Transactional
    public Map<String, Object> verify(RedeemVerifyRequest request) {
        String code = request == null ? null : trim(request.getCode());
        if (!code.matches("^\\d{8}$")) {
            throw new BusinessException("请输入 8 位数字核销码");
        }
        // 先结算：过期待核销的凭证必须先退回积分，再谈能不能核销
        redeemService.settleExpired();

        Map<String, Object> found = mapper.findByCode(code);
        if (found == null) {
            throw new BusinessException(404, "核销码不存在，请与顾客核对");
        }
        ensureVerifiable(found);

        int rows = mapper.verifyByCode(code, currentOperator());
        if (rows == 0) {
            // 并发下刚被别的店员核销 / 恰好过期：重读一次给出准确原因
            ensureVerifiable(mapper.findByCode(code));
            throw new BusinessException(409, "核销失败，请重试");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("redeemNo", found.get("redeemNo"));
        result.put("productName", found.get("productName"));
        result.put("pointsCost", found.get("pointsCost"));
        return result;
    }

    /** 状态判定：把「不能核销」的四种情形讲清楚，店员才知道下一步该做什么。 */
    private void ensureVerifiable(Map<String, Object> row) {
        if (row == null) {
            throw new BusinessException(404, "核销码不存在，请与顾客核对");
        }
        String status = String.valueOf(row.get("status"));
        if ("REDEEMED".equals(status)) {
            throw new BusinessException(409, "该核销码已于 " + row.get("verifiedAt") + " 核销过，请勿重复核销");
        }
        if ("CANCELLED".equals(status)) {
            throw new BusinessException(409, "该兑换单已被会员取消，积分已退回");
        }
        if ("EXPIRED".equals(status) || Boolean.TRUE.equals(toBoolean(row.get("expired")))) {
            throw new BusinessException(409, "该兑换单已过期，积分已退回会员");
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.FALSE;
    }

    /** 当前操作人：管理端 JWT 的 principal 就是 sys_user.username。 */
    private static String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication == null ? null : String.valueOf(authentication.getPrincipal());
        return principal == null || principal.isBlank() ? "system" : principal;
    }

    // ---- 私有辅助 ----

    private void validate(RedeemProductWriteRequest request) {
        requireText(request.getName(), "兑换品名称");
        if (request.getPointsCost() == null || request.getPointsCost() < 1) {
            throw new BusinessException("兑换所需积分必须大于 0");
        }
        if (request.getStock() != null && request.getStock() < 0) {
            throw new BusinessException("库存不能为负数；不限量请留空");
        }
        if (request.getPerMemberLimit() == null) request.setPerMemberLimit(0);
        if (request.getPerMemberLimit() < 0) {
            throw new BusinessException("每人限兑次数不能为负数；不限请填 0");
        }
        if (request.getSortOrder() == null) request.setSortOrder(0);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new BusinessException(field + "不能为空");
        return value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        if (!STATUSES.contains(status)) {
            throw new BusinessException("兑换单状态只能是 " + String.join(" / ", STATUSES));
        }
        return status;
    }
}
