package com.hiddenwhite.admin.coffee.controller;

import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.framework.audit.OperLog;
import com.hiddenwhite.framework.security.RequiresPermission;
import com.hiddenwhite.admin.coffee.domain.dto.BenefitWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.MemberWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.OptionWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.ProductEnabledRequest;
import com.hiddenwhite.admin.coffee.domain.dto.ProductWriteRequest;
import com.hiddenwhite.admin.coffee.domain.dto.StoreWriteRequest;
import com.hiddenwhite.admin.coffee.service.CoffeeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 咖啡业务管理接口：直接读写点单端使用的 hidden_white 库。
 * 页面入口见 sys_menu 中「咖啡运营」目录，权限码前缀 coffee:。
 */
@RestController
@RequestMapping("/api/admin/coffee")
public class CoffeeController {

    private final CoffeeService service;

    public CoffeeController(CoffeeService service) {
        this.service = service;
    }

    // ---- 概览 ----

    @GetMapping("/summary")
    @RequiresPermission("coffee:product:list")
    public R<Map<String, Object>> summary() {
        return R.success(service.summary());
    }

    // ---- 商品 ----

    @GetMapping("/products")
    @RequiresPermission("coffee:product:list")
    public R<PageResult<Map<String, Object>>> products(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer category,
            @RequestParam(required = false) Boolean enabled
    ) {
        return R.success(service.products(query, keyword, category, enabled));
    }

    @PostMapping("/products")
    @RequiresPermission("coffee:product:add")
    @OperLog(module = "商品管理", action = "新增")
    public R<Void> createProduct(@RequestBody ProductWriteRequest request) {
        service.createProduct(request);
        return R.success(null);
    }

    @PutMapping("/products/{id}")
    @RequiresPermission("coffee:product:edit")
    @OperLog(module = "商品管理", action = "修改")
    public R<Void> updateProduct(@PathVariable String id, @RequestBody ProductWriteRequest request) {
        service.updateProduct(id, request);
        return R.success(null);
    }

    @DeleteMapping("/products/{id}")
    @RequiresPermission("coffee:product:remove")
    @OperLog(module = "商品管理", action = "删除")
    public R<Map<String, Object>> deleteProduct(@PathVariable String id) {
        String notice = service.deleteProduct(id);
        return R.success(notice == null ? Map.of("ok", true) : Map.of("ok", true, "notice", notice));
    }

    /**
     * 上架 / 下架。
     *
     * <p>独立端点而非复用 {@code PUT /products/{id}}：后者是整行更新，
     * 只带 enabled 会被必填校验拒绝，且会用空值覆盖其他列。
     */
    @PutMapping("/products/{id}/enabled")
    @RequiresPermission("coffee:product:edit")
    @OperLog(module = "商品管理", action = "上下架")
    public R<Void> setProductEnabled(@PathVariable String id, @RequestBody ProductEnabledRequest request) {
        service.setProductEnabled(id, request.getEnabled());
        return R.success(null);
    }

    // ---- 规格选项 ----

    @GetMapping("/options")
    @RequiresPermission("coffee:option:list")
    public R<List<Map<String, Object>>> options() {
        return R.success(service.options());
    }

    @PostMapping("/options")
    @RequiresPermission("coffee:option:add")
    @OperLog(module = "规格选项", action = "新增")
    public R<Map<String, Object>> createOption(@RequestBody OptionWriteRequest request) {
        return R.success(Map.of("id", service.createOption(request)));
    }

    @PutMapping("/options/{id}")
    @RequiresPermission("coffee:option:edit")
    @OperLog(module = "规格选项", action = "修改")
    public R<Void> updateOption(@PathVariable long id, @RequestBody OptionWriteRequest request) {
        service.updateOption(id, request);
        return R.success(null);
    }

    @DeleteMapping("/options/{id}")
    @RequiresPermission("coffee:option:remove")
    @OperLog(module = "规格选项", action = "删除")
    public R<Void> deleteOption(@PathVariable long id) {
        service.deleteOption(id);
        return R.success(null);
    }

    // ---- 订单 ----

    @GetMapping("/orders")
    @RequiresPermission("coffee:order:list")
    public R<PageResult<Map<String, Object>>> orders(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return R.success(service.orders(query, keyword, status));
    }

    @PutMapping("/orders/{id}/complete")
    @RequiresPermission("coffee:order:edit")
    @OperLog(module = "订单管理", action = "完成订单")
    public R<Void> completeOrder(@PathVariable String id) {
        service.completeOrder(id);
        return R.success(null);
    }

    @DeleteMapping("/orders/{id}")
    @RequiresPermission("coffee:order:remove")
    @OperLog(module = "订单管理", action = "删除")
    public R<Void> deleteOrder(@PathVariable String id) {
        service.deleteOrder(id);
        return R.success(null);
    }

    // ---- 会员 ----

    @GetMapping("/members")
    @RequiresPermission("coffee:member:list")
    public R<PageResult<Map<String, Object>>> members(
            PageQuery query,
            @RequestParam(required = false) String keyword
    ) {
        return R.success(service.members(query, keyword));
    }

    @PutMapping("/members/{id}")
    @RequiresPermission("coffee:member:edit")
    @OperLog(module = "会员管理", action = "修改")
    public R<Void> updateMember(@PathVariable String id, @RequestBody MemberWriteRequest request) {
        service.updateMember(id, request);
        return R.success(null);
    }

    @DeleteMapping("/members/{id}")
    @RequiresPermission("coffee:member:remove")
    @OperLog(module = "会员管理", action = "删除")
    public R<Void> deleteMember(@PathVariable String id) {
        service.deleteMember(id);
        return R.success(null);
    }

    // ---- 权益 ----

    @GetMapping("/benefits")
    @RequiresPermission("coffee:benefit:list")
    public R<List<Map<String, Object>>> benefits() {
        return R.success(service.benefits());
    }

    @PostMapping("/benefits")
    @RequiresPermission("coffee:benefit:add")
    @OperLog(module = "会员权益", action = "新增")
    public R<Map<String, Object>> createBenefit(@RequestBody BenefitWriteRequest request) {
        return R.success(Map.of("id", service.createBenefit(request)));
    }

    @PutMapping("/benefits/{id}")
    @RequiresPermission("coffee:benefit:edit")
    @OperLog(module = "会员权益", action = "修改")
    public R<Void> updateBenefit(@PathVariable long id, @RequestBody BenefitWriteRequest request) {
        service.updateBenefit(id, request);
        return R.success(null);
    }

    @DeleteMapping("/benefits/{id}")
    @RequiresPermission("coffee:benefit:remove")
    @OperLog(module = "会员权益", action = "删除")
    public R<Void> deleteBenefit(@PathVariable long id) {
        service.deleteBenefit(id);
        return R.success(null);
    }

    // ---- 门店 ----

    @GetMapping("/store")
    @RequiresPermission("coffee:store:list")
    public R<Map<String, Object>> store() {
        return R.success(service.store());
    }

    @PutMapping("/store")
    @RequiresPermission("coffee:store:edit")
    @OperLog(module = "门店设置", action = "修改")
    public R<Void> updateStore(@RequestBody StoreWriteRequest request) {
        service.updateStore(request);
        return R.success(null);
    }
}
