import { computed, ref } from 'vue'
import { requestApi, setUnauthorizedHandler } from '../config/backend.js'
import { getStorage, setStorageImmediate } from '../utils/storage.js'
import { validatePhone, validatePassword, validateDisplayName } from '../utils/validators.js'
import { clearToken, setToken } from './token.js'

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
    // 主动登出时 401 是预期结果（token 可能已失效），不该再弹「登录已过期」
    await requestApi('/auth/logout', { method: 'POST', ignoreUnauthorized: true })
  } catch {
    // token 可能已在服务端失效，本地照常清理即可
  }
  currentUser.value = null
  clearToken()
  setStorageImmediate(USER_KEY, null)
}

/**
 * 401 统一处理：清会话 + 提示一次。
 *
 * <p>必须连 `currentUser` 一起清 —— 界面靠它判断「是否登录」，只清 token
 * 会让每个面板继续发请求、各自失败一次，一次失效变成满屏 401。
 *
 * <p>不自动跳登录页：用户可能正在浏览菜单，被硬拽走比看一条提示更烦；
 * 各页面都有自己的未登录态（会员页「登录后查看」、下单前引导），能接住下一步。
 */
setUnauthorizedHandler(() => {
  currentUser.value = null
  setStorageImmediate(USER_KEY, null)
  uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
})
