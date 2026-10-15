<template>
  <view class="ui-input-wrapper">
    <text v-if="label" class="ui-input-label">{{ label }}</text>
    <input
      v-model="localValue"
      class="ui-input"
      :type="masked ? 'text' : type"
      :password="masked"
      :maxlength="maxlength"
      :placeholder="placeholder"
      @input="handleInput"
      @blur="handleBlur"
    />
    <text v-if="error" class="ui-input-error">{{ error }}</text>
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

watch(() => props.modelValue, (newValue) => {
  localValue.value = newValue
})

const handleInput = () => {
  emit('update:modelValue', localValue.value)
}

const handleBlur = () => {
  emit('blur', localValue.value)
}
</script>

<style lang="scss" scoped>
.ui-input-wrapper {
  margin-bottom: $sp-5;
}

.ui-input-label {
  display: block;
  margin-bottom: $sp-2;
  color: $c-ink-muted;
  font-size: 24rpx;
}

.ui-input {
  width: 100%;
  height: 96rpx;
  padding: 0 $sp-4;
  border: 1rpx solid $c-border-strong;
  border-radius: $r-md;
  background: $c-surface;
  color: $c-ink-strong;
  font-size: 26rpx;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}

.ui-input-error {
  display: block;
  margin-top: $sp-2;
  color: $c-danger;
  font-size: 22rpx;
}

/* #ifdef H5 */
.ui-input:focus {
  border-color: $c-gold-line !important;
  box-shadow: 0 0 0 4rpx rgba(185, 151, 55, 0.18);
}
/* #endif */
</style>
