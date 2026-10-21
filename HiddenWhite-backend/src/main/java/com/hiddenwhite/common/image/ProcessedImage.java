package com.hiddenwhite.common.image;

/**
 * 图片处理结果。
 *
 * @param bytes         处理后的图片字节
 * @param width         输出宽度（像素）
 * @param height        输出高度（像素）
 * @param format        输出格式（jpg / png）
 * @param size          输出字节数
 * @param sourceWidth   原图宽度（像素），用于提示是否发生了裁剪
 * @param sourceHeight  原图高度（像素）
 */
public record ProcessedImage(byte[] bytes, int width, int height, String format, long size,
                             int sourceWidth, int sourceHeight) {

    /** 补上原图尺寸。 */
    public ProcessedImage withSource(int sourceWidth, int sourceHeight) {
        return new ProcessedImage(bytes, width, height, format, size, sourceWidth, sourceHeight);
    }

    /**
     * 原图与目标的比例是否不一致（超过 2% 视为不一致）。
     *
     * <p>用于给运营者明确提示「这张图被裁剪过」或「按等比缩放保留了原图」。
     */
    public boolean ratioChanged() {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return false;
        }
        double sourceRatio = (double) sourceWidth / sourceHeight;
        double targetRatio = (double) width / height;
        if (targetRatio <= 0) {
            return false;
        }
        return Math.abs(sourceRatio - targetRatio) / targetRatio > 0.02;
    }
}
