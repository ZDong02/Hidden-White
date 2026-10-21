import { computed, ref } from 'vue'
import { BACKEND_ENABLED, requestApi } from '../config/backend'
import { getStorage, setStorageImmediate } from '../utils/storage'
import { validatePhone, validatePassword, validateDisplayName } from '../utils/validators'
import { clearToken, setToken } from './token'

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

// 兼容旧版本残留：没有 token 却有用户信息时视为未登录，避免带着无效身份请求
if (currentUser.value && !getStorage('hidden-white-token', '')) {
  currentUser.value = null
  setStorageImmediate(USER_KEY, null)
}

export async function registerAccount({ displayName, phone, password }) {
  // 使用新的 validators 工具
  const nameResult = validateDisplayName(displayName)
  if (!nameResult.valid) throw new Error(nameResult.message)

  const phoneResult = validatePhone(phone)
  if (!phoneResult.valid) throw new Error(phoneResult.message)

  const passwordResult = validatePassword(password)
  if (!passwordResult.valid) throw new Error(passwordResult.message)

  if (!BACKEND_ENABLED) throw new Error('后端服务未启用，暂时无法注册')

  const auth = await requestApi('/auth/register', {
    method: 'POST',
    auth: false,
    data: {
      displayName: nameResult.value,
      phone: phoneResult.value,
      password: passwordResult.value
    }
  })

  return applyAuth(auth)
}

export async function loginAccount({ phone, password }) {
  // 使用新的 validators 工具
  const phoneResult = validatePhone(phone)
  if (!phoneResult.valid) throw new Error(phoneResult.message)

  const passwordResult = validatePassword(password)
  if (!passwordResult.valid) throw new Error(passwordResult.message)

  if (!BACKEND_ENABLED) throw new Error('后端服务未启用，暂时无法登录')

  const auth = await requestApi('/auth/login', {
    method: 'POST',
    auth: false,
    data: {
      phone: phoneResult.value,
      password: passwordResult.value
    }
  })

  return applyAuth(auth)
}

/** 后端返回 { member, token }：先存 token 再存用户信息，避免中途失败留下半登录态。 */
function applyAuth(auth) {
  if (!auth?.token || !auth?.member?.id) {
    throw new Error('登录响应缺少令牌，请检查后端版本')
  }
  setToken(auth.token)
  setCurrentUser(auth.member)
  return currentUser.value
}

function setCurrentUser(user) {
  const session = {
    id: user.id,
    displayName: user.displayName,
    phone: user.phone,
    level: user.level
  }
  currentUser.value = session
  setStorageImmediate(USER_KEY, session)
}

/** 退出登录：通知后端使 token 失效（失败不阻塞本地清理），并清除本地会话。 */
export async function logout() {
  try {
    await requestApi('/auth/logout', { method: 'POST' })
  } catch {
    // token 可能已在服务端失效，本地照常清理即可
  }
  currentUser.value = null
  clearToken()
  setStorageImmediate(USER_KEY, null)
}
