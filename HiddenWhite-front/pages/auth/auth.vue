<template>
  <view class="screen auth-screen">
    <PhoneHeader />

    <view class="auth-body">
      <view class="auth-brand reveal">
        <text class="brand">隐白</text>
        <text class="brand-rule" />
        <text class="caption">登录后即可点单并查看专属订单</text>
      </view>

      <!-- 登录 / 注册：下划线指示条跟着切换滑动，而不是让文字自己换颜色 -->
      <view class="auth-tabs reveal reveal-1">
        <view class="auth-tabs-track">
          <text
            :class="['auth-tab', { active: mode === 'login' }]"
            hover-class="auth-tab--press"
            :hover-stay-time="60"
            @tap="mode = 'login'"
          >登录</text>
          <text
            :class="['auth-tab', { active: mode === 'register' }]"
            hover-class="auth-tab--press"
            :hover-stay-time="60"
            @tap="mode = 'register'"
          >注册</text>
          <view class="auth-tab-indicator" :class="{ 'is-register': mode === 'register' }" />
        </view>
      </view>

      <view class="auth-form reveal reveal-2">
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

        <view class="auth-submit">
          <UiButton
            variant="primary"
            :disabled="submitting"
            style="width: 100%;"
            @tap="submit"
          >
            {{ submitting ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}
          </UiButton>
        </view>
      </view>

      <text class="auth-hint reveal reveal-3">
        账号信息由服务端安全保存，登录后可跨设备查看会员资料和订单
      </text>
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
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background: $c-bg;
}

/* 表单区吃掉剩余高度，底部指示条才会贴到屏幕底部，而不是飘在内容末尾 */
.auth-body {
  flex: 1 1 auto;
  padding: $sp-8 $gutter $sp-6;
}

.auth-brand {
  text-align: center;
}

.brand {
  display: block;
  font-family: $font-display;
  font-size: $fs-brand;
  line-height: $lh-display;
  letter-spacing: 8rpx;
  color: $c-ink-strong;
}

/* 品牌字下方的短金线：与首页品牌区同一条笔触 */
.brand-rule {
  display: block;
  width: 56rpx;
  height: 2rpx;
  margin: $sp-4 auto $sp-3;
  background: $c-gold-line;
}

.caption {
  display: block;
  color: $c-ink-muted;
  font-size: $fs-caption;
}

/* ---- 切换标签 ---- */
.auth-tabs {
  margin: $sp-8 0 $sp-6;
}

.auth-tabs-track {
  position: relative;
  display: flex;
  border-bottom: 1rpx solid $c-border-strong;
}

.auth-tab {
  display: flex;
  width: 50%;
  min-height: $tap-min;
  align-items: center;
  justify-content: center;
  color: $c-ink-muted;
  font-size: $fs-body;
  transition: color $dur-fast ease;
}

.auth-tab.active {
  color: $c-ink-strong;
  font-weight: 600;
}

.auth-tab--press {
  opacity: 0.6;
}

/* 指示条：宽度固定半栏，靠 transform 平移，切换时有明确的「移动」而不是「消失再出现」 */
.auth-tab-indicator {
  position: absolute;
  left: 0;
  bottom: -1rpx;
  width: 50%;
  height: 4rpx;
  border-radius: $r-pill;
  background: $c-gold-line;
  transform: translateX(0);
  transition: transform $dur-base $ease-out;
}

.auth-tab-indicator.is-register {
  transform: translateX(100%);
}

/* ---- 表单 ---- */
.auth-form {
  display: block;
}

.auth-submit {
  display: block;
  margin-top: $sp-5;
}

.auth-hint {
  display: block;
  margin-top: $sp-5;
  color: $c-ink-muted;
  font-size: $fs-micro;
  line-height: 1.6;
  text-align: center;
}

/* #ifdef H5 */
.auth-tab:hover {
  color: $c-ink-strong;
}
/* #endif */
</style>
