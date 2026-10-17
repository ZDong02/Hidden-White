import { getStorage, removeStorage, setStorageImmediate } from '../utils/storage'

/**
 * 会员访问令牌。
 *
 * 后端 /api/orders/** 与 /api/members/{id} 需要凭 token 判定会员归属，
 * 不再接受客户端明文传入 memberId。token 由 /api/auth/login 或 /api/auth/register 签发。
 */
const TOKEN_KEY = 'hidden-white-token'

export function getToken() {
  return getStorage(TOKEN_KEY, '') || ''
}

export function setToken(token) {
  if (token) {
    setStorageImmediate(TOKEN_KEY, token)
  } else {
    clearToken()
  }
}

export function clearToken() {
  removeStorage(TOKEN_KEY)
}
