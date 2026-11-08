<template>
  <view v-if="visible" class="ui-dialog-mask" @tap="handleMaskTap">
    <view class="ui-dialog" @tap.stop>
      <text v-if="title" class="ui-dialog-title">{{ title }}</text>

      <view class="ui-dialog-body">
        <text v-if="content" class="ui-dialog-text">{{ content }}</text>
        <slot />

        <!-- 输入模式：与全站输入框同一个外壳（描边 + 金色聚焦光晕） -->
        <view v-if="editable" class="ui-dialog-field">
          <Input
            v-model="draft"
            :placeholder="placeholder"
            :maxlength="maxlength"
          />
        </view>
      </view>

      <view class="ui-dialog-actions">
        <view
          v-if="showCancel"
          class="ui-dialog-btn ui-dialog-btn--cancel"
          hover-class="ui-dialog-btn--press"
          :hover-stay-time="60"
          @tap="handleCancel"
        >
          <text>{{ cancelText }}</text>
        </view>
        <view
          :class="['ui-dialog-btn', 'ui-dialog-btn--confirm', { 'is-danger': danger }]"
          hover-class="ui-dialog-btn--press"
          :hover-stay-time="60"
          @tap="handleConfirm"
        >
          <text>{{ confirmText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'
import Input from './Input.vue'

/**
 * 居中对话框：提示 / 确认 / 输入三种形态共用一套骨架。
 *
 * 与 ui/Modal.vue 的分工：Modal 是底部抽屉，用于承载「展开看/选」的内容
 * （选券、升杯说明、生日礼遇）；Dialog 是居中卡片，用于「一句话 + 表态」，
 * 选择成本低、必须给明确答案的场景。两者共用同一套遮罩、圆角、阴影与动效令牌，
 * 所以视觉上仍是同一族。
 */
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  content: {
    type: String,
    default: ''
  },
  confirmText: {
    type: String,
    default: '确定'
  },
  cancelText: {
    type: String,
    default: '取消'
  },
  showCancel: {
    type: Boolean,
    default: true
  },
  /** 主操作为破坏性动作（取消订单、退出登录）：确认键换成语义危险色 */
  danger: {
    type: Boolean,
    default: false
  },
  /** 输入模式 */
  editable: {
    type: Boolean,
    default: false
  },
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  maxlength: {
    type: Number,
    default: 100
  },
  maskClosable: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['confirm', 'cancel'])

const draft = ref('')

/* 打开时把初值灌进输入框。
   同时盯 modelValue：连续弹两个输入框时 visible 一直是 true，
   只盯 visible 会让第二个弹窗沿用上一次的草稿。 */
watch(
  () => [props.visible, props.modelValue],
  ([visible, value]) => {
    if (visible) draft.value = value || ''
  }
)

const handleConfirm = () => {
  emit('confirm', props.editable ? draft.value.trim() : undefined)
}

const handleCancel = () => {
  emit('cancel')
}

const handleMaskTap = () => {
  if (props.maskClosable) handleCancel()
}
</script>

<style lang="scss" scoped>
/* 遮罩：只淡入，不做位移——遮罩自身移动会让人误以为页面在滑动 */
.ui-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: $z-mask;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $sp-7;
  background: $c-scrim;
  animation: modal-enter 200ms ease both;
}

/* 卡片：居中、留白充足，宽度上限避免在 H5 宽屏上拉成一条 */
.ui-dialog {
  width: 100%;
  max-width: 600rpx;
  max-height: 72vh;
  padding: $sp-6 $sp-6 $sp-5;
  overflow-y: auto;
  border-radius: $r-xl;
  background: $c-surface;
  box-shadow: $sh-3;
  animation: dialog-enter $dur-base $ease-out both;
}

.ui-dialog-title {
  display: block;
  color: $c-ink-strong;
  font-size: $fs-title-lg;
  font-weight: 600;
  line-height: $lh-tight;
  letter-spacing: $ls-normal;
  text-align: center;
}

.ui-dialog-body {
  margin-top: $sp-3;
}

.ui-dialog-text {
  display: block;
  /* 文案里带 \n 分段（地址 + 提示），必须保留换行，否则挤成一坨 */
  white-space: pre-line;
  color: $c-ink-muted;
  font-size: $fs-body-sm;
  line-height: $lh-body;
  text-align: center;
}

.ui-dialog-field {
  margin-top: $sp-4;
  text-align: left;
}

/* 按钮行：取消在左、主操作在右，与「左退右进」的习惯一致 */
.ui-dialog-actions {
  display: flex;
  gap: $sp-3;
  margin-top: $sp-5;
}

.ui-dialog-btn {
  flex: 1 1 0;
  display: flex;
  min-height: $tap-min;
  align-items: center;
  justify-content: center;
  border-radius: $r-md;
  font-size: $fs-body;
  font-weight: 600;
  transition: transform $dur-instant $ease-out, opacity $dur-fast ease;
}

.ui-dialog-btn--cancel {
  border: 1rpx solid $c-border-strong;
  background: $c-surface;
  color: $c-ink;
}

.ui-dialog-btn--confirm {
  background: $c-dark;
  color: $c-gold-on-dark;
  box-shadow: $sh-2;
}

/* 破坏性主操作：白字在 $c-danger 上约 6:1，够读且不抢金的地位（金只出现在普通主操作） */
.ui-dialog-btn--confirm.is-danger {
  background: $c-danger;
  color: $c-surface;
}

.ui-dialog-btn--press {
  transform: scale(0.97);
  opacity: 0.9;
}

/* #ifdef H5 */
.ui-dialog-btn--cancel:hover {
  background: $c-surface-2;
}

.ui-dialog-btn--confirm:hover {
  box-shadow: $sh-3;
}
/* #endif */

/* 入场：轻微上浮 + 收住尺寸，观感是「卡片从遮罩里立起来」 */
@keyframes dialog-enter {
  from {
    opacity: 0;
    transform: translateY(24rpx) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
