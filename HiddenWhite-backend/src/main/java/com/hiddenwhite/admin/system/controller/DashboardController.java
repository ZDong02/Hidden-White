package com.hiddenwhite.admin.system.controller;

import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.admin.system.service.DashboardService;
import com.hiddenwhite.framework.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /**
     * 工作台概览。
     *
     * <p>数据范围较广（用户数、角色数、在线用户、API 调用量等），
     * 因此必须校验权限码，不能只靠「已登录」放行。
     */
    @GetMapping("/summary")
    @RequiresPermission("system:dashboard:list")
    public R<Map<String, Object>> summary(@RequestParam(required = false, defaultValue = "24h") String range) {
        return R.success(service.summary(range));
    }
}
