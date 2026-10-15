<template>
  <view v-if="visible" class="ui-modal-mask" @tap="handleMaskTap">
    <view class="ui-modal-content" @tap.stop>
      <view v-if="title" class="ui-modal-header">
        <text class="ui-modal-title">{{ title }}</text>
        <text v-if="closable" class="ui-modal-close" @tap="handleClose">×</text>
      </view>
      <view class="ui-modal-body">
        <slot />
      </view>
      <view v-if="$slots.footer" class="ui-modal-footer">
        <slot name="footer" />
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  closable: {
    type: Boolean,
    default: true
  },
  maskClosable: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:visible', 'close'])

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const handleMaskTap = () => {
  if (props.maskClosable) {
    handleClose()
  }
}
</script>

<style lang="scss" scoped>
.ui-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: rgba(18, 14, 7, 0.5);
  display: flex;
  align-items: flex-end;
  animation: modal-enter 200ms ease both;
}

.ui-modal-content {
  background: $c-surface;
  width: 100%;
  padding: $sp-6 $gutter $sp-8;
  border-radius: $r-xl $r-xl 0 0;
  max-height: 78vh;
  overflow-y: auto;
  animation: sheet-enter 240ms $ease-out both;
}

.ui-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  margin-bottom: $sp-5;
}

.ui-modal-title {
  font-size: $fs-title-lg;
  font-weight: 600;
  color: $c-ink-strong;
}

.ui-modal-close {
  display: flex;
  width: $tap-min;
  height: $tap-min;
  flex: 0 0 $tap-min;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: $c-ink-muted;
  font-size: 48rpx;
  line-height: 1;
  transition: background-color $dur-fast ease, color $dur-fast ease;
}

.ui-modal-close:active {
  background: $c-surface-2;
  color: $c-ink-strong;
}

.ui-modal-body {
  color: $c-ink;
}

.ui-modal-footer {
  margin-top: $sp-5;
  padding-top: $sp-4;
  border-top: 1rpx solid $c-divider;
}

/* #ifdef H5 */
.ui-modal-close:hover {
  background: $c-surface-2;
}
/* #endif */
</style>
