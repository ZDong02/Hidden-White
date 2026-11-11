<template>
  <button
    :class="['ui-button', variant, { 'is-disabled': disabled, 'is-loading': loading }]"
    :disabled="disabled"
    :hover-class="loading || disabled ? 'none' : 'ui-button--press'"
    :hover-stay-time="60"
    @tap="handleTap"
  >
    <view v-if="loading" class="ui-button__spinner" />
    <view class="ui-button__label"><slot /></view>
  </button>
</template>

<script setup>
const props = defineProps({
  variant: {
    type: String,
    default: 'primary', // primary, outline, text
    validator: (value) => ['primary', 'outline', 'text'].includes(value)
  },
  disabled: {
    type: Boolean,
    default: false
  },
  /* 提交中：显示 spinner 并锁住重复点击。
     刻意不把 loading 并进原生 disabled —— common.scss 里 button[disabled] 会把底色
     强制刷成 $c-surface-3、字色刷成 $c-ink-faint，深底金字的 primary 按钮会当场变成
     一块灰板，spinner 用 currentColor 也一起变灰，反而看不出「正在处理」。
     所以视觉态交给 .is-loading，防重复点击靠 handleTap 里的守卫 + pointer-events。 */
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['tap'])

const handleTap = (event) => {
  if (!props.disabled && !props.loading) {
    emit('tap', event)
  }
}
</script>

<style lang="scss" scoped>
/* 三档按钮共用同一套高度/圆角/过渡，只在底色与字色上分档：
   primary  深底金字，一屏只出现一个（主操作）
   outline  白底描边，用于并列的次级操作
   text     纯文字，用于弱化到几乎不该抢视线的操作 */
.ui-button {
  position: relative; // 加载态的 spinner 绝对居中，需要可定位的父级
  min-height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $r-md;
  font-size: $fs-title-sm;
  font-weight: $fw-semibold;
  letter-spacing: 0.5rpx;
  /* v2: 使用弹性曲线，增强按压反馈 */
  transition: transform $dur-instant cubic-bezier(0.34, 1.4, 0.64, 1),
    background-color $dur-fast ease,
    color $dur-fast ease,
    box-shadow $dur-base $ease-out,
    opacity $dur-fast ease;
}

/* 文字单独成层：加载态只淡化文字，spinner 保持原色 */
.ui-button__label {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity $dur-fast ease;
}

.ui-button.primary {
  background: $c-dark;
  color: $c-gold-on-dark;
  box-shadow: $sh-2;
}

.ui-button.outline {
  border: 1rpx solid $c-border-strong;
  background: $c-surface;
  min-height: $tap-min;
  font-size: $fs-caption;
  font-weight: $fw-medium;
  color: $c-ink;
}

.ui-button.text {
  background: transparent;
  color: $c-ink;
  box-shadow: none;
}

/* 加载态：锁住指针、隐去文字、转起 spinner。
   文字用 opacity: 0 而不是 visibility: hidden / display: none —— 前者仍占据原来的
   盒宽，按钮不会因为文字消失而横向缩一截（spinner 是绝对定位的，不占位）；
   后者会让非整宽按钮在 loading 一开就缩边，底栏布局跟着抖。 */
.ui-button.is-loading {
  pointer-events: none;
}

.ui-button.is-loading .ui-button__label {
  opacity: 0;
}

.ui-button__spinner {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 32rpx;
  height: 32rpx;
  margin: -16rpx 0 0 -16rpx;
  border: 4rpx solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: ui-button-spin 700ms linear infinite;
}

@keyframes ui-button-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 按压：由 hover-class 挂上（小程序 :active 在真机不可靠） */
.ui-button--press {
  transform: scale(0.96); // v2: 从 0.975 加大压缩感
  opacity: 0.88;
}

/* #ifdef H5 */
.ui-button:not(.is-disabled):hover {
  box-shadow: $sh-2;
}

.ui-button.primary:hover:not(.is-disabled):not(.is-loading) {
  box-shadow: $sh-3;
}
/* #endif */
</style>
