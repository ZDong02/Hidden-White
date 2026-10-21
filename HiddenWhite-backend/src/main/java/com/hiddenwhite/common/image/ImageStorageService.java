package com.hiddenwhite.common.image;

import com.hiddenwhite.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 图片落盘服务。
 *
 * <p>文件按 {@code yyyy/MM/dd} 分目录，文件名取内容 SHA-256 前 16 位。
 * 同内容重复上传会得到同一文件名（天然去重），不同内容不会互相覆盖。
 */
@Service
public class ImageStorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final ImageProperties properties;

    public ImageStorageService(ImageProperties properties) {
        this.properties = properties;
    }

    /**
     * 保存处理后的图片，返回可访问的相对 URL。
     *
     * @param processed 处理结果
     * @return 形如 {@code /uploads/2026/10/21/a1b2c3d4e5f67890.jpg}
     */
    public String store(ProcessedImage processed) {
        String extension = "png".equals(processed.format()) ? "png" : "jpg";
        String name = hash(processed.bytes()) + "." + extension;
        String relative = DATE_PATH.format(LocalDate.now()) + "/" + name;

        Path root = root();
        Path target = root.resolve(relative).normalize();
        // 双保险：确保最终路径仍位于上传根目录内，杜绝路径穿越
        if (!target.startsWith(root)) {
            throw new BusinessException("图片保存失败：非法路径");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, processed.bytes());
        } catch (IOException exception) {
            throw new BusinessException("图片保存失败，请检查服务端存储目录权限");
        }
        return "/uploads/" + relative;
    }

    /** 上传根目录，配置为相对路径时以进程工作目录为基准。 */
    public Path root() {
        Path path = Paths.get(properties.getUploadDir());
        return path.isAbsolute() ? path.normalize() : path.toAbsolutePath().normalize();
    }

    /** 内容哈希，取前 16 位十六进制。 */
    private static String hash(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("图片保存失败：运行环境缺少 SHA-256 支持");
        }
    }

    /** 读取已保存的图片（供测试与后续的图片读取接口使用）。 */
    public byte[] read(String relativePath) {
        Path root = root();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new BusinessException("图片不存在");
        }
        try (InputStream input = Files.newInputStream(target)) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new BusinessException("图片读取失败");
        }
    }
}
