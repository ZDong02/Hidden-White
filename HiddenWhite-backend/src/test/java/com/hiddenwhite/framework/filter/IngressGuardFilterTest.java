package com.hiddenwhite.framework.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IngressGuardFilter} 的准入语义。
 *
 * <p>锁住三条不变式：
 * <ol>
 *   <li>未配置密钥（本地开发）时一律放行，不能把开发环境堵死；</li>
 *   <li>配置了密钥后，缺头与错值都必须 403，且响应体与 {@code R} 同构；</li>
 *   <li>CORS 预检（OPTIONS）不被拦，否则跨域场景整体失效。</li>
 * </ol>
 */
class IngressGuardFilterTest {

    private static final String SECRET = "gateway-secret-for-test";
    private static final String PATH = "/api/products";

    @Test
    void passesThroughWhenSecretNotConfigured() throws ServletException, IOException {
        IngressGuardFilter filter = new IngressGuardFilter("");

        MockHttpServletResponse response = pass(filter, null, "GET");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void rejectsRequestWithoutGatewayHeader() throws ServletException, IOException {
        IngressGuardFilter filter = new IngressGuardFilter(SECRET);

        MockHttpServletResponse response = pass(filter, null, "GET");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":403");
    }

    @Test
    void rejectsRequestWithWrongGatewayHeader() throws ServletException, IOException {
        IngressGuardFilter filter = new IngressGuardFilter(SECRET);

        MockHttpServletResponse response = pass(filter, SECRET + "-wrong", "GET");

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsRequestCarryingGatewayHeader() throws ServletException, IOException {
        IngressGuardFilter filter = new IngressGuardFilter(SECRET);

        MockHttpServletResponse response = pass(filter, SECRET, "GET");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void ignoresSurroundingWhitespaceInConfiguredSecret() throws ServletException, IOException {
        IngressGuardFilter filter = new IngressGuardFilter("  " + SECRET + "\n");

        assertThat(pass(filter, SECRET, "GET").getStatus()).isEqualTo(200);
    }

    @Test
    void letsCorsPreflightThrough() throws ServletException, IOException {
        IngressGuardFilter filter = new IngressGuardFilter(SECRET);

        // 浏览器发预检时不会带自定义头；拦掉它等于让所有跨域请求失败
        assertThat(pass(filter, null, "OPTIONS").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse pass(IngressGuardFilter filter, String gatewayHeader, String method)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, PATH);
        if (gatewayHeader != null) {
            request.addHeader(IngressGuardFilter.GATEWAY_HEADER, gatewayHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
