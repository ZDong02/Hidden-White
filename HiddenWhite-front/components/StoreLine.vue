<template>
  <!-- 整行即地图入口：点击直接打开地图页面并标记门店位置 -->
  <view
    class="store-line"
    :hover-class="'is-press'"
    :hover-stay-time="60"
    @tap="handleTap"
  >
    <Icon name="pin" tone="ink" :size="36" />
    <text>{{ storeName }}</text>
    <view v-if="arrow" class="store-line__chevron">
      <Icon name="chevron-right" tone="faint" :size="28" />
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import Icon from './ui/Icon.vue'
import { STORE } from '../config/constants'
import { storeInfo } from '../store/index'
import { openStoreMap } from '../utils/navigation'

defineProps({
  arrow: {
    type: Boolean,
    default: true
  }
})

// 门店名称优先取接口数据（管理端可改），未加载到时回落本地常量
const storeName = computed(() => storeInfo.name || STORE.name)

// 经纬度随门店信息一起传入：接口值优先，缺失时由 openStoreMap 回落本地常量，
// 两份都不合法则走「地址弹窗 + 复制」降级，不会出现「点了没反应」。
function handleTap() {
  openStoreMap({
    name: storeName.value,
    address: storeInfo.address,
    latitude: storeInfo.latitude,
    longitude: storeInfo.longitude
  })
}
</script>
