package com.hiddenwhite.admin.redeem.controller;

import com.hiddenwhite.admin.coffee.service.CoffeeImageService;
import com.hiddenwhite.admin.redeem.domain.dto.RedeemProductEnabledRequest;
import com.hiddenwhite.admin.redeem.domain.dto.RedeemProductWriteRequest;
import com.hiddenwhite.admin.redeem.domain.dto.RedeemVerifyRequest;
import com.hiddenwhite.admin.redeem.service.RedeemAdminService;
import com.hiddenwhite.common.domain.PageQuery;
import com.hiddenwhite.common.domain.PageResult;
import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.common.image.ImageProperties;
import com.hiddenwhite.common.image.ScaleMode;
import com.hiddenwhite.framework.audit.OperLog;
import com.hiddenwhite.framework.security.RequiresPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 积分运营管理接口：兑换品目录 + 兑换记录核销。
 *
 * <p>页面入口见 sys_menu「积分运营」目录（600 段），权限码前缀 {@code redeem:}。
 * 图片沿用商品图片的处理链路（{@link CoffeeImageService}）：同一套
 * 「服务端只解码一次、按目标比例裁剪一次」的处理，只是换成积分运营自己的权限码，
 * 避免为了传一张兑换品图去申请商品管理权限。
 */
@RestController
@RequestMapping("/api/admin/redeem")
public class RedeemAdminController {

    private final RedeemAdminService service;
    private final CoffeeImageService imageService;
    private final ImageProperties imageProperties;

    public RedeemAdminController(RedeemAdminService service, CoffeeImageService imageService,
                                 ImageProperties imageProperties) {
        this.service = service;
        this.imageService = imageService;
        this.imageProperties = imageProperties;
    }

    // ---- 兑换品 ----

    @GetMapping("/products")
    @RequiresPermission("redeem:product:list")
    public R<PageResult<Map<String, Object>>> products(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled) {
        return R.success(service.products(query, keyword, enabled));
    }

    @PostMapping("/products")
    @RequiresPermission("redeem:product:add")
    @OperLog(module = "积分运营", action = "新增兑换品")
    public R<Void> createProduct(@RequestBody RedeemProductWriteRequest request) {
        service.createProduct(request);
        return R.success(null);
    }

    @PutMapping("/products/{id}")
    @RequiresPermission("redeem:product:edit")
    @OperLog(module = "积分运营", action = "修改兑换品")
    public R<Void> updateProduct(@PathVariable String id, @RequestBody RedeemProductWriteRequest request) {
        service.updateProduct(id, request);
        return R.success(null);
    }

    /** 上下架：独立端点，只带状态位。 */
    @PutMapping("/products/{id}/enabled")
    @RequiresPermission("redeem:product:edit")
    @OperLog(module = "积分运营", action = "兑换品上下架")
    public R<Void> setProductEnabled(@PathVariable String id, @RequestBody RedeemProductEnabledRequest request) {
        service.setProductEnabled(id, request.getEnabled());
        return R.success(null);
    }

    @DeleteMapping("/products/{id}")
    @RequiresPermission("redeem:product:remove")
    @OperLog(module = "积分运营", action = "删除兑换品")
    public R<Void> deleteProduct(@PathVariable String id) {
        service.deleteProduct(id);
        return R.success(null);
    }

    // ---- 兑换记录 ----

    @GetMapping("/orders")
    @RequiresPermission("redeem:order:list")
    public R<PageResult<Map<String, Object>>> orders(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.success(service.orders(query, keyword, status));
    }

    @PostMapping("/orders/verify")
    @RequiresPermission("redeem:order:verify")
    @OperLog(module = "积分运营", action = "核销兑换码")
    public R<Map<String, Object>> verify(@RequestBody RedeemVerifyRequest request) {
        return R.success(service.verify(request));
    }

    // ---- 图片 ----

    /** 兑换品图片：与商品列表图同一目标规格（点单端卡片容器比例一致）。 */
    @PostMapping("/images/upload")
    @RequiresPermission("redeem:product:add")
    @OperLog(module = "积分运营", action = "上传兑换品图片")
    public R<CoffeeImageService.UploadResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "slot", defaultValue = "list") String slot) {
        CoffeeImageService.Slot target = "detail".equalsIgnoreCase(slot)
                ? CoffeeImageService.Slot.DETAIL
                : CoffeeImageService.Slot.LIST;
        return R.success(imageService.upload(file, target));
    }

    @GetMapping("/images/spec")
    @RequiresPermission("redeem:product:list")
    public R<Map<String, Object>> spec() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("maxBytes", imageProperties.getMaxBytes());
        data.put("list", describe(imageProperties.getList()));
        data.put("detail", describe(imageProperties.getDetail()));
        return R.success(data);
    }

    private Map<String, Object> describe(ImageProperties.Slot slot) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("width", slot.getWidth());
        item.put("height", slot.getHeight());
        item.put("mode", slot.getMode() == ScaleMode.CONTAIN ? "contain" : "cover");
        return item;
    }
}
