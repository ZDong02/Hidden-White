<template>
  <view class="screen auth-screen">
    <PhoneHeader />

    <view class="auth-body">
      <view class="auth-brand">
        <text class="brand">隐白</text>
        <text class="caption">登录后即可点单并查看专属订单</text>
      </view>

      <view class="auth-tabs">
        <text :class="['auth-tab', { active: mode === 'login' }]" @tap="mode = 'login'">登录</text>
        <text :class="['auth-tab', { active: mode === 'register' }]" @tap="mode = 'register'">注册</text>
      </view>

      <!-- 使用新的 Input 组件 -->
      <UiInput
        v-model="phone"
        label="手机号"
        type="text"
        :maxlength="20"
        placeholder="请输入手机号"
        :error="phoneError"
        @blur="validatePhoneField"
      />

      <UiInput
        v-if="mode === 'register'"
        v-model="displayName"
        label="昵称"
        type="text"
        :maxlength="30"
        placeholder="请输入昵称"
        :error="nameError"
        @blur="validateNameField"
      />

      <UiInput
        v-model="password"
        label="密码"
        type="password"
        :maxlength="30"
        placeholder="至少 6 位密码"
        :error="passwordError"
        @blur="validatePasswordField"
      />

      <!-- 使用新的 Button 组件 -->
      <UiButton
        variant="primary"
        :disabled="submitting"
        @tap="submit"
        style="width: 100%; margin-top: 48rpx;"
      >
        {{ submitting ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}
      </UiButton>

      <text class="auth-hint">账号信息由服务端安全保存，登录后可跨设备查看会员资料和订单</text>
    </view>

    <HomeIndicator />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import UiInput from '../../components/ui/Input.vue'
import UiButton from '../../components/ui/Button.vue'
import { loginAccount, registerAccount } from '../../store/auth'
import { replacePage, resetToPage } from '../../utils/navigation'
import { validatePhone, validatePassword, validateDisplayName } from '../../utils/validators'
import { showSuccess, showError } from '../../utils/toast'

const mode = ref('login')
const phone = ref('')
const displayName = ref('')
const password = ref('')
const submitting = ref(false)
const redirect = ref('profile')

// 错误消息
const phoneError = ref('')
const nameError = ref('')
const passwordError = ref('')

let redirectTimer = null

onLoad((query) => {
  if (query?.redirect) redirect.value = query.redirect
})

onUnload(() => {
  if (redirectTimer) {
    clearTimeout(redirectTimer)
    redirectTimer = null
  }
})

// 实时验证
function validatePhoneField() {
  const result = validatePhone(phone.value)
  phoneError.value = result.valid ? '' : result.message
  return result.valid
}

function validateNameField() {
  const result = validateDisplayName(displayName.value)
  nameError.value = result.valid ? '' : result.message
  return result.valid
}

function validatePasswordField() {
  const result = validatePassword(password.value)
  passwordError.value = result.valid ? '' : result.message
  return result.valid
}

// 提交前验证所有字段
function validateAll() {
  const phoneValid = validatePhoneField()
  const passwordValid = validatePasswordField()

  if (mode.value === 'register') {
    const nameValid = validateNameField()
    return phoneValid && nameValid && passwordValid
  }

  return phoneValid && passwordValid
}

async function submit() {
  if (submitting.value) return

  // 验证表单
  if (!validateAll()) {
    showError('请检查输入信息')
    return
  }

  try {
    submitting.value = true

    if (mode.value === 'login') {
      await loginAccount({ phone: phone.value, password: password.value })
    } else {
      await registerAccount({
        displayName: displayName.value,
        phone: phone.value,
        password: password.value
      })
    }

    showSuccess(mode.value === 'login' ? '登录成功' : '注册成功')

    // 等待 toast 呈现后再跳转
    redirectTimer = setTimeout(() => {
      redirectTimer = null
      if (redirect.value === 'checkout') {
        replacePage('checkout')
      } else {
        resetToPage('profile')
      }
    }, 250)
  } catch (error) {
    showError(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.auth-screen {
  min-height: 100vh;
  background: $c-bg;
}

.auth-body {
  padding: $sp-8 $gutter $sp-6;
}

.auth-brand {
  text-align: center;
}

.brand {
  display: block;
  font-family: $font-display;
  font-size: 62rpx;
  line-height: $lh-display;
  letter-spacing: 8rpx;
  color: $c-ink-strong;
}

.caption {
  display: block;
  margin-top: $sp-3;
  color: $c-ink-muted;
  font-size: 24rpx;
}

.auth-tabs {
  display: flex;
  margin: $sp-8 0 $sp-7;
  border-bottom: 1rpx solid $c-border-strong;
}

.auth-tab {
  display: flex;
  width: 50%;
  min-height: $tap-min;
  align-items: center;
  justify-content: center;
  color: $c-ink-muted;
  font-size: 28rpx;
  border-bottom: 4rpx solid transparent;
  margin-bottom: -1rpx;
  transition: color $dur-fast ease, border-color $dur-fast ease;
}

.auth-tab.active {
  border-bottom-color: $c-gold-line;
  color: $c-ink-strong;
  font-weight: 600;
}

.auth-hint {
  display: block;
  margin-top: $sp-5;
  color: $c-ink-muted;
  font-size: 22rpx;
  line-height: 1.6;
  text-align: center;
}

/* #ifdef H5 */
.auth-tab:hover {
  color: $c-ink-strong;
}
/* #endif */
</style>
