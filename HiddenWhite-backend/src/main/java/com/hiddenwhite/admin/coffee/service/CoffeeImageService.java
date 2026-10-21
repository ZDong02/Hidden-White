package com.hiddenwhite.admin.coffee.service;

import com.hiddenwhite.common.exception.BusinessException;
import com.hiddenwhite.common.image.ImageProcessor;
import com.hiddenwhite.common.image.ImageProperties;
import com.hiddenwhite.common.image.ImageStorageService;
import com.hiddenwhite.common.image.ProcessedImage;
import com.hiddenwhite.common.image.ScaleMode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;

/**
 * 商品图片上传服务：校验 → 按用途处理 → 落盘 → 返回可访问 URL。
 */
@Service
public class CoffeeImageService {

    /** 图片用途：列表图 / 详情图。两者目标宽高独立配置。 */
    public enum Slot {
        /** 列表图。 */
        LIST,
        /** 详情图。 */
        DETAIL
    }

    private final ImageProperties properties;
    private final ImageStorageService storage;

    public CoffeeImageService(ImageProperties properties, ImageStorageService storage) {
        this.properties = properties;
        this.storage = storage;
    }

    /**
     * 上传并处理一张商品图片。
     *
     * @param file   上传文件
     * @param slot   图片用途
     * @return 处理结果
     */
    public UploadResult upload(MultipartFile file, Slot slot) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        long maxBytes = properties.getMaxBytes();
        if (file.getSize() > maxBytes) {
            throw new BusinessException("图片大小超过 %s，请压缩后再上传".formatted(formatSize(maxBytes)));
        }
        byte[] source;
        try {
            source = file.getBytes();
        } catch (Exception exception) {
            throw new BusinessException("图片读取失败，请重新选择图片");
        }
        // 先按真实字节判定格式，不信任前端传入的 contentType
        ImageProcessor.detectFormat(source);

        ImageProperties.Slot spec = specOf(slot);
        ProcessedImage processed = ImageProcessor.process(
                source, spec.getWidth(), spec.getHeight(), spec.getMode());
        String url = storage.store(processed);
        return new UploadResult(url, processed.width(), processed.height(), processed.format(),
                processed.size(), processed.sourceWidth(), processed.sourceHeight(), processed.ratioChanged());
    }

    /** 取指定用途的目标规格。 */
    private ImageProperties.Slot specOf(Slot slot) {
        ImageProperties.Slot spec = slot == Slot.DETAIL ? properties.getDetail() : properties.getList();
        if (spec.getWidth() <= 0 || spec.getHeight() <= 0) {
            throw new BusinessException("图片目标尺寸配置无效，请联系管理员");
        }
        return spec;
    }

    /** 把字节数格式化为便于阅读的文案。 */
    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return "%.1fMB".formatted(bytes / 1024.0 / 1024.0);
        }
        return "%dKB".formatted(bytes / 1024);
    }

    /**
     * 上传结果。
     *
     * @param url          图片可访问地址
     * @param width        处理后宽度
     * @param height       处理后高度
     * @param format       输出格式
     * @param size         输出字节数
     * @param sourceWidth  原图宽度
     * @param sourceHeight 原图高度
     * @param ratioChanged 原图与目标比例是否不一致（不一致说明发生了裁剪）
     */
    public record UploadResult(String url, int width, int height, String format, long size,
                               int sourceWidth, int sourceHeight, boolean ratioChanged) {
    }
}
