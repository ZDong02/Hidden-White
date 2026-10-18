package com.hiddenwhite.shop.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 取餐号规则测试。
 *
 * <p>取餐号是门店叫号的唯一凭据，同号即意味着可能拿错餐——
 * 这是会直接造成顾客投诉与门店损耗的缺陷，因此边界值逐一覆盖。
 */
class PickupNumberTest {

    @Test
    void firstNumberOfDayStartsAtOne() {
        // 每日必须从 1 开始，而非像订单号那样从 1000 起
        assertThat(PickupNumber.nextAfter(0)).isEqualTo(1);
    }

    @Test
    void incrementsSequentially() {
        assertThat(PickupNumber.nextAfter(1)).isEqualTo(2);
        assertThat(PickupNumber.nextAfter(42)).isEqualTo(43);
        assertThat(PickupNumber.nextAfter(998)).isEqualTo(999);
    }

    @Test
    void wrapsAroundAtUpperBound() {
        // 达到 999 后回到 1，绝不能溢出到 1000
        assertThat(PickupNumber.nextAfter(PickupNumber.MAX)).isEqualTo(PickupNumber.MIN);
    }

    @Test
    void supportsThreeDigitsAsRequired() {
        assertThat(PickupNumber.MAX).isEqualTo(999);
        assertThat(PickupNumber.format(999)).isEqualTo("999");
    }

    @Test
    void padsToFixedWidth() {
        // 固定 3 位，前端无需处理变长字符串
        assertThat(PickupNumber.format(1)).isEqualTo("001");
        assertThat(PickupNumber.format(7)).isEqualTo("007");
        assertThat(PickupNumber.format(45)).isEqualTo("045");
        assertThat(PickupNumber.format(999)).isEqualTo("999");
    }

    @Test
    void rejectsOutOfRangeValues() {
        // 静默接受越界值会产出「000」这类非法号码，比抛错危险得多
        assertThatThrownBy(() -> PickupNumber.format(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PickupNumber.format(1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PickupNumber.format(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeLastUsedFallsBackToFirstNumber() {
        // 脏数据不应导致 NPE 或产生负号码
        assertThat(PickupNumber.nextAfter(-5)).isEqualTo(PickupNumber.MIN);
    }

    @Test
    void twoConsecutiveNumbersAreDistinct() {
        // 直接对着用户报的 bug 写回归：连续取号不得重复
        int first = PickupNumber.nextAfter(0);
        int second = PickupNumber.nextAfter(first);
        assertThat(second).isNotEqualTo(first);
    }
}