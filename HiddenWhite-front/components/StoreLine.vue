<template>
  <view class="store-line" @tap="handleTap">
    <image :src="iconPin" />
    <text>{{ storeName }}</text>
    <text v-if="arrow" class="chevron">›</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { STORE, IMAGES } from '../config/constants'
import { storeInfo } from '../store/index'
import { showStoreInfo } from '../utils/navigation'

defineProps({
  arrow: {
    type: Boolean,
    default: true
  }
})

// 门店名称优先取接口数据（管理端可改），未加载到时回落本地常量
const storeName = computed(() => storeInfo.name || STORE.name)
const iconPin = IMAGES.icons.pin

function handleTap() {
  showStoreInfo()
}
</script>

