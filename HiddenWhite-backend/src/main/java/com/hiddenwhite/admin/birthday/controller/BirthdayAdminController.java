package com.hiddenwhite.admin.birthday.controller;

import com.hiddenwhite.admin.birthday.domain.dto.BirthdayConfigWriteRequest;
import com.hiddenwhite.admin.birthday.service.BirthdayAdminService;
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
 * 生日礼遇运营接口。
 *
 * <p>页面入口见 sys_menu「生日礼遇」（408，挂在咖啡运营下），
 * 权限码前缀 {@code coffee:birthday:}。配置写入记操作日志 ——
 * 改一个数字就影响所有会员下个月能拿多少积分，这类操作必须留痕。
 */
@RestController
@RequestMapping("/api/admin/birthday")
public class BirthdayAdminController {

    private final BirthdayAdminService service;

    public BirthdayAdminController(BirthdayAdminService service) {
        this.service = service;
    }

    /** 配置 + 年度概览。 */
    @GetMapping("/config")
    @RequiresPermission("coffee:birthday:list")
    public R<Map<String, Object>> config() {
        return R.success(service.overview());
    }

    @PutMapping("/config")
    @RequiresPermission("coffee:birthday:edit")
    @OperLog(module = "生日礼遇", action = "修改礼遇配置")
    public R<Void> updateConfig(@RequestBody BirthdayConfigWriteRequest request) {
        service.updateConfig(request);
        return R.success(null);
    }

    /** 领取记录（按年份 + 会员关键字）。 */
    @GetMapping("/gifts")
    @RequiresPermission("coffee:birthday:gifts")
    public R<PageResult<Map<String, Object>>> gifts(PageQuery query,
                                                    @RequestParam(required = false) Integer year,
                                                    @RequestParam(required = false) String keyword) {
        return R.success(service.gifts(query, year, keyword));
    }
}
