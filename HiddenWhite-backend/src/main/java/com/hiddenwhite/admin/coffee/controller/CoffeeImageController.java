package com.hiddenwhite.admin.coffee.controller;

import com.hiddenwhite.admin.coffee.service.CoffeeImageService;
import com.hiddenwhite.common.domain.R;
import com.hiddenwhite.common.image.ImageProperties;
import com.hiddenwhite.common.image.ScaleMode;
import com.hiddenwhite.framework.audit.OperLog;
import com.hiddenwhite.framework.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品图片上传接口。
 *
 * <p>与商品增删改查分离：上传只负责「把图片按目标比例处理好并返回 URL」，
 * 商品表单仍按原有逻辑提交图片 URL 字段，业务链路与页面结构保持不变。
 */
@RestController
@RequestMapping("/api/admin/coffee/images")
public class CoffeeImageController {

    private final CoffeeImageService imageService;
    private final ImageProperties properties;

    public CoffeeImageController(CoffeeImageService imageService, ImageProperties properties) {
        this.imageService = imageService;
        this.properties = properties;
    }

    /**
     * 上传商品图片。
     *
     * @param file 上传的图片文件
     * @param slot 图片用途：list（列表图）/ detail（详情图）
     * @return 处理后的图片地址与尺寸信息
     */
    @PostMapping("/upload")
    @RequiresPermission("coffee:product:add")
    @OperLog(module = "商品管理", action = "上传图片")
    public R<CoffeeImageService.UploadResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "slot", defaultValue = "list") String slot) {
        CoffeeImageService.Slot target = "detail".equalsIgnoreCase(slot)
                ? CoffeeImageService.Slot.DETAIL
                : CoffeeImageService.Slot.LIST;
        return R.success(imageService.upload(file, target));
    }

    /**
     * 查询各用途图片的目标规格。
     *
     * <p>管理端据此展示目标宽高与缩放方式，并在上传前做比例预校验。
     */
    @GetMapping("/spec")
    @RequiresPermission("coffee:product:list")
    public R<Map<String, Object>> spec() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("maxBytes", properties.getMaxBytes());
        data.put("list", describe(properties.getList()));
        data.put("detail", describe(properties.getDetail()));
        return R.success(data);
    }

    /** 单个用途的规格描述。 */
    private Map<String, Object> describe(ImageProperties.Slot slot) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("width", slot.getWidth());
        item.put("height", slot.getHeight());
        item.put("mode", slot.getMode() == ScaleMode.CONTAIN ? "contain" : "cover");
        return item;
    }
}
