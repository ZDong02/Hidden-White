<template>
  <view class="ui-stepper">
    <!-- 组件 wxss 内禁止使用标签名/属性选择器，故按钮与数值都改为 class 选择器；
         按压态用 hover-class（小程序 :active/[disabled] 不可靠，真机不生效） -->
    <button
      :class="['ui-stepper__btn', { 'is-disabled': value <= min || disabled }]"
      :disabled="value <= min || disabled"
      aria-label="减少数量"
      hover-class="ui-stepper__btn--press"
      :hover-stay-time="80"
      @tap="decrease"
    >
      <Icon name="minus" tone="ink" :size="30" />
    </button>
    <!-- 以值为 key：数值一变就重挂载，配合 number-pop 让数字「跳」一下，
         否则加减之后只是换个字形，眼睛很难确认操作生效了 -->
    <text :key="value" class="ui-stepper__value num">{{ value }}</text>
    <button
      :class="['ui-stepper__btn', { 'is-disabled': value >= max || disabled }]"
      :disabled="value >= max || disabled"
      aria-label="增加数量"
      hover-class="ui-stepper__btn--press"
      :hover-stay-time="80"
      @tap="increase"
    >
      <Icon name="plus" tone="ink" :size="30" />
    </button>
  </view>
</template>

<script setup>
import Icon from './Icon.vue'

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
  gap: $sp-2;
  flex-shrink: 0;
}

.ui-stepper__btn {
  width: $tap-min;
  height: $tap-min;
  border-radius: 50%;
  background: $c-surface;
  border: 1rpx solid $c-border-strong;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform $dur-instant $ease-out, background-color $dur-fast ease,
    border-color $dur-fast ease;
}

/* 原生 button 自带的 ::after 会画一圈默认边框，与上面的 border 叠成双层 */
.ui-stepper__btn::after {
  border: none;
}

/* 禁用态：底色下沉、描边消失，明确「到头了」而不是只把数字变灰。
   用 class 而不是 [disabled] 属性选择器：组件 wxss 不允许属性选择器。 */
.ui-stepper__btn.is-disabled {
  background: $c-surface-2 !important;
  border-color: $c-divider;
}

/* 按压态：由 hover-class 挂上（disabled 的 button 不会触发，无需 :not([disabled])） */
.ui-stepper__btn--press {
  transform: scale(0.94);
  background: $c-surface-2;
}

.ui-stepper__value {
  min-width: 48rpx;
  text-align: center;
  font-size: $fs-body;
  color: $c-ink-strong;
  animation: number-pop $dur-fast $ease-out both;
}

/* #ifdef H5 */
.ui-stepper__btn:hover:not(.is-disabled) {
  background: $c-surface-2;
  border-color: $c-ink-faint;
}
/* #endif */
</style>
