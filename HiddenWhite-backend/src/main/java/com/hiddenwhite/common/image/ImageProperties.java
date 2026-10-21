package com.hiddenwhite.common.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片上传与处理配置。
 *
 * <p>列表图与详情图<b>各自独立</b>配置目标尺寸与缩放模式：
 * 列表图出现在点单端商品卡片的横向容器里，详情图用于详情页大图，
 * 两者宽高比不同，因此必须分开配置、分开处理。
 *
 * <p>配置项（application.yml）：
 * <pre>
 * hiddenwhite:
 *   image:
 *     upload-dir: ./data/uploads
 *     max-bytes: 10485760
 *     list:
 *       width: 600
 *       height: 400
 *       mode: cover
 *     detail:
 *       width: 1200
 *       height: 900
 *       mode: cover
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "hiddenwhite.image")
public class ImageProperties {

    /** 上传文件落盘目录。 */
    private String uploadDir = "./data/uploads";

    /** 单张图片允许的最大字节数，默认 10MB。 */
    private long maxBytes = 10L * 1024 * 1024;

    /** 列表图规格。 */
    private Slot list = new Slot(600, 400, ScaleMode.COVER);

    /** 详情图规格。 */
    private Slot detail = new Slot(1200, 900, ScaleMode.COVER);

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public Slot getList() {
        return list;
    }

    public void setList(Slot list) {
        this.list = list;
    }

    public Slot getDetail() {
        return detail;
    }

    public void setDetail(Slot detail) {
        this.detail = detail;
    }

    /**
     * 单个用途（列表图 / 详情图）的目标规格。
     *
     * @param width  目标宽度（像素）
     * @param height 目标高度（像素）
     * @param mode   缩放模式
     */
    public static class Slot {

        private int width = 600;
        private int height = 400;
        private ScaleMode mode = ScaleMode.COVER;

        public Slot() {
        }

        public Slot(int width, int height, ScaleMode mode) {
            this.width = width;
            this.height = height;
            this.mode = mode;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public ScaleMode getMode() {
            return mode;
        }

        public void setMode(ScaleMode mode) {
            this.mode = mode == null ? ScaleMode.COVER : mode;
        }
    }
}
