package com.hiddenwhite.admin.coupon.controller;

import com.hiddenwhite.admin.coupon.domain.dto.CouponConfigWriteRequest;
import com.hiddenwhite.admin.coupon.service.CouponAdminService;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.framework.audit.OperLog;
import com.hiddenwhite.framework.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 优惠券运营接口。
 *
 * <p>页面入口见 sys_menu「优惠券」（409，挂在咖啡运营下），
 * 权限码前缀 {@code coffee:coupon:}。配置写入记操作日志 ——
 * 改一次面额就影响全体会员接下来能省多少钱，这类操作必须留痕。
 */
@RestController
@RequestMapping("/api/admin/coupons")
public class CouponAdminController {

    private final CouponAdminService service;

    public CouponAdminController(CouponAdminService service) {
        this.service = service;
    }

    /** 配置 + 周期概览。 */
    @GetMapping("/config")
    @RequiresPermission("coffee:coupon:list")
    public R<Map<String, Object>> config() {
        return R.success(service.overview());
    }

    @PutMapping("/config")
    @RequiresPermission("coffee:coupon:edit")
    @OperLog(module = "优惠券", action = "修改券配置")
    public R<Void> updateConfig(@RequestBody CouponConfigWriteRequest request) {
        service.updateConfig(request);
        return R.success(null);
    }

    /** 发放与使用记录（按状态 / 归属周 / 会员关键字过滤）。 */
    @GetMapping("/records")
    @RequiresPermission("coffee:coupon:records")
    public R<PageResult<Map<String, Object>>> records(PageQuery query,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String weekKey,
                                                      @RequestParam(required = false) String keyword) {
        return R.success(service.records(query, status, weekKey, keyword));
    }

    /** 最近 12 个自然周，供筛选下拉。 */
    @GetMapping("/weeks")
    @RequiresPermission("coffee:coupon:list")
    public R<List<Map<String, Object>>> weeks() {
        return R.success(service.recentWeeks());
    }
}
