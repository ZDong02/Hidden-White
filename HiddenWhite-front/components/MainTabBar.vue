<template>
  <!-- 药丸形分段控件：滑块用 transform 位移跟随选中项，与内容区 swiper 同步滑动 -->
  <view class="seg-bar">
    <view class="seg-track">
      <view class="seg-thumb" :style="thumbStyle" />
      <view
        v-for="tab in tabs"
        :key="tab.id"
        :class="['seg-item', { active: active === tab.id }]"
        @tap="$emit('select', tab.id)"
      >
        <text :class="['seg-glyph', `${tab.id}-icon`]">{{ tab.glyph }}</text>
        <text class="seg-label">{{ tab.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  active: {
    type: String,
    default: 'home'
  }
})

defineEmits(['select'])

// 统一使用文字符号而非位图图标：滑块是近黑色，位图无法随激活态变成金色，
// 文字符号可以直接继承分段的前景色，激活/默认两态都干净。
const tabs = [
  { id: 'home', label: '首页', glyph: '⌂' },
  { id: 'orders', label: '订单', glyph: '☰' },
  { id: 'member', label: '会员', glyph: '✦' },
  { id: 'profile', label: '我的', glyph: '◉' }
]

const activeIndex = computed(() => Math.max(0, tabs.findIndex((tab) => tab.id === props.active)))

// translateX(100%) 即滑块自身宽度 = 一个分段的步进，左移右移天然正确
const thumbStyle = computed(() => ({ transform: `translateX(${activeIndex.value * 100}%)` }))
</script>

<style lang="scss" scoped>
.seg-bar {
  /* 左右各留 40rpx：胶囊宽 670rpx，介于满屏与之前过窄之间 */
  padding: $sp-2 40rpx $sp-2;
  /* 透明背景：跟随壳页底部区域的画布色，避免出现白色断层 */
  background: transparent;
}

.seg-track {
  position: relative;
  display: flex;
  /* 触控目标：120rpx 轨道 - 12rpx×2 内边距 = 96rpx = 48px，达标 */
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
  gap: 4rpx;
  min-width: 0;
  /* 未选中态用 $c-ink-muted：对轨道底色约 6.2:1，比原先的 #75736a 更清晰 */
  color: $c-ink-muted;
  transition: color 240ms ease;
}

.seg-item.active {
  color: $c-gold-on-dark;
}

.seg-glyph {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  line-height: 1;
}

.home-icon {
  font-size: 46rpx;
}

.member-icon {
  font-size: 36rpx;
}

.seg-label {
  font-size: 22rpx;
  letter-spacing: 1rpx;
}
</style>
