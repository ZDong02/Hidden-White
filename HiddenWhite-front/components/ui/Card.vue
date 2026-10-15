<template>
  <view :class="['ui-card', variant]" @tap="handleTap">
    <slot />
  </view>
</template>

<script setup>
const props = defineProps({
  variant: {
    type: String,
    default: 'default', // default, elevated, outlined
    validator: (value) => ['default', 'elevated', 'outlined'].includes(value)
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
.ui-card {
  border-radius: $r-lg;
  padding: $sp-5;
  background: $c-surface;
  animation: card-enter 350ms ease both;
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
</style>
