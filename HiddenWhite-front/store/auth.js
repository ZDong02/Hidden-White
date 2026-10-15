import { computed, ref } from 'vue'
import { BACKEND_ENABLED, requestApi } from '../config/backend'
import { getStorage, setStorageImmediate } from '../utils/storage'
import { validatePhone, validatePassword, validateDisplayName } from '../utils/validators'

const USER_KEY = 'hidden-white-current-user'

// 清理旧的登录信息
try {
  uni.removeStorageSync('hidden-white-accounts')
} catch (error) {
  console.error('清理旧登录信息失败:', error)
}

// 使用新的 storage 工具
export const currentUser = ref(getStorage(USER_KEY, null))
export const isLoggedIn = computed(() => Boolean(currentUser.value?.id))

export async function registerAccount({ displayName, phone, password }) {
  // 使用新的 validators 工具
  const nameResult = validateDisplayName(displayName)
  if (!nameResult.valid) throw new Error(nameResult.message)

  const phoneResult = validatePhone(phone)
  if (!phoneResult.valid) throw new Error(phoneResult.message)

  const passwordResult = validatePassword(password)
  if (!passwordResult.valid) throw new Error(passwordResult.message)

  if (!BACKEND_ENABLED) throw new Error('后端服务未启用，暂时无法注册')

  const member = await requestApi('/auth/register', {
    method: 'POST',
    data: {
      displayName: nameResult.value,
      phone: phoneResult.value,
      password: passwordResult.value
    }
  })

  setCurrentUser(member)
  return currentUser.value
}

export async function loginAccount({ phone, password }) {
  // 使用新的 validators 工具
  const phoneResult = validatePhone(phone)
  if (!phoneResult.valid) throw new Error(phoneResult.message)

  const passwordResult = validatePassword(password)
  if (!passwordResult.valid) throw new Error(passwordResult.message)

  if (!BACKEND_ENABLED) throw new Error('后端服务未启用，暂时无法登录')

  const member = await requestApi('/auth/login', {
    method: 'POST',
    data: {
      phone: phoneResult.value,
      password: passwordResult.value
    }
  })

  setCurrentUser(member)
  return currentUser.value
}

export function setCurrentUser(user) {
  const session = {
    id: user.id,
    displayName: user.displayName,
    phone: user.phone,
    level: user.level
  }
  currentUser.value = session
  setStorageImmediate(USER_KEY, session)
}

export function logout() {
  currentUser.value = null
  setStorageImmediate(USER_KEY, null)
}
