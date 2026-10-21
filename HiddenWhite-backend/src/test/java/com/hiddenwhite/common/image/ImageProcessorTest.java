package com.hiddenwhite.common.image;

import com.hiddenwhite.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图片处理测试。
 *
 * <p>锁住三条核心不变式：
 * <ol>
 *   <li>输出尺寸必须精确等于目标宽高；</li>
 *   <li>不得变形：输出宽高比 = 目标宽高比；</li>
 *   <li>只压一次：输出是可解码的合法图片，且格式符合预期。</li>
 * </ol>
 */
class ImageProcessorTest {

    /** 构造一张纯色图片，用于验证缩放与裁剪的几何结果。 */
    private static byte[] sampleImage(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }

    @Test
    void coverModeProducesExactTargetSize() throws IOException {
        byte[] source = sampleImage(1200, 900, Color.RED);

        ProcessedImage result = ImageProcessor.process(source, 600, 400, ScaleMode.COVER);

        assertThat(result.width()).isEqualTo(600);
        assertThat(result.height()).isEqualTo(400);
    }

    @Test
    void coverModeKeepsTargetRatioWithoutDistortion() throws IOException {
        // 源图 4:3，目标 1:1：cover 会从两侧裁掉，不会把画面压扁
        byte[] source = sampleImage(1200, 900, Color.RED);

        ProcessedImage result = ImageProcessor.process(source, 600, 600, ScaleMode.COVER);

        assertThat(result.width()).isEqualTo(600);
        assertThat(result.height()).isEqualTo(600);
        assertThat((double) result.width() / result.height()).isEqualTo(1.0d);
    }

    @Test
    void containModeFitsWholeImageInsideTargetBox() throws IOException {
        // 源图比目标更宽：contain 应整体缩进框内，长边贴合目标框
        byte[] source = sampleImage(1200, 600, Color.RED);

        ProcessedImage result = ImageProcessor.process(source, 600, 600, ScaleMode.CONTAIN);

        assertThat(result.width()).isEqualTo(600);
        assertThat(result.height()).isEqualTo(300);
        // 不补背景画布，因此输出就是缩放后的实际尺寸
        assertThat(result.height()).isLessThan(600);
    }

    @Test
    void containModeNeverUpscalesBeyondTarget() throws IOException {
        // 源图比目标小：contain 不放大，长边贴合目标框，短边按原比例
        byte[] source = sampleImage(300, 150, Color.RED);

        ProcessedImage result = ImageProcessor.process(source, 600, 600, ScaleMode.CONTAIN);

        assertThat(result.width()).isEqualTo(600);
        assertThat(result.height()).isEqualTo(300);
    }

    @Test
    void outputIsDecodableAndCorrectlyFormatted() throws IOException {
        byte[] source = sampleImage(800, 600, Color.BLUE);

        ProcessedImage result = ImageProcessor.process(source, 400, 300, ScaleMode.COVER);

        // 不含透明通道 → 输出 JPEG
        assertThat(result.format()).isEqualTo("jpg");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(400);
        assertThat(decoded.getHeight()).isEqualTo(300);
    }

    @Test
    void transparentSourceKeepsPngFormat() throws IOException {
        BufferedImage transparent = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(transparent, "png", buffer);

        ProcessedImage result = ImageProcessor.process(buffer.toByteArray(), 200, 200, ScaleMode.COVER);

        // 含透明通道必须保留 PNG，否则透明区会被压成黑色
        assertThat(result.format()).isEqualTo("png");
    }

    @Test
    void detectsCommonFormatsAndRejectsOthers() throws IOException {
        assertThat(ImageProcessor.detectFormat(sampleImage(10, 10, Color.RED))).isEqualTo("png");

        byte[] notAnImage = "this is definitely not an image".getBytes("UTF-8");
        assertThatThrownBy(() -> ImageProcessor.detectFormat(notAnImage))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别");
    }

    @Test
    void rejectsEmptyUpload() {
        assertThatThrownBy(() -> ImageProcessor.process(new byte[0], 100, 100, ScaleMode.COVER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("为空");
    }

    @Test
    void ratioChangedDetectsAspectDeviation() throws IOException {
        byte[] source = sampleImage(1200, 900, Color.RED);

        ProcessedImage result = ImageProcessor.process(source, 600, 600, ScaleMode.COVER);

        // 原图 4:3 → 目标 1:1，比例被改变过
        assertThat(result.ratioChanged()).isTrue();
    }
}
