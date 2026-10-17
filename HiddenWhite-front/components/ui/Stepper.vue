<template>
  <view class="ui-stepper">
    <!-- 组件 wxss 内禁止使用标签名/属性选择器，故按钮与数值都改为 class 选择器；
         按压态用 hover-class（小程序 :active/[disabled] 不可靠，真机不生效） -->
    <button
      class="ui-stepper__btn"
      :disabled="value <= min || disabled"
      hover-class="ui-stepper__btn--press"
      :hover-stay-time="80"
      @tap="decrease"
    >−</button>
    <text class="ui-stepper__value">{{ value }}</text>
    <button
      class="ui-stepper__btn"
      :disabled="value >= max || disabled"
      hover-class="ui-stepper__btn--press"
      :hover-stay-time="80"
      @tap="increase"
    >＋</button>
  </view>
</template>

<script setup>
const props = defineProps({
  value: {
    type: Number,
    required: true
  },
  min: {
    type: Number,
    default: 1
  },
  max: {
    type: Number,
    default: 999
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:value', 'change'])

const decrease = () => {
  if (props.value > props.min && !props.disabled) {
    const newValue = props.value - 1
    emit('update:value', newValue)
    emit('change', newValue)
  }
}

const increase = () => {
  if (props.value < props.max && !props.disabled) {
    const newValue = props.value + 1
    emit('update:value', newValue)
    emit('change', newValue)
  }
}
</script>

<style lang="scss" scoped>
.ui-stepper {
  display: flex;
  align-items: center;
  gap: $sp-3;
  flex-shrink: 0;
}

.ui-stepper__btn {
  width: $tap-min;
  height: $tap-min;
  border-radius: 50%;
  background: $c-surface;
  color: $c-ink;
  font-size: 36rpx;
  border: 1rpx solid $c-border-strong;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform $dur-fast ease, background-color $dur-fast ease;
}

/* 原生 button 自带的 ::after 会画一圈默认边框，与上面的 border 叠成双层 */
.ui-stepper__btn::after {
  border: none;
}

/* 按压态：由 hover-class 挂上（disabled 的 button 不会触发，无需 :not([disabled])） */
.ui-stepper__btn--press {
  transform: scale(0.95);
}

.ui-stepper__value {
  min-width: 44rpx;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* #ifdef H5 */
.ui-stepper__btn:hover {
  background: $c-surface-2;
}
/* #endif */
</style>
