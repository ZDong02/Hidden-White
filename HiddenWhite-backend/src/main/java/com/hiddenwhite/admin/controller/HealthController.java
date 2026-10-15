package com.hiddenwhite.admin.controller;

import com.hiddenwhite.common.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    /** 健康检查：/api/health 与 /api/admin/health（管理前端 baseUrl 为 /api/admin，登录页探活走后者）。 */
    @GetMapping({"/health", "/admin/health"})
    public R<Map<String, Object>> health() {
        return R.success(Map.of(
                "status", "UP",
                "time", OffsetDateTime.now().toString()
        ));
    }
}
