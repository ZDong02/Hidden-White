<template>
  <!-- 药丸形分段控件：滑块用 transform 位移跟随选中项，与内容区 swiper 同步滑动 -->
  <view class="seg-bar">
    <view class="seg-track">
      <view class="seg-thumb" :style="thumbStyle" />
      <view
        v-for="tab in tabs"
        :key="tab.id"
        :class="['seg-item', { active: active === tab.id }]"
        :aria-label="tab.label"
        hover-class="seg-item--press"
        :hover-stay-time="60"
        @tap="$emit('select', tab.id)"
      >
        <view class="seg-icon">
          <Icon
            :name="tab.icon"
            :tone="active === tab.id ? 'gold-on-dark' : 'muted'"
            :size="38"
          />
        </view>
        <text class="seg-label">{{ tab.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import Icon from './ui/Icon.vue'

const props = defineProps({
  active: {
    type: String,
    default: 'home'
  }
})

defineEmits(['select'])

// 图标统一走图标集（scripts/icons）：SVG 母版按色版预生成 PNG，
// 激活态直接换「深底金」色版，不再靠字形符号冒充图标。
const tabs = [
  { id: 'home', label: '首页', icon: 'home' },
  { id: 'orders', label: '订单', icon: 'receipt' },
  { id: 'member', label: '会员', icon: 'sparkle' },
  { id: 'profile', label: '我的', icon: 'user' }
]

const activeIndex = computed(() => Math.max(0, tabs.findIndex((tab) => tab.id === props.active)))

// translateX(100%) 即滑块自身宽度 = 一个分段的步进，左移右移天然正确
const thumbStyle = computed(() => ({ transform: `translateX(${activeIndex.value * 100}%)` }))
</script>

<style lang="scss" scoped>
.seg-bar {
  /* 与面板内容共用同一条横向留白，两栏边缘严格对齐 */
  padding: $sp-2 $gutter $sp-2;
  /* 透明背景：跟随壳页底部区域的画布色，避免出现白色断层 */
  background: transparent;
}

.seg-track {
  position: relative;
  display: flex;
  /* 触控目标：120rpx 轨道 - 16rpx×2 内边距 = 88rpx = 44px，达标 */
  height: 120rpx;
  padding: $sp-2;
  border-radius: $r-pill;
  background: $c-bg-sunken;
  border: 1rpx solid $c-border;
}

/* 选中块：宽度 = (轨道内宽)/4，translateX 一步一个分段 */
.seg-thumb {
  position: absolute;
  top: $sp-2;
  bottom: $sp-2;
  left: $sp-2;
  width: calc((100% - #{$sp-2 * 2}) / 4);
  border-radius: $r-pill;
  background: $c-dark;
  box-shadow: $sh-2;
  transition: transform 340ms $ease-out;
}

.seg-item {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-0;
  min-width: 0;
  /* 未选中态用 $c-ink-muted：对轨道底色约 6.2:1，比原先的 #75736a 更清晰 */
  color: $c-ink-muted;
  transition: color $dur-fast ease;
}

.seg-item.active {
  color: $c-gold-on-dark;
}

/* 激活时图标轻微上移并放大：让「选中」有物理反馈，而不只是换个颜色。
   动效挂在外层 view 上而不是 Icon 组件上——组件标签上的 class 在小程序端
   会落到组件宿主节点，能否命中最内层 image 依赖基础库版本，不值得赌。 */
.seg-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform $dur-base $ease-spring;
}

.seg-item.active .seg-icon {
  transform: translateY(-2rpx) scale(1.06);
}

.seg-item--press {
  opacity: 0.66;
}

.seg-label {
  font-size: $fs-micro;
  letter-spacing: 1rpx;
}

/* #ifdef H5 */
.seg-item:hover .seg-icon {
  transform: translateY(-2rpx);
}

.seg-item.active:hover .seg-icon {
  transform: translateY(-2rpx) scale(1.06);
}
/* #endif */
</style>
