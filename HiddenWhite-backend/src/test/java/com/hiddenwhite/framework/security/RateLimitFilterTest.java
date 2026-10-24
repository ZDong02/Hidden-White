package com.hiddenwhite.framework.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RateLimitFilter} 的窗口与放行语义：窗口内超限即 429，窗口滑动后恢复，
 * 且只作用于认证入口路径。
 */
class RateLimitFilterTest {

    private static final String LOGIN_PATH = "/api/admin/auth/login";

    @Test
    void allowsUpToLimitThenRejectsWith429() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(3, 60_000);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = pass(filter, "10.0.0.1");
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejected = pass(filter, "10.0.0.1");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getContentAsString())
                .contains("\"code\":429")
                .contains("尝试过于频繁");
    }

    @Test
    void windowSlidesSoOldHitsExpire() {
        RateLimitFilter filter = new RateLimitFilter(2, 100);

        assertThat(filter.tryAcquire("10.0.0.2", 1_000)).isTrue();
        assertThat(filter.tryAcquire("10.0.0.2", 1_050)).isTrue();
        assertThat(filter.tryAcquire("10.0.0.2", 1_090)).isFalse();

        // 前两次命中已滑出 100ms 窗口，只剩一次在窗内
        assertThat(filter.tryAcquire("10.0.0.2", 1_101)).isTrue();
        assertThat(filter.tryAcquire("10.0.0.2", 1_102)).isFalse();
    }

    @Test
    void eachIpIsCountedIndependently() {
        RateLimitFilter filter = new RateLimitFilter(1, 60_000);

        assertThat(filter.tryAcquire("10.0.0.3", 1_000)).isTrue();
        assertThat(filter.tryAcquire("10.0.0.3", 1_001)).isFalse();
        // 另一个 IP 不受牵连
        assertThat(filter.tryAcquire("10.0.0.4", 1_001)).isTrue();
    }

    @Test
    void nonAuthPathsBypassTheFilter() throws ServletException, IOException {
        RateLimitFilter filter = new RateLimitFilter(1, 60_000);
        // 先把登录入口的额度打满
        assertThat(filter.tryAcquire("10.0.0.5", 1_000)).isTrue();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletResponse pass(RateLimitFilter filter, String ip)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_PATH);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);
        if (response.getStatus() == 200) {
            assertThat(chain.getRequest()).isNotNull();
        }
        return response;
    }
}
