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
        <image :src="iconWifi" />
        <view class="battery" />
      </view>
    </view>
    <!-- #endif -->

    <!-- #ifndef H5 -->
    <view class="native-status" />
    <!-- #endif -->

    <view v-if="title" class="page-header">
      <view class="back-button" @tap="handleBack">
        <image :src="iconBack" />
      </view>
      <text>{{ title }}</text>
      <view class="header-right">
        <slot name="right" />
      </view>
    </view>
  </view>
</template>

<script setup>
import { IMAGES } from '../config/constants'
import { navigateBack } from '../utils/navigation'

defineProps({
  title: {
    type: String,
    default: ''
  }
})

const iconWifi = IMAGES.icons.wifi
const iconBack = IMAGES.icons.back

function handleBack() {
  navigateBack()
}
</script>

<style lang="scss" scoped>
/* 样式已在 common.scss 中定义 */
.header-right {
  position: absolute;
  right: $sp-6;
  top: 50%;
  transform: translateY(-50%);
}
</style>
