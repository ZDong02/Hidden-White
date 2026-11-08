package com.hiddenwhite.framework.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 后端暴露面测试：锁住「生产环境不直接对外」这件事。
 *
 * <p>与 {@link ApiDocsExposureTest} 同一思路——直接读 yml 原文，
 * 因为一次只能激活一个 profile，而这里要固定 prod 的形态。
 *
 * <p>三条不变式：
 * <ol>
 *   <li>prod 的监听地址必须是回环（或显式的内网地址），<b>不能是 0.0.0.0</b>；</li>
 *   <li>prod 必须声明入口守卫密钥项，且只能由环境变量注入（不落仓库）；</li>
 *   <li>base 配置不得把监听地址摊到所有网卡上，否则任何 profile 都会绕过反代。</li>
 * </ol>
 */
class IngressExposureTest {

    private static String text(String file) throws Exception {
        return Files.readString(Path.of("src", "main", "resources", file));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String file) throws Exception {
        try (InputStream input = Files.newInputStream(Path.of("src", "main", "resources", file))) {
            return new Yaml().load(input);
        }
    }

    private static Map<String, Object> server(Map<String, Object> root) {
        Object node = root.get("server");
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> gateway(Map<String, Object> root) {
        Object hiddenwhite = root.get("hiddenwhite");
        if (!(hiddenwhite instanceof Map)) {
            return Map.of();
        }
        Object node = ((Map<String, Object>) hiddenwhite).get("gateway");
        return node instanceof Map ? (Map<String, Object>) node : Map.of();
    }

    @Test
    void prodMustNotListenOnAllInterfaces() throws Exception {
        String address = String.valueOf(server(load("application-prod.yml")).get("address"));

        assertThat(address)
                .as("prod 必须绑定回环（或显式内网地址），否则局域网/公网可直连 8080，绕过反向代理")
                .contains("127.0.0.1")
                .doesNotContain("0.0.0.0");
    }

    @Test
    void prodMustDeclareGatewaySecretFromEnvironment() throws Exception {
        Map<String, Object> secret = gateway(load("application-prod.yml"));

        assertThat(secret)
                .as("prod 必须声明 hiddenwhite.gateway.secret，IngressGuardFilter 才会启用")
                .containsKey("secret");
        assertThat(String.valueOf(secret.get("secret")))
                .as("密钥只能由环境变量注入，不允许在仓库里写死")
                .contains("HW_GATEWAY_SECRET");

        // 反面：配置里不能出现具体的密钥字面量（防止有人图省事把值提交上来）
        assertThat(text("application-prod.yml"))
                .as("application-prod.yml 不应包含硬编码密钥")
                .doesNotContainPattern("secret:\\s*[0-9a-zA-Z]{16,}");
    }

    @Test
    void baseConfigMustNotExposeServerOnAllInterfaces() throws Exception {
        String address = String.valueOf(server(load("application.yml")).getOrDefault("address", ""));

        assertThat(address)
                .as("application.yml 对所有 profile 生效，不能在这里把监听地址摊到 0.0.0.0")
                .doesNotContain("0.0.0.0");
    }
}
