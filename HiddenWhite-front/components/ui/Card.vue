<template>
  <view
    :class="['ui-card', variant, { 'ui-card--clickable': clickable }]"
    :hover-class="clickable ? 'ui-card--press' : 'none'"
    :hover-stay-time="60"
    @tap="handleTap"
  >
    <slot />
  </view>
</template>

<script setup>
const props = defineProps({
  variant: {
    type: String,
    default: 'default', // default, elevated, outlined, sunken
    validator: (value) => ['default', 'elevated', 'outlined', 'sunken'].includes(value)
  },
  clickable: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['tap'])

const handleTap = (event) => {
  if (props.clickable) {
    emit('tap', event)
  }
}
</script>

<style lang="scss" scoped>
/* 内容卡：全站统一 $r-lg 圆角 + $sp-5 内边距，与 common.scss 的 .card 同值 */
.ui-card {
  border-radius: $r-lg;
  padding: $sp-5;
  background: $c-surface;
  animation: card-enter 380ms $ease-out both;
  transition: box-shadow $dur-base $ease-out, border-color $dur-fast ease,
    transform $dur-instant $ease-out;
}

.ui-card.default {
  border: 1rpx solid $c-border;
  box-shadow: $sh-1;
}

.ui-card.elevated {
  box-shadow: $sh-2;
}

.ui-card.outlined {
  border: 1rpx solid $c-border-strong;
}

/* 下沉底：用于「信息块」而非「可点卡片」，避免每张卡都在抢注意力 */
.ui-card.sunken {
  border: 1rpx solid $c-border;
  background: $c-surface-2;
}

.ui-card--press {
  transform: scale(0.99);
}

/* #ifdef H5 */
.ui-card--clickable:hover {
  border-color: $c-border-strong;
  box-shadow: $sh-2;
}
/* #endif */
</style>
