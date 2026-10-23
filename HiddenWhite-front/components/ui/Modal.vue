<template>
  <view v-if="visible" class="ui-modal-mask" @tap="handleMaskTap">
    <view class="ui-modal-content" @tap.stop>
      <view v-if="title" class="ui-modal-header">
        <text class="ui-modal-title">{{ title }}</text>
        <view
          v-if="closable"
          class="ui-modal-close"
          aria-label="关闭"
          hover-class="ui-modal-close--press"
          :hover-stay-time="60"
          @tap="handleClose"
        >
          <Icon name="close" tone="muted" :size="32" />
        </view>
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
import Icon from './Icon.vue'

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
/* 遮罩：淡入即可，不做位移——遮罩位移会让人误以为页面在滑动 */
.ui-modal-mask {
  position: fixed;
  inset: 0;
  z-index: $z-mask;
  background: $c-scrim;
  display: flex;
  align-items: flex-end;
  animation: modal-enter 200ms ease both;
}

/* 弹层本体：自下而上 + 轻微过冲收住，时长与遮罩错开，观感是「盒子弹上来」 */
.ui-modal-content {
  width: 100%;
  padding: $sp-6 $gutter calc(#{$sp-6} + env(safe-area-inset-bottom));
  border-radius: $r-xl $r-xl 0 0;
  background: $c-surface;
  box-shadow: $sh-3;
  max-height: 78vh;
  overflow-y: auto;
  animation: sheet-enter 300ms $ease-out both;
}

.ui-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-4;
  margin-bottom: $sp-4;
}

.ui-modal-title {
  font-size: $fs-title-lg;
  font-weight: 600;
  letter-spacing: $ls-normal;
  color: $c-ink-strong;
}

.ui-modal-close {
  display: flex;
  width: $tap-min;
  height: $tap-min;
  flex: 0 0 $tap-min;
  align-items: center;
  justify-content: center;
  /* 视觉上贴住标题行右缘，触控区仍是 88rpx */
  margin-right: -$sp-5;
  border-radius: 50%;
  transition: background-color $dur-fast ease, transform $dur-instant $ease-out;
}

.ui-modal-close--press {
  background: $c-surface-2;
  transform: scale(0.94);
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
