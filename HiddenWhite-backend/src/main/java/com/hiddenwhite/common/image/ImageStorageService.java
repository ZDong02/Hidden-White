package com.hiddenwhite.common.image;

import com.hiddenwhite.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p><b>上传根目录的解析基准</b>：相对路径不再以「进程工作目录」为基准，
 * 而是以<b>本模块所在目录</b>为基准（见 {@link #root()}）。
 * 原因是一个真实踩过的坑：从 {@code HiddenWhite-backend/} 启动时文件落在
 * {@code HiddenWhite-backend/data/uploads}，从仓库根启动时落在 {@code <仓库>/data/uploads}，
 * 数据库里记的却是同一个 {@code /uploads/...} 路径 —— 于是「换了启动方式，商品图集体 404」。
 * 静态资源映射（{@code WebConfig}）与落盘必须共用本类解析出的同一个目录，
 * 否则读写两侧会各算一套。
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final ImageProperties properties;
    private final Path root;

    public ImageStorageService(ImageProperties properties) {
        this.properties = properties;
        this.root = resolveRoot(properties.getUploadDir());
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            log.error("无法创建图片上传目录 {}，上传会失败", root, exception);
        }
        // 启动即打印绝对路径：目录跑偏时这是唯一能一眼看出来的线索
        log.info("图片上传目录: {}", root);
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

    /** 上传根目录（绝对路径）。落盘与静态映射共用这一个入口。 */
    public Path root() {
        return root;
    }

    /**
     * 解析上传根目录。
     *
     * <p>绝对路径直接采用；相对路径以本模块根目录为基准 —— 通过代码所在位置反推：
     * <ul>
     *   <li>{@code java -jar target/xxx.jar} → 代码位置是 jar 文件 → 上溯两级到模块根；</li>
     *   <li>IDE / Maven 运行 → 代码位置是 {@code target/classes} → 上溯两级到模块根。</li>
     * </ul>
     * 反推失败（如被包装到其他容器）时退回进程工作目录，行为与改造前一致。
     */
    private static Path resolveRoot(String configured) {
        Path path = Paths.get(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return moduleRoot().resolve(path).normalize();
    }

    private static Path moduleRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        try {
            var location = ImageStorageService.class.getProtectionDomain().getCodeSource().getLocation();
            Path codePath = Paths.get(location.toURI()).toAbsolutePath();
            Path base = Files.isRegularFile(codePath) ? codePath.getParent() : codePath;
            if (base != null && "classes".equals(String.valueOf(base.getFileName()))) {
                base = base.getParent();
            }
            if (base != null && "target".equals(String.valueOf(base.getFileName()))) {
                base = base.getParent();
            }
            return base == null ? cwd : base;
        } catch (Exception exception) {
            log.debug("无法从代码位置推断模块根目录，退回进程工作目录", exception);
            return cwd;
        }
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
