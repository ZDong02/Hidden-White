<template>
  <view class="ui-input-wrapper">
    <text v-if="label" class="ui-input-label">{{ label }}</text>
    <view :class="['ui-input-shell', { 'is-focus': focused, 'is-error': !!error }]">
      <input
        v-model="localValue"
        class="ui-input"
        :type="masked ? 'text' : type"
        :password="masked"
        :maxlength="maxlength"
        :placeholder="placeholder"
        placeholder-class="ui-input-placeholder"
        @input="handleInput"
        @focus="focused = true"
        @blur="handleBlur"
      />
    </view>
    <!-- 错误文案占位：无错误时保留高度，避免报错瞬间整页跳动 -->
    <text class="ui-input-error">{{ error || ' ' }}</text>
  </view>
</template>

<script setup>
import { ref, watch, computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  label: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'text'
  },
  maxlength: {
    type: Number,
    default: 100
  },
  placeholder: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'blur'])

/* 密码掩码：小程序端 input 的 type 不支持 password 值，
   必须用独立的 password 属性；H5 端该属性同样生效 */
const masked = computed(() => props.type === 'password')

const localValue = ref(props.modelValue)
const focused = ref(false)

watch(() => props.modelValue, (newValue) => {
  localValue.value = newValue
})

const handleInput = () => {
  emit('update:modelValue', localValue.value)
}

const handleBlur = () => {
  focused.value = false
  emit('blur', localValue.value)
}
</script>

<style lang="scss" scoped>
.ui-input-wrapper {
  margin-bottom: $sp-4;
}

.ui-input-label {
  display: block;
  margin-bottom: $sp-2;
  color: $c-ink-muted;
  font-size: $fs-caption;
  letter-spacing: $ls-normal;
}

/* 外壳负责描边与光晕，input 自己只负责文字：
   小程序 input 的 border 在聚焦/失焦切换上不稳定，改由外层 class 控制更可靠。 */
.ui-input-shell {
  border: 1rpx solid $c-border-strong;
  border-radius: $r-md;
  background: $c-surface;
  transition: border-color $dur-fast ease, box-shadow $dur-fast ease,
    background-color $dur-fast ease;
}

.ui-input-shell.is-focus {
  border-color: $c-gold-line;
  box-shadow: 0 0 0 4rpx $c-focus-ring;
  background: $c-surface;
}

.ui-input-shell.is-error {
  border-color: $c-danger;
}

.ui-input {
  width: 100%;
  height: 96rpx;
  padding: 0 $sp-4;
  background: transparent;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
}

.ui-input-placeholder {
  color: $c-ink-faint;
}

.ui-input-error {
  display: block;
  min-height: $fs-micro * $lh-body;
  margin-top: $sp-1;
  color: $c-danger;
  font-size: $fs-micro;
  line-height: $lh-body;
}
</style>
