<template>
  <view>
    <!-- #ifdef H5 -->
    <view class="status-bar">
      <text>9:41</text>
      <view class="island" />
      <view class="status-right">
        <view class="signal">
          <view />
          <view />
          <view />
          <view />
        </view>
        <view class="status-wifi">
          <Icon name="wifi" tone="ink" :size="28" />
        </view>
        <view class="battery" />
      </view>
    </view>
    <!-- #endif -->

    <!-- #ifndef H5 -->
    <view class="native-status" />
    <!-- #endif -->

    <view v-if="title" class="page-header">
      <view
        class="back-button"
        aria-label="返回"
        hover-class="is-press"
        :hover-stay-time="60"
        @tap="handleBack"
      >
        <Icon name="arrow-left" tone="ink" :size="40" />
      </view>
      <text>{{ title }}</text>
      <view class="header-right">
        <slot name="right" />
      </view>
    </view>
  </view>
</template>

<script setup>
import Icon from './ui/Icon.vue'
import { navigateBack } from '../utils/navigation'

defineProps({
  title: {
    type: String,
    default: ''
  }
})

function handleBack() {
  navigateBack()
}
</script>

<style lang="scss" scoped>
/* 主体样式已在 common.scss 中定义 */
.status-wifi {
  margin-top: -2rpx;
}

.header-right {
  position: absolute;
  right: $sp-6;
  top: 50%;
  transform: translateY(-50%);
}
</style>
