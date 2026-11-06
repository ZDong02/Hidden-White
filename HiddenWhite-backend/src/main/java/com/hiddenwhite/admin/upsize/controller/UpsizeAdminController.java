package com.hiddenwhite.admin.upsize.controller;

import com.hiddenwhite.admin.upsize.domain.dto.UpsizePolicyWriteRequest;
import com.hiddenwhite.admin.upsize.service.UpsizeAdminService;
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

import java.util.Map;

/**
 * 免费升杯运营接口。
 *
 * <p>页面入口见 sys_menu「免费升杯」（411，挂在咖啡运营下），权限码前缀
 * {@code coffee:upsize:}。额度与积分价改动会直接影响顾客能白拿几杯，
 * 写入必须记操作日志。
 */
@RestController
@RequestMapping("/api/admin/upsize")
public class UpsizeAdminController {

    private final UpsizeAdminService service;

    public UpsizeAdminController(UpsizeAdminService service) {
        this.service = service;
    }

    /** 五档策略 + 本月各档使用量 + 可选杯型。 */
    @GetMapping("/policies")
    @RequiresPermission("coffee:upsize:list")
    public R<Map<String, Object>> policies() {
        return R.success(service.overview());
    }

    /** 保存一个等级的策略（未传的字段保留原值）。 */
    @PutMapping("/policies")
    @RequiresPermission("coffee:upsize:edit")
    @OperLog(module = "免费升杯", action = "修改升杯策略")
    public R<Void> save(@RequestBody UpsizePolicyWriteRequest request) {
        service.save(request);
        return R.success(null);
    }

    /** 使用记录：谁、哪一单、免费还是扣分、是否已随订单取消释放。 */
    @GetMapping("/records")
    @RequiresPermission("coffee:upsize:records")
    public R<PageResult<Map<String, Object>>> records(
            PageQuery query,
            @RequestParam(required = false) String monthKey,
            @RequestParam(required = false) String payType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.success(service.records(query, monthKey, payType, status, keyword));
    }
}
