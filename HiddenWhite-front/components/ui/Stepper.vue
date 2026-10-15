<template>
  <view class="ui-stepper">
    <button :disabled="value <= min || disabled" @tap="decrease">−</button>
    <text>{{ value }}</text>
    <button :disabled="value >= max || disabled" @tap="increase">＋</button>
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

.ui-stepper button {
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

.ui-stepper button:not([disabled]):active {
  transform: scale(0.95);
}

.ui-stepper text {
  min-width: 44rpx;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* #ifdef H5 */
.ui-stepper button:not([disabled]):hover {
  background: $c-surface-2;
}
/* #endif */
</style>
