<template>
  <button
    :class="['ui-button', variant, { disabled }]"
    :disabled="disabled"
    hover-class="ui-button--press"
    :hover-stay-time="60"
    @tap="handleTap"
  >
    <slot />
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
  }
})

const emit = defineEmits(['tap'])

const handleTap = (event) => {
  if (!props.disabled) {
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
  min-height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $r-md;
  font-size: $fs-title-sm;
  font-weight: 600;
  letter-spacing: 0.5rpx;
  transition: transform $dur-instant $ease-out, background-color $dur-fast ease,
    color $dur-fast ease, box-shadow $dur-base $ease-out, opacity $dur-fast ease;
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
  font-weight: 500;
  color: $c-ink;
}

.ui-button.text {
  background: transparent;
  color: $c-ink;
  box-shadow: none;
}

/* 按压：由 hover-class 挂上（小程序 :active 在真机不可靠） */
.ui-button--press {
  transform: scale(0.975);
  opacity: 0.88;
}

/* #ifdef H5 */
.ui-button:not(.disabled):hover {
  box-shadow: $sh-2;
}

.ui-button.primary:hover:not(.disabled) {
  box-shadow: $sh-3;
}
/* #endif */
</style>
