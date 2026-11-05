package com.hiddenwhite.admin.leveldiscount.controller;

import com.hiddenwhite.admin.leveldiscount.domain.dto.LevelDiscountWriteRequest;
import com.hiddenwhite.admin.leveldiscount.service.LevelDiscountAdminService;
import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.framework.audit.OperLog;
import com.hiddenwhite.framework.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 会员折扣运营接口。
 *
 * <p>页面入口见 sys_menu「会员折扣」（410，挂在咖啡运营下），权限码前缀
 * {@code coffee:leveldiscount:}。折扣率改一位小数就影响该等级全体会员的每一单，
 * 写入必须记操作日志。
 *
 * <p><b>为什么没有路径参数</b>：等级键是中文（「白银会员」），放进 URL 要依赖
 * 各层对编码的一致处理；改为请求体传 {@code levelKey}，少一类只在中文环境下复现的问题。
 */
@RestController
@RequestMapping("/api/admin/level-discounts")
public class LevelDiscountAdminController {

    private final LevelDiscountAdminService service;

    public LevelDiscountAdminController(LevelDiscountAdminService service) {
        this.service = service;
    }

    /** 折扣总览：每个等级的策略 + 成员数 + 孤儿等级提示。 */
    @GetMapping
    @RequiresPermission("coffee:leveldiscount:list")
    public R<Map<String, Object>> overview() {
        return R.success(service.overview());
    }

    /** 整行保存一个等级的折扣策略（含每日覆盖）。 */
    @PutMapping
    @RequiresPermission("coffee:leveldiscount:edit")
    @OperLog(module = "会员折扣", action = "修改等级折扣")
    public R<Void> save(@RequestBody LevelDiscountWriteRequest request) {
        service.save(request);
        return R.success(null);
    }

    /** 单独启停一个等级（不复用整行更新）。 */
    @PutMapping("/enabled")
    @RequiresPermission("coffee:leveldiscount:edit")
    @OperLog(module = "会员折扣", action = "启停等级折扣")
    public R<Void> setEnabled(@RequestBody LevelDiscountWriteRequest request) {
        service.setEnabled(request.levelKey(), request.enabled());
        return R.success(null);
    }
}
