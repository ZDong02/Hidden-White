package com.hiddenwhite.admin.operation.controller;

import com.hiddenwhite.admin.operation.domain.dto.HoursWriteRequest;
import com.hiddenwhite.admin.operation.domain.dto.StatusWriteRequest;
import com.hiddenwhite.admin.operation.domain.dto.TrafficAdjustRequest;
import com.hiddenwhite.admin.operation.service.OperationService;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.framework.audit.OperLog;
import com.hiddenwhite.framework.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 店铺运营接口：营业状态切换、每日运营时段配置、日流量统计查看与调整。
 * 页面入口见 sys_menu 中「店铺运营」目录，权限码前缀 operation:。
 */
@RestController
@RequestMapping("/api/admin/operation")
public class OperationController {

    private final OperationService service;

    public OperationController(OperationService service) {
        this.service = service;
    }

    // ---- 营业状态 ----

    @GetMapping("/status")
    @RequiresPermission("operation:status:list")
    public R<Map<String, Object>> status() {
        return R.success(service.status());
    }

    @PutMapping("/status")
    @RequiresPermission("operation:status:edit")
    @OperLog(module = "营业状态", action = "切换状态")
    public R<Map<String, Object>> switchStatus(@RequestBody StatusWriteRequest request) {
        return R.success(service.switchStatus(request));
    }

    // ---- 运营时间 ----

    @GetMapping("/hours")
    @RequiresPermission("operation:hours:list")
    public R<Map<String, Object>> hours() {
        return R.success(service.hours());
    }

    @PutMapping("/hours")
    @RequiresPermission("operation:hours:edit")
    @OperLog(module = "运营时间", action = "修改")
    public R<Void> updateHours(@RequestBody HoursWriteRequest request) {
        service.updateHours(request);
        return R.success(null);
    }

    // ---- 日流量 ----

    @GetMapping("/traffic/today")
    @RequiresPermission("operation:traffic:list")
    public R<Map<String, Object>> todayTraffic() {
        return R.success(service.todayTraffic());
    }

    @GetMapping("/traffic")
    @RequiresPermission("operation:traffic:list")
    public R<PageResult<Map<String, Object>>> traffic(
            PageQuery query,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return R.success(service.traffic(query, startDate, endDate));
    }

    @PutMapping("/traffic/{date}")
    @RequiresPermission("operation:traffic:edit")
    @OperLog(module = "日流统计", action = "调整")
    public R<Void> adjustTraffic(@PathVariable String date, @RequestBody TrafficAdjustRequest request) {
        service.adjustTraffic(date, request);
        return R.success(null);
    }
}
