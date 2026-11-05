package com.hiddenwhite.framework.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口文档暴露面测试。
 *
 * <p>锁住三条不变式（改动配置导致破防时测试会直接失败）：
 * <ol>
 *   <li>base 配置不得把 {@code hiddenwhite.api-docs.enabled} 打开 —— 它对所有 profile 生效；
 *   <li>prod 必须显式关闭放行开关，并且让 springdoc 根本不生成文档（双保险）；
 *   <li>dev 必须显式打开，否则本地开发看不到 /doc.html。</li>
 * </ol>
 *
 * <p>直接读 yml 原文而非读注入后的值：{@code @SpringBootTest} 只能覆盖一个 profile，
 * 而这里要同时校验 dev 与 prod 两套配置。
 */
class ApiDocsExposureTest {

    private static Map<String, Object> load(String file) throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream input = Files.newInputStream(Path.of("src", "main", "resources", file))) {
            return yaml.load(input);
        }
    }

    /** 逐层下钻取子节点；任一层缺失都返回空 Map，便于断言「没有配」而不是 NPE。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String... keys) {
        Map<String, Object> current = root;
        for (String key : keys) {
            Object next = current == null ? null : current.get(key);
            if (!(next instanceof Map)) {
                return Map.of();
            }
            current = (Map<String, Object>) next;
        }
        return current;
    }

    @Test
    void baseConfigMustNotEnableApiDocs() throws Exception {
        Map<String, Object> base = load("application.yml");
        Object enabled = section(base, "hiddenwhite", "api-docs").get("enabled");

        // 允许「不配」（SecurityConfig 的 @Value 默认值就是 false），但不允许写成 true。
        assertThat(enabled)
                .as("application.yml 对所有 profile 生效，不能在这里把接口文档打开")
                .isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void prodMustDenyApiDocsAndDisableSpringdoc() throws Exception {
        Map<String, Object> prod = load("application-prod.yml");

        assertThat(section(prod, "hiddenwhite", "api-docs").get("enabled"))
                .as("生产环境必须关闭接口文档放行开关")
                .isEqualTo(Boolean.FALSE);

        // 双保险：即使将来有人放宽了 SecurityConfig，文档也不该被生成出来。
        assertThat(section(prod, "springdoc", "api-docs").get("enabled"))
                .as("生产环境 springdoc 不应生成 /v3/api-docs")
                .isEqualTo(Boolean.FALSE);
        assertThat(section(prod, "springdoc", "swagger-ui").get("enabled"))
                .as("生产环境不应提供 swagger-ui")
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void devMustEnableApiDocs() throws Exception {
        Map<String, Object> dev = load("application-dev.yml");

        assertThat(section(dev, "hiddenwhite", "api-docs").get("enabled"))
                .as("本地开发需要 /doc.html 调接口，dev 必须显式打开")
                .isEqualTo(Boolean.TRUE);
    }
}
