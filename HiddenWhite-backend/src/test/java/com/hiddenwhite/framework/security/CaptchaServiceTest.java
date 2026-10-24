package com.hiddenwhite.framework.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CaptchaService} 的安全契约：明文验证码不出服务端，图片里的字符与存储值一致。
 */
class CaptchaServiceTest {

    private static final Pattern TEXT_CHAR = Pattern.compile("<text[^>]*>([A-Z0-9])</text>");

    @Test
    void generateReturnsImageInsteadOfPlaintextCode() {
        CaptchaService service = new CaptchaService();
        var response = service.generate();

        // 接口只暴露 uuid + image；code 若随响应下发，验证码就形同虚设
        assertThat(response).containsOnlyKeys("uuid", "image");
        assertThat(response.get("uuid")).isNotBlank();
        assertThat(response.get("image")).startsWith("data:image/svg+xml;base64,");

        String svg = decodeSvg(response.get("image"));
        assertThat(svg).startsWith("<svg").contains("</text>");
    }

    @Test
    void charactersDrawnInImageMatchTheStoredCode() {
        CaptchaService service = new CaptchaService();
        var response = service.generate();

        StringBuilder drawn = new StringBuilder();
        Matcher matcher = TEXT_CHAR.matcher(decodeSvg(response.get("image")));
        while (matcher.find()) {
            drawn.append(matcher.group(1));
        }
        assertThat(drawn.length()).isEqualTo(4);

        // 图片里画出来的字符必须能通过校验（大小写不敏感），否则是真回归
        assertThat(service.verify(response.get("uuid"), drawn.toString())).isTrue();
    }

    @Test
    void verifyIsSingleShotAndRejectsWrongCode() {
        CaptchaService service = new CaptchaService();
        var response = service.generate();

        // O / 0 不在字符集里，OOO0 永远不可能是真验证码
        assertThat(service.verify(response.get("uuid"), "OOO0")).isFalse();
        // 取出即失效：错的尝试也耗掉这张验证码，杜绝枚举
        assertThat(service.verify(response.get("uuid"), "OOO0")).isFalse();
        assertThat(service.verify(null, "ABCD")).isFalse();
        assertThat(service.verify(response.get("uuid"), null)).isFalse();
    }

    private String decodeSvg(String dataUri) {
        String base64 = dataUri.substring("data:image/svg+xml;base64,".length());
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
}
