<template>
  <button
    :class="['ui-button', variant, { disabled }]"
    :disabled="disabled"
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
.ui-button {
  min-height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $r-md;
  font-size: $fs-title-sm;
  font-weight: 600;
  letter-spacing: 0.5rpx;
  transition: transform $dur-fast ease-out, background-color 200ms ease, color 200ms ease, box-shadow 200ms ease;
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

.ui-button:not(.disabled):active {
  transform: scale(0.97);
  opacity: 0.82;
}

/* #ifdef H5 */
.ui-button:not(.disabled):hover {
  box-shadow: $sh-2;
}
/* #endif */
</style>
