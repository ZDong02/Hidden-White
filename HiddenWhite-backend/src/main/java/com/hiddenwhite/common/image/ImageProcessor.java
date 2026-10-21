package com.hiddenwhite.common.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.hiddenwhite.common.exception.BusinessException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * 图片处理器：解码 → 按目标比例缩放/裁剪 → 单次编码输出。
 *
 * <p><b>清晰度保障</b>：整条链路只做「解码一次 + 编码一次」，
 * 不会出现「先压缩再压缩」的代际损失。大图缩到小图采用<b>渐进式降采样</b>
 * （先反复二分缩到接近目标，再做一次高质量重采样），
 * 比一次性缩放更能保留细节、减少锯齿。
 *
 * <p><b>输出格式</b>：含透明通道的原图输出 PNG，其余输出 JPEG（质量 0.9）。
 * JPEG 不支持透明，转成 JPEG 会让透明区域变黑，因此含透明通道时保留 PNG。
 */
public final class ImageProcessor {

    /** JPEG 输出质量：0.9 在体积与观感之间取平衡，肉眼几乎无损。 */
    private static final float JPEG_QUALITY = 0.9f;

    /** 允许的输入格式。GIF 动图与 BMP 取舍后不开放，避免静默丢帧。 */
    private static final String[] ALLOWED_FORMATS = {"jpeg", "jpg", "png", "webp"};

    /** 单张图片允许的最大像素数（约 8000 万），用于拦截超大图导致的内存风险。 */
    private static final long MAX_PIXELS = 80_000_000L;

    private ImageProcessor() {
    }

    /**
     * 按目标尺寸处理图片。
     *
     * @param source 原始图片字节
     * @param targetWidth  目标宽度（像素）
     * @param targetHeight 目标高度（像素）
     * @param mode         缩放模式
     * @return 处理结果
     */
    public static ProcessedImage process(byte[] source, int targetWidth, int targetHeight, ScaleMode mode) {
        if (source == null || source.length == 0) {
            throw new BusinessException("上传的图片为空");
        }
        BufferedImage original = decode(source);
        try {
            // 先按 EXIF 摆正，避免手机横竖拍的照片被裁错方向
            BufferedImage oriented = applyExifOrientation(original, source);
            int sourceWidth = oriented.getWidth();
            int sourceHeight = oriented.getHeight();
            BufferedImage scaled = scale(oriented, targetWidth, targetHeight, mode);
            ProcessedImage result = encode(scaled);
            return result.withSource(sourceWidth, sourceHeight);
        } finally {
            original.flush();
        }
    }

    /**
     * 校验输入格式，返回可识别的格式名。
     *
     * <p>按图片真实字节判定，不信任前端传入的 contentType。
     *
     * @param source 原始字节
     * @return 小写的格式名
     */
    public static String detectFormat(byte[] source) {
        if (source == null || source.length == 0) {
            throw new BusinessException("上传的图片为空");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException("无法识别的图片格式，请上传 JPG / PNG / WebP 图片");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                for (String candidate : ALLOWED_FORMATS) {
                    if (candidate.equals(format)) {
                        return format;
                    }
                }
                throw new BusinessException("不支持的图片格式（" + format + "），请上传 JPG / PNG / WebP 图片");
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BusinessException("图片数据损坏或格式无法解析，请更换一张图片重试");
        }
    }

    /** 解码为内存位图，并做像素规模校验。 */
    private static BufferedImage decode(byte[] source) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException("无法识别的图片格式，请上传 JPG / PNG / WebP 图片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new BusinessException("图片分辨率过大（超过 8000 万像素），请压缩后再上传");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new BusinessException("图片解析失败，请更换一张图片重试");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BusinessException("图片数据损坏或格式无法解析，请更换一张图片重试");
        }
    }

    /**
     * 按 EXIF Orientation 摆正图片。
     *
     * <p>手机拍摄的照片把旋转信息写在 EXIF 里而不在像素本身，
     * 不处理的话图片会「躺着」上传，裁剪时也会裁错方向。
     *
     * <p>其中 5~8 需要旋转 90 度，输出宽高随之互换。
     */
    private static BufferedImage applyExifOrientation(BufferedImage image, byte[] source) {
        Integer orientation = readExifOrientation(source);
        if (orientation == null || orientation == 1) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean swapped = orientation >= 5;
        int targetWidth = swapped ? height : width;
        int targetHeight = swapped ? width : height;

        // 保持原有的色彩模型：照片若无透明通道，旋转后也不应变成 PNG
        int imageType = image.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(image, transformFor(orientation, width, height), null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * EXIF Orientation 对应的仿射变换。
     *
     * <p>取值含义：1 正常、2 水平镜像、3 旋转 180、4 垂直镜像、
     * 5 转置、6 顺时针 90、7 副对角线镜像、8 逆时针 90。
     */
    private static AffineTransform transformFor(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }

    /** 读取 EXIF Orientation，取值缺失时返回 null。 */
    private static Integer readExifOrientation(byte[] source) {
        try (InputStream input = new ByteArrayInputStream(source)) {
            var metadata = ImageMetadataReader.readMetadata(input);
            Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory == null) {
                return null;
            }
            Integer value = directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return value == null ? null : Math.max(1, Math.min(8, value));
        } catch (Exception exception) {
            // EXIF 缺失或损坏不影响正常处理，按未旋转处理
            return null;
        }
    }

    /**
     * 缩放到目标尺寸。
     *
     * <p>COVER：先按覆盖比例放大/缩小，再从中心裁掉超出部分，
     * 输出精确等于目标宽高，铺满不留白边。
     *
     * <p>CONTAIN：按内接比例缩放，输出为缩放后的实际尺寸（长边贴合目标框），
     * <b>不补背景画布</b>——补白属于「留白」，与需求相悖；
     * 且点单端统一用 aspectFill 渲染，补出来的背景在展示端也会被裁掉，纯属浪费体积。
     */
    private static BufferedImage scale(BufferedImage image, int targetWidth, int targetHeight,
                                       ScaleMode mode) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            throw new BusinessException("图片尺寸异常，请更换一张图片重试");
        }
        double targetRatio = (double) targetWidth / targetHeight;
        double sourceRatio = (double) width / height;

        if (mode == ScaleMode.COVER) {
            // 覆盖：取两轴中较大的缩放比，保证铺满后至少有一轴被裁
            double scale = sourceRatio > targetRatio
                    ? (double) targetHeight / height
                    : (double) targetWidth / width;
            int scaledWidth = Math.max(1, (int) Math.round(width * scale));
            int scaledHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage scaled = resize(image, scaledWidth, scaledHeight);
            if (scaledWidth == targetWidth && scaledHeight == targetHeight) {
                return scaled;
            }
            int offsetX = (scaledWidth - targetWidth) / 2;
            int offsetY = (scaledHeight - targetHeight) / 2;
            // 复制出一张独立位图，避免与原图共享像素数据带来的裁剪副作用
            return copyRegion(scaled, offsetX, offsetY, targetWidth, targetHeight);
        }

        // 内接：取两轴中较小的缩放比，保证整张图都在框内，输出即缩放后的尺寸
        double scale = sourceRatio > targetRatio
                ? (double) targetWidth / width
                : (double) targetHeight / height;
        int scaledWidth = Math.max(1, Math.min(targetWidth, (int) Math.round(width * scale)));
        int scaledHeight = Math.max(1, Math.min(targetHeight, (int) Math.round(height * scale)));
        return resize(image, scaledWidth, scaledHeight);
    }

    /** 从指定区域复制出一张独立位图。 */
    private static BufferedImage copyRegion(BufferedImage source, int x, int y, int width, int height) {
        int imageType = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, imageType);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, -x, -y, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * 高质量重采样。
     *
     * <p>当缩放比例小于 1/2 时，先反复二分缩到接近目标尺寸，
     * 最后一步再做一次双线性重采样。二分过程能显著减少大比例缩小产生的锯齿与摩尔纹。
     */
    private static BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight) {
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();
        BufferedImage current = source;

        // 反复二分，直到再缩一次就小于等于目标尺寸
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            current = drawScaled(current, currentWidth / 2, currentHeight / 2);
            currentWidth = current.getWidth();
            currentHeight = current.getHeight();
        }
        if (currentWidth == targetWidth && currentHeight == targetHeight) {
            return current;
        }
        return drawScaled(current, targetWidth, targetHeight);
    }

    /** 用双线性插值绘制到指定尺寸。 */
    private static BufferedImage drawScaled(BufferedImage source, int targetWidth, int targetHeight) {
        boolean hasAlpha = source.getColorModel().hasAlpha();
        BufferedImage target = new BufferedImage(targetWidth, targetHeight,
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * 编码输出。
     *
     * <p>整条链路只在这里编码一次，避免多次压缩带来的画质损失。
     * 带透明通道的图输出 PNG，其余输出 JPEG。
     */
    private static ProcessedImage encode(BufferedImage image) {
        boolean hasAlpha = image.getColorModel().hasAlpha();
        String format = hasAlpha ? "png" : "jpg";
        String writerFormat = hasAlpha ? "png" : "jpeg";
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(writerFormat);
            if (!writers.hasNext()) {
                throw new BusinessException("当前环境缺少图片编码器，无法保存上传的图片");
            }
            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (!hasAlpha && param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                writer.setOutput(output);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            byte[] bytes = buffer.toByteArray();
            return new ProcessedImage(bytes, image.getWidth(), image.getHeight(), format, bytes.length, 0, 0);
        } catch (IOException exception) {
            throw new BusinessException("图片保存失败，请稍后重试");
        }
    }
}
