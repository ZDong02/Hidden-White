package com.hiddenwhite.shop.pay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付开发模式的profile 守卫测试。
 *
 * <p>这是全项目唯一拦住「假支付上生产」的关卡，因此逐个 profile 显式验证，
 * 任何一条回归都会导致真实门店免费出杯，必须当作高优先级测试对待。
 */
class PayDevModeGuardTest {

    private static PayDevModeProperties enabled() {
        PayDevModeProperties props = new PayDevModeProperties();
        props.setDevMode(true);
        return props;
    }

    @Test
    void devProfileAllowsDevMode() {
        assertThat(enabled().resolve("dev")).isTrue();
    }

    @Test
    void prodProfileBlocksStartup() {
        assertThatThrownBy(() -> enabled().resolve("prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只允许在 dev profile");
    }

    @Test
    void mixedDevProdProfileIsRejected() {
        // 混合 profile 说明这次是要跑生产的，dev 在列表里也不该放行
        assertThatThrownBy(() -> enabled().resolve("dev,prod"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaultProfileIsRejected() {
        // 未指定 profile 时 Spring 的默认值是 "default"，既非 dev 也非 prod
        assertThatThrownBy(() -> enabled().resolve("default"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankProfileIsRejected() {
        assertThatThrownBy(() -> enabled().resolve("  "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> enabled().resolve(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void devModeOffIsAlwaysSafe() {
        // 关闭状态下无论什么 profile 都放行——这是生产环境应有的常态
        PayDevModeProperties off = new PayDevModeProperties();
        off.setDevMode(false);
        assertThatCode(() -> {
            assertThat(off.resolve("prod")).isFalse();
            assertThat(off.resolve("dev")).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    void devTransactionIdIsClearlyMarked() {
        // 交易号前缀是对账时识别「非真实交易」的唯一依据，不能被改
        assertThat(PayDevModeProperties.DEV_TX_PREFIX).startsWith("DEV-");
    }
}