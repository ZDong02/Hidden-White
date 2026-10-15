import { BACKEND_HOSTS } from './backend-hosts'

// 后端接口地址解析与统一请求封装。
//
// 设计目标：彻底消灭硬编码 IP。局域网 IP 会随网络变化，写死在代码里必然再次断链。
//
// 解析优先级（从高到低）：
// 1. uni.setStorageSync('hw-api-base')：运行时手动覆盖（"我的"页可改，最高优先级）
// 2. import.meta.env.VITE_API_BASE_URL：构建期注入（.env）
// 3. H5 开发态：相对路径 /api，由 vite 代理转发，不依赖 IP
// 4. 候选地址探测：按 config/backend-hosts.js 顺序做健康检查，第一个连通的即生效
// 5.兜底：localhost（H5 / 微信开发者工具本机调试）
export const BACKEND_ENABLED = true

const API_BASE_STORAGE_KEY = 'hw-api-base'
// 健康检查路径。注意：base 已统一以 /api 结尾，这里只能用相对路径，
// 否则拼出来会是 /api/api/health 而返回 404。
const HEALTH_PATH = '/health'
const PROBE_TIMEOUT = 3000
const REQUEST_TIMEOUT = 10000

function readEnvBaseUrl() {
  try {
    const env = import.meta.env || {}
    return (env.VITE_API_BASE_URL || '').trim()
  } catch {
    return ''
  }
}

function readStoredBaseUrl() {
  try {
    return (uni.getStorageSync(API_BASE_STORAGE_KEY) || '').trim()
  } catch {
    return ''
  }
}

function normalizeBaseUrl(value) {
  return String(value || '').trim().replace(/\/+$/, '')
}

// 统一 base 形态：结尾恰好是单个 /api。
// 先剥掉所有尾部斜杠，再去掉可能重复的 /api 前缀，最后补一个 /api，
// 这样用户填 "http://IP:8080/api" 或 "http://IP:8080/api/" 都能得到正确结果。
function ensureApiSuffix(value) {
  const base = normalizeBaseUrl(value)
  if (!base) return ''
  const withoutApi = base.replace(/\/api$/i, '')
  return `${withoutApi}/api`
}

// 候选地址：来自 config/backend-hosts.js + 本机兜底。
// 换网络时只需改那一个文件，无需改动任何业务代码。
function readCandidateHosts() {
  const candidates = []
  try {
    BACKEND_HOSTS.forEach((host) => {
      const value = ensureApiSuffix(host)
      if (value) candidates.push(value)
    })
  } catch {
    // 配置缺失时静默降级，不影响运行
  }
  ;['http://localhost:8080', 'http://127.0.0.1:8080'].forEach((host) => {
    const value = ensureApiSuffix(host)
    if (!candidates.includes(value)) candidates.push(value)
  })
  return candidates
}

let apiBaseUrl = ''
let resolved = false
let probing = null

// 探测候选地址，返回第一个可用的 baseUrl；全部失败返回 ''。
function probeCandidates(candidates) {
  const list = [...new Set(candidates.filter(Boolean))]
  if (!list.length) return Promise.resolve('')

  // 并行探测：串行时最坏情况要累加多个超时，首屏会被拖到数秒。
  // 这里并发发起，谁先成功就用谁，失败的结果直接丢弃。
  return new Promise((resolve) => {
    let settled = false
    let pending = list.length

    const done = (value) => {
      if (settled) return
      settled = true
      resolve(value)
    }

    list.forEach((candidate) => {
      const finish = (ok) => {
        pending -= 1
        if (ok) {
          done(candidate)
          return
        }
        if (pending === 0) done('')
      }

      uni.request({
        url: `${candidate}${HEALTH_PATH}`,
        method: 'GET',
        timeout: PROBE_TIMEOUT,
        success: (res) => finish(res.statusCode >= 200 && res.statusCode < 400),
        fail: () => finish(false)
      })
    })
  })
}

async function resolveApiBaseUrl() {
  const stored = readStoredBaseUrl()
  if (stored) return ensureApiSuffix(stored)

  const fromEnv = readEnvBaseUrl()
  if (fromEnv) return ensureApiSuffix(fromEnv)

  // #ifdef H5
  // H5 开发态由 vite 代理转发，避免跨域与 IP 依赖
  if (import.meta.env && import.meta.env.DEV) return '/api'
  // #endif

  const probed = await probeCandidates(readCandidateHosts())
  if (probed) {
    // 探测结果写入本地缓存，后续请求直接复用，免去每次启动的开销
    try {
      uni.setStorageSync(API_BASE_STORAGE_KEY, probed)
    } catch {
      // 存储失败不影响使用
    }
    return probed
  }

  return ensureApiSuffix('http://localhost:8080')
}

/** 取当前 baseUrl；首次调用时会触发一次地址解析（必要时探测）。 */
export async function getApiBaseUrl() {
  if (!resolved) {
    apiBaseUrl = await resolveApiBaseUrl()
    resolved = true
  }
  return apiBaseUrl
}

/** 同步读取当前 baseUrl，未解析完成时返回已探测值或空串（供展示用）。 */
export function peekApiBaseUrl() {
  return apiBaseUrl
}

/** 运行时切换后端地址并持久化，用于"我的"页手动配置。 */
export function setApiBaseUrl(value) {
  const normalized = ensureApiSuffix(value)
  if (!normalized) return apiBaseUrl
  apiBaseUrl = normalized
  resolved = true
  probing = null
  try {
    uni.setStorageSync(API_BASE_STORAGE_KEY, normalized)
  } catch (error) {
    console.error('保存后端地址失败:', error)
  }
  return apiBaseUrl
}

/** 清除手动配置，下次调用 getApiBaseUrl 时重新探测。 */
export function resetApiBaseUrl() {
  resolved = false
  probing = null
  apiBaseUrl = ''
  try {
    uni.removeStorageSync(API_BASE_STORAGE_KEY)
  } catch {
    // 忽略
  }
}

/** 连通性自检，供设置页展示诊断信息。 */
export async function checkApiHealth() {
  const base = await getApiBaseUrl()
  if (!base) return { ok: false, baseUrl: '', message: '未解析到后端地址' }
  try {
    await new Promise((resolve, reject) => {
      uni.request({
        url: `${base}${HEALTH_PATH}`,
        method: 'GET',
        timeout: PROBE_TIMEOUT,
        success: (res) => (res.statusCode >= 200 && res.statusCode < 400 ? resolve(res) : reject(new Error(`状态码 ${res.statusCode}`))),
        fail: (err) => reject(new Error(err.errMsg || '连接失败'))
      })
    })
    return { ok: true, baseUrl: base, message: '连接正常' }
  } catch (error) {
    return { ok: false, baseUrl: base, message: error.message || '连接失败' }
  }
}

// 请求前确保地址已解析。同一时刻的并发调用共享同一次解析过程。
function ensureBaseUrl() {
  if (resolved) return Promise.resolve(apiBaseUrl)
  if (!probing) {
    probing = getApiBaseUrl().finally(() => {
      probing = null
    })
  }
  return probing
}

export async function requestApi(path, options = {}) {
  const base = await ensureBaseUrl()
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${base}${path}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: options.timeout ?? REQUEST_TIMEOUT,
      header: {
        'Content-Type': 'application/json',
        ...(options.header || {})
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data)
          return
        }
        const message = response.data?.message || `请求失败（${response.statusCode}）`
        reject(new Error(message))
      },
      fail: (error) => {
        const raw = error.errMsg || ''
        if (raw.includes('timeout')) {
          reject(new Error('请求超时，请确认后端已启动且地址正确'))
          return
        }
        reject(new Error(`无法连接后端（${base}），请在"我的"页检查后端地址`))
      }
    })
  })
}