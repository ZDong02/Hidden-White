<template>
  <!--
    滑动确认胶囊：替换原「确认已取餐」按钮。

    设计语言沿用全站「珍珠玻璃 + 黑金」体系，不引入新的配色/圆角/阴影：
      · 轨道 88rpx = $tap-min，与原 outline 按钮同高
      · 圆角 $r-pill，与 MainTabBar / 进度分段同属药丸语汇
      · 字号 $fs-caption、字重 500，与原 outline 按钮一致
      · 滑块用 $c-dark + $c-gold-on-dark，即 primary 按钮的配色，暗示「这是主操作」
      · 完成态整条转为深底金字，与「确认已取餐」完成后的语义一致
  -->
  <view
    class="slide-confirm"
    :class="[
      completed ? 'slide-confirm--done' : '',
      dragging ? 'slide-confirm--dragging' : '',
      disabled ? 'slide-confirm--disabled' : ''
    ]"
    role="slider"
    :aria-label="ariaLabel"
    :aria-valuenow="progressPercent"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-disabled="disabled || completed"
    :tabindex="disabled || completed ? -1 : 0"
    @touchstart="onTouchStart"
    @touchmove.stop.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchCancel"
    @mousedown="onMouseDown"
    @keydown="onKeyDown"
  >
    <!-- 进度填充：随滑动增长，达到阈值时转为实心金，提示「松手即完成」 -->
    <view class="slide-confirm__fill" :style="fillStyle" />

    <!-- 轨道文案：常态提示滑动，蓄力态改为「松开确认」，完成后定格 -->
    <text class="slide-confirm__label">{{ displayLabel }}</text>

    <!-- 滑块 -->
    <view class="slide-confirm__knob" :style="knobStyle" hover-class="slide-confirm__knob--press">
      <view
        class="slide-confirm__glyph"
        :class="completed ? 'slide-confirm__glyph--check' : 'slide-confirm__glyph--arrow'"
      />
    </view>
  </view>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  /** 确认后的终态文案 */
  doneText: { type: String, default: '已取餐' },
  /** 未滑动时的提示文案 */
  hintText: { type: String, default: '滑动确认已取餐' },
  /** 达到阈值、等待松手时的提示文案 */
  armedText: { type: String, default: '松开确认' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['confirm'])

/** 判定阈值：滑行距离达到轨道的 80% 即视为确认 */
const THRESHOLD = 0.8

const instance = getCurrentInstance()

const trackWidth = ref(0)
const knobWidth = ref(0)
const offset = ref(0)
const dragging = ref(false)
const completed = ref(false)
/** 已发出 confirm 但父组件尚未完成异步收尾时，锁住重复触发 */
const submitting = ref(false)
const startX = ref(0)
const startOffset = ref(0)
/** 多点触控时只认第一根手指，避免双指互相抢位置 */
let activeTouchId = null

const maxOffset = computed(() => Math.max(0, trackWidth.value - knobWidth.value))
const progress = computed(() => (maxOffset.value > 0 ? offset.value / maxOffset.value : 0))
const progressPercent = computed(() => Math.round(progress.value * 100))
/** 已蓄力但尚未松手 */
const armed = computed(() => !completed.value && progress.value >= THRESHOLD)

const displayLabel = computed(() => {
  if (completed.value) return props.doneText
  return armed.value ? props.armedText : props.hintText
})

const ariaLabel = computed(() => (completed.value ? props.doneText : `${props.hintText}，向右滑动`))

const fillStyle = computed(() => {
  // 填充宽度跟住滑块右缘，视觉上「已走过」的部分被点亮
  const width = knobWidth.value + offset.value
  if (completed.value) return ''
  // 蓄力态加深、常态极浅：用同一支金色调透明度，避免引入第二种强调色
  return armed.value
    ? `width: ${width}px; background: linear-gradient(90deg, rgba(212, 173, 47, 0.3), rgba(212, 173, 47, 0.62));`
    : `width: ${width}px; background: linear-gradient(90deg, rgba(212, 173, 47, 0.1), rgba(212, 173, 47, 0.26));`
})

const knobStyle = computed(() => `transform: translateX(${offset.value}px);`)

// ---- 尺寸测量 ----

function measure() {
  // 用 class 选择器：组件 wxss 禁用标签名选择器，这里保持一致的定位方式
  uni.createSelectorQuery()
    .in(instance.proxy)
    .select('.slide-confirm')
    .boundingClientRect()
    .select('.slide-confirm__knob')
    .boundingClientRect()
    .exec(([track, knob]) => {
      if (track) trackWidth.value = track.width
      if (knob) knobWidth.value = knob.width
    })
}

onMounted(() => {
  // swiper 内的面板挂载后布局才稳定，下一拍再量
  measure()
})

// 尺寸变化（横竖屏切换 / 窗口缩放）后重新计算可滑动距离
// #ifdef H5
function onResize() {
  measure()
}

onMounted(() => window.addEventListener('resize', onResize))
// #endif

// ---- 触摸拖拽（小程序主路径 / 移动端 H5）----

function beginDrag(clientX) {
  // 终态不可回退：完成后不再接受任何拖动
  if (completed.value || props.disabled || dragging.value) return false
  dragging.value = true
  startX.value = clientX
  startOffset.value = offset.value
  return true
}

function moveDrag(clientX) {
  if (!dragging.value) return
  // 跟手：位移直接跟随手指，并夹紧在轨道内，避免拖出边界
  const next = startOffset.value + (clientX - startX.value)
  offset.value = Math.min(Math.max(next, 0), maxOffset.value)
}

function endDrag() {
  if (!dragging.value) return
  dragging.value = false

  // 达到 80% 即判定完成；否则回弹到起点并保持可再次拖动
  if (progress.value >= THRESHOLD) {
    lockComplete()
  } else {
    offset.value = 0
  }
}

function lockComplete() {
  // 已完成则忽略，避免快速反复拖动造成重复触发
  if (completed.value || submitting.value) return
  offset.value = maxOffset.value
  completed.value = true
  submitting.value = true
  emit('confirm')
}

/** 父组件处理完（或失败需要重试）后调用：failed=true 时解锁回可拖动态 */
function settle(failed = false) {
  submitting.value = false
  if (failed) {
    completed.value = false
    offset.value = 0
    measure()
  }
}

function onTouchStart(event) {
  const touch = event.touches[0]
  if (!touch) return
  if (activeTouchId === null) activeTouchId = touch.identifier
  if (touch.identifier !== activeTouchId) return
  beginDrag(touch.clientX)
}

function onTouchMove(event) {
  const touch = event.touches[0]
  if (!touch || touch.identifier !== activeTouchId) return
  moveDrag(touch.clientX)
}

function onTouchEnd(event) {
  const touch = event.changedTouches[0]
  if (touch && touch.identifier !== activeTouchId) return
  activeTouchId = null
  endDrag()
}

function onTouchCancel() {
  activeTouchId = null
  dragging.value = false
  offset.value = 0
}

// ---- 鼠标（桌面端 H5）----
// 小程序不存在鼠标事件，绑定无害；桌面浏览器无触摸事件，必须走这一条路径

function onMouseDown(event) {
  if (!beginDrag(event.clientX)) return
  event.preventDefault()
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(event) {
  moveDrag(event.clientX)
}

function onMouseUp() {
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  endDrag()
}

// ---- 键盘（可访问性：滑动确认不应把纯键盘用户挡在门外）----

function onKeyDown(event) {
  if (completed.value || props.disabled) return
  const step = maxOffset.value * 0.2

  if (event.key === 'ArrowRight') {
    event.preventDefault()
    offset.value = Math.min(offset.value + step, maxOffset.value)
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    offset.value = Math.max(offset.value - step, 0)
  } else if (event.key === 'Home') {
    event.preventDefault()
    offset.value = 0
  } else if (event.key === 'End') {
    event.preventDefault()
    offset.value = maxOffset.value
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    if (progress.value >= THRESHOLD) lockComplete()
  }
}

onBeforeUnmount(() => {
  // #ifdef H5
  window.removeEventListener('resize', onResize)
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
  // #endif
})

defineExpose({ settle, reset: () => { completed.value = false; submitting.value = false; offset.value = 0 } })
</script>

<style lang="scss" scoped>
/*
 * 全部使用 class 选择器：组件 wxss 禁用标签名 / id / 属性选择器。
 * 按压态用 hover-class（小程序 :active 不可靠）。
 */
.slide-confirm {
  position: relative;
  display: flex;
  align-items: center;
  height: $tap-min;
  box-sizing: border-box;
  overflow: hidden;
  border: 1rpx solid $c-border-strong;
  border-radius: $r-pill;
  background: $c-bg-sunken;
  /* 归位回弹用；拖动中由 --dragging 关掉过渡以保证跟手 */
  transition: background-color $dur-base ease, border-color $dur-base ease;
}

.slide-confirm--dragging {
  /* 跟手期间必须零过渡，否则手指与滑块之间会出现延迟 */
  transition: none;
}

.slide-confirm--disabled {
  opacity: 0.5;
}

/* 进度填充：绝对定位在底层，被滑块压住 */
.slide-confirm__fill {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 0;
  border-radius: $r-pill;
  transition: width $dur-base $ease-out;
}

.slide-confirm--dragging .slide-confirm__fill {
  transition: none;
}

.slide-confirm__label {
  position: absolute;
  left: 0;
  right: 0;
  text-align: center;
  font-size: $fs-caption;
  font-weight: 500;
  color: $c-ink-muted;
  /* 文字不参与拖拽命中，避免抢走滑块的事件 */
  pointer-events: none;
  transition: color $dur-base ease;
}

.slide-confirm__knob {
  position: relative;
  flex: 0 0 auto;
  width: 80rpx;
  height: 80rpx;
  margin: 4rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: $c-dark;
  box-shadow: $sh-2;
  will-change: transform;
  /* 归位回弹曲线；拖动中关闭以保证跟手 */
  transition: transform 420ms $ease-out;
}

.slide-confirm--dragging .slide-confirm__knob {
  transition: none;
}

.slide-confirm__knob--press {
  transform: scale(0.92);
}

.slide-confirm__knob--press,
.slide-confirm--dragging .slide-confirm__knob--press {
  /* 按压缩放与拖动位移叠加会互相覆盖，拖动中不再叠加缩放 */
  transform: scale(0.92);
}

/* 箭头：两条边框拼出的雪佛龙，不依赖图标字体 */
.slide-confirm__glyph--arrow {
  width: 14rpx;
  height: 14rpx;
  border-top: 3rpx solid $c-gold-on-dark;
  border-right: 3rpx solid $c-gold-on-dark;
  transform: rotate(45deg);
  margin-left: -4rpx;
}

/* 对勾：两边框旋转 45° 组成 */
.slide-confirm__glyph--check {
  width: 12rpx;
  height: 22rpx;
  border-right: 3rpx solid $c-gold-on-dark;
  border-bottom: 3rpx solid $c-gold-on-dark;
  transform: rotate(45deg);
  margin-top: -6rpx;
}

/* ---- 完成态：终态锁定，深底金字，与 primary 按钮同一套配色 ---- */

.slide-confirm--done {
  background: $c-dark;
  border-color: $c-gold-line;
}

.slide-confirm--done .slide-confirm__label {
  color: $c-gold-on-dark;
  /* 完成文案左移给滑块让位，避免与右侧滑块叠字 */
  padding-left: 56rpx;
}

.slide-confirm--done .slide-confirm__knob {
  background: $c-dark-2;
  border: 1rpx solid $c-gold-line;
  box-shadow: none;
}
</style>
