import { BACKEND_HOSTS } from './backend-hosts.js'
import { clearToken, getToken } from '../store/token.js'
import { DEADLINE, withDeadline } from '../utils/deadline.js'

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
const API_BASE_STORAGE_KEY = 'hw-api-base'
// 手动配置标记：区分「用户在我的页填的地址」与「探测结果自动缓存」。
// 前者必须尊重，后者在连不上时应该被丢掉重探，否则换网络后重启会一直连旧 IP。
const API_BASE_MANUAL_KEY = 'hw-api-base-manual'
// 健康检查路径。注意：base 已统一以 /api 结尾，这里只能用相对路径，
// 否则拼出来会是 /api/api/health 而返回 404。
const HEALTH_PATH = '/health'
const PROBE_TIMEOUT = 3000
const REQUEST_TIMEOUT = 10000

/**
 * 生产构建是否锁定访问入口。
 *
 * <p>锁定后只认两种地址：构建期注入的 `VITE_API_BASE_URL`，或同源 `/api`
 * （由 nginx 反代到后端）。运行时改写地址、内网候选地址探测一律停用。
 *
 * <p>为什么必须锁：客户端能自由指定后端地址，等于把「接口可以被导到任意服务器」
 * 这个能力交给使用者 —— 本地存储里塞一个地址就能让下单请求打到自建服务；
 * 同时 `config/backend-hosts.js` 里的内网 IP 会随安装包发出去，
 * 这些地址本不该出现在客户端可见的任何产物里。
 *
 * <p>判据用 `PROD` 而不是 `!DEV`：Node 测试环境两个字段都不存在，
 * 那时按「未锁定」处理，才能继续跑地址解析的防护测试。
 */
const IS_PROD_BUILD = Boolean(import.meta.env && import.meta.env.PROD)
/** 极端场景（预发机上仍需改地址）才打开：构建时注入 VITE_ALLOW_ENDPOINT_OVERRIDE=true */
const ALLOW_OVERRIDE_IN_PROD = Boolean(import.meta.env && import.meta.env.VITE_ALLOW_ENDPOINT_OVERRIDE === 'true')

/** 生产构建的入口锁定状态：为 true 时只认同源 /api 或构建期注入的域名。 */
export const ENDPOINT_LOCKED = IS_PROD_BUILD && !ALLOW_OVERRIDE_IN_PROD

/** 调试卡片是否被显式要求显示：构建/启动时注入 VITE_SHOW_BACKEND_DEBUG=true 才为 true。 */
const DEBUG_CARD_REQUESTED = Boolean(
  import.meta.env && import.meta.env.VITE_SHOW_BACKEND_DEBUG === 'true'
)

/**
 * 「我的」页「后端服务」调试卡是否渲染。
 *
 * <p><b>默认关闭</b>，开发构建也一样：这张卡会把后端地址摊在界面上，
 * 演示、截图、把手机递给别人看菜单时都会露出来，而它真正要解决的
 * 「换网络后端口变了」一周也用不上一次。
 *
 * <p>需要改地址时（本地联调 / 手机连不上本机后端），启动前显式打开：
 * <pre>
 *   VITE_SHOW_BACKEND_DEBUG=true npm run dev:h5
 *   VITE_SHOW_BACKEND_DEBUG=true npm run dev:mp-weixin
 * </pre>
 * 生产构建即使注入该变量也不会显示（入口已锁定，改地址本来就不生效）。
 */
export const BACKEND_DEBUG_VISIBLE = DEBUG_CARD_REQUESTED && !ENDPOINT_LOCKED

// 探测的硬截止。
// uni.request 的 timeout 只是「空闲超时」：TCP 握手阶段卡住时它不一定触发回调，
// 而探测只有在「全部候选都回来」时才 resolve。于是只要有一个候选地址挂在连接上
// （换网络后旧 IP 变成静默丢包最常见），整个地址解析就永不返回 ——
// 表现为首屏骨架屏一直转、连错误提示都没有。所以这里再加一道绝对上限。
const PROBE_DEADLINE = PROBE_TIMEOUT + 800

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

/** 缓存里的地址是不是用户在「我的」页手动填的（而非探测自动写入）。 */
function readManualFlag() {
  try {
    return uni.getStorageSync(API_BASE_MANUAL_KEY) === '1'
  } catch {
    return false
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
  // 生产构建直接返回空数组：这段判断在打包时会被折叠成 `return []`，
  // 连带 backend-hosts.js 与下面的本机兜底一起被摇掉 ——
  // 内网 IP 属于拓扑信息，不该随客户端安装包发出去，生产环境也没有「换网络找后端」这回事。
  if (import.meta.env && import.meta.env.PROD) return []

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
// 当前地址是否来自「探测缓存」。是的话，连不上就允许作废重探 —— 这是换网络后能自愈的关键
let baseFromProbe = false

/**
 * 连接级失败后作废探测缓存。
 *
 * 只处理探测得到的地址：用户手动填的地址（如固定的内网 IP）不该被我们悄悄清掉，
 * 否则他每次都要重填一遍。作废后 resolved 置 false，下一次请求会重新探测。
 */
function markBaseUnreachable() {
  if (!baseFromProbe) return
  baseFromProbe = false
  resolved = false
  apiBaseUrl = ''
  try {
    uni.removeStorageSync(API_BASE_STORAGE_KEY)
  } catch {
    // 清缓存失败不影响下次重探（resolved 已置 false）
  }
}

/**
 * 打一次健康检查，返回连通性与可读原因。
 *
 * <p>探测（probeCandidates）与自检（checkApiHealth）共用这一处：
 * 两处各写一遍 `/health` 请求时，超时、状态码判定、失败文案很容易只改一边 ——
 * 于是会出现「探测认为连得上、自检说不通」这类互相矛盾的诊断。
 *
 * @returns {Promise<{ ok: boolean, message: string }>}
 */
function probeHealth(base, timeout = PROBE_TIMEOUT) {
  return new Promise((resolve) => {
    uni.request({
      url: `${base}${HEALTH_PATH}`,
      method: 'GET',
      timeout,
      success: (res) => {
        const ok = res.statusCode >= 200 && res.statusCode < 400
        resolve({ ok, message: ok ? '连接正常' : `状态码 ${res.statusCode}` })
      },
      fail: (error) => resolve({ ok: false, message: error?.errMsg || '连接失败' })
    })
  })
}

// 探测候选地址，返回第一个可用的 baseUrl；全部失败返回 ''。
function probeCandidates(candidates) {
  const list = [...new Set(candidates.filter(Boolean))]
  if (!list.length) return Promise.resolve('')

  // 并行探测：串行时最坏情况要累加多个超时，首屏会被拖到数秒。
  // 这里并发发起，谁先成功就用谁，失败的结果直接丢弃。
  const all = new Promise((resolve) => {
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

      probeHealth(candidate).then((result) => finish(result.ok))
    })
  })

  // 关键：探测原本要等「全部候选都回来」才收场。只要有一个候选静默丢包，
  // 整个地址解析就永不返回，首屏便会一直停在骨架屏。这里按绝对上限收场，
  // 到点没用上就交给兜底地址。
  return withDeadline(all, PROBE_DEADLINE, '')
}

/** 清掉调试期残留在本机的自定义地址：锁定后它已失效，留着只会误导排查。 */
function clearStoredOverride() {
  try {
    uni.removeStorageSync(API_BASE_STORAGE_KEY)
    uni.removeStorageSync(API_BASE_MANUAL_KEY)
  } catch {
    // 清不掉也无所谓：锁定态本来就不读这两个键
  }
}

async function resolveApiBaseUrl() {
  // ---------------------------------------------------------------
  // 生产构建：入口锁定
  //   1. 有 VITE_API_BASE_URL → 用它（小程序等原生端必须走这条）；
  //   2. 否则用同源 /api（H5 由 nginx 反代到后端，客户端根本不知道后端在哪）。
  // 内网候选地址探测、本机自定义地址在锁定态一律不参与。
  // ---------------------------------------------------------------
  if (ENDPOINT_LOCKED) {
    clearStoredOverride()
    const fromEnv = readEnvBaseUrl()
    if (fromEnv) return ensureApiSuffix(fromEnv)

    // 缺 VITE_API_BASE_URL 在 H5 是正常情况（同域走相对路径），
    // 但小程序 / App 不支持相对路径，属于发版前必须修掉的配置问题 ——
    // 只在原生端告警，避免 H5 产物里带一条永远打不到的日志。
    // #ifndef H5
    console.error('[backend] 生产构建缺少 VITE_API_BASE_URL，小程序/App 端将无法访问接口')
    // #endif

    // 同源 /api：由 nginx 的 /api/ 反代到后端，客户端不知道后端在哪
    return '/api'
  }

  const stored = readStoredBaseUrl()
  if (stored) {
    // 手动填的地址无条件尊重；探测缓存则记下来源，连不上时允许被丢弃重探
    baseFromProbe = !readManualFlag()
    return ensureApiSuffix(stored)
  }

  const fromEnv = readEnvBaseUrl()
  if (fromEnv) return ensureApiSuffix(fromEnv)

  // #ifdef H5
  // H5 开发态由 vite 代理转发，避免跨域与 IP 依赖
  if (import.meta.env && import.meta.env.DEV) return '/api'
  // #endif

  const probed = await probeCandidates(readCandidateHosts())
  if (probed) {
    // 探测结果写入本地缓存，后续请求直接复用，免去每次启动的开销
    baseFromProbe = true
    try {
      uni.setStorageSync(API_BASE_STORAGE_KEY, probed)
    } catch {
      // 存储失败不影响使用
    }
    return probed
  }

  baseFromProbe = false
  return ensureApiSuffix('http://localhost:8080')
}

/** 取当前 baseUrl；首次调用时会触发一次地址解析（必要时探测）。供自检与测试使用。 */
export async function getApiBaseUrl() {
  if (!resolved) {
    apiBaseUrl = await resolveApiBaseUrl()
    resolved = true
  }
  return apiBaseUrl
}

/** 运行时切换后端地址并持久化，用于"我的"页手动配置（仅开发构建可用）。 */
export function setApiBaseUrl(value) {
  if (ENDPOINT_LOCKED) {
    // 生产构建里这个方法不该被调用（调试卡片已不渲染）；
    // 万一被调用也直接忽略，避免「界面改了但实际没生效」或反过来被改写
    console.warn('[backend] 生产构建已锁定接口入口，忽略运行时修改')
    return apiBaseUrl || '/api'
  }
  const normalized = ensureApiSuffix(value)
  if (!normalized) return apiBaseUrl
  apiBaseUrl = normalized
  resolved = true
  baseFromProbe = false
  probing = null
  try {
    uni.setStorageSync(API_BASE_STORAGE_KEY, normalized)
    // 打上手动标记：往后连不上也不自动清掉，这是用户自己的选择
    uni.setStorageSync(API_BASE_MANUAL_KEY, '1')
  } catch (error) {
    console.error('保存后端地址失败:', error)
  }
  return apiBaseUrl
}

/** 清除手动配置，下次解析 baseUrl 时重新探测。 */
export function resetApiBaseUrl() {
  resolved = false
  probing = null
  apiBaseUrl = ''
  baseFromProbe = false
  try {
    uni.removeStorageSync(API_BASE_STORAGE_KEY)
    uni.removeStorageSync(API_BASE_MANUAL_KEY)
  } catch {
    // 忽略
  }
}

/**
 * 站点根地址（不含 /api 后缀），用于拼接后端静态资源。
 *
 * 上传的图片由后端以 /uploads/** 暴露，不在 /api 之下，
 * 因此不能直接用 apiBaseUrl 拼；需去掉尾部 /api 得到 origin。
 *
 * 例：apiBaseUrl = 'http://192.168.1.5:8080/api' → origin = 'http://192.168.1.5:8080'
 *
 * 同步返回：图片地址要在模板渲染时直接可用，不能等待异步探测。
 * 地址尚未探测出来时回退为空串，调用方据此保留原始相对路径。
 */
export function getApiOrigin() {
  if (!apiBaseUrl) return ''
  return apiBaseUrl.replace(/\/api\/?$/i, '')
}

/** 连通性自检，供设置页展示诊断信息。 */
export async function checkApiHealth() {
  const base = await getApiBaseUrl()
  if (!base) return { ok: false, baseUrl: '', message: '未解析到后端地址' }
  const { ok, message } = await probeHealth(base)
  return { ok, baseUrl: base, message }
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

/**
 * 登录态失效的统一处理者。
 *
 * <p>由 `store/auth` 注册，而不是在这里直接 import 它 —— 请求封装是底层，
 * 反向依赖业务 store 会形成循环（auth 需要 requestApi）。
 */
let unauthorizedHandler = null

/** 上次处理的时间：并发请求同时 401 时只处理一次，否则会连弹好几条「登录已过期」 */
let unauthorizedHandledAt = 0
const UNAUTHORIZED_COOLDOWN = 3000

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

/**
 * 401 处理。
 *
 * <p><b>为什么必须清整个会话而不只是 token</b>：`isLoggedIn` 由 `currentUser` 决定。
 * 只清 token 的话，界面仍然认为「已登录」，于是每个面板照常发请求、各自失败一次 ——
 * 一次 token 失效会变成一串 401 加一串报错（真实踩过：后端重启后满屏 401）。
 */
function notifyUnauthorized() {
  clearToken()
  const now = Date.now()
  if (now - unauthorizedHandledAt < UNAUTHORIZED_COOLDOWN) return
  unauthorizedHandledAt = now
  try {
    unauthorizedHandler?.()
  } catch (error) {
    console.error('处理登录失效失败', error)
  }
}

/**
 * 统一请求封装。
 *
 * 自动携带会员 token（Authorization: Bearer <token>）：
 * 后端 /api/orders/** 与 /api/members/{id} 依据 token 判定会员归属，
 * 不再信任客户端传入的 memberId。公开接口（菜单 / 门店 / 权益）带上 token 也无副作用。
 *
 * 同时统一处理 401：登录态失效时清除本地会话并提示一次，避免后续请求持续失败。
 * 主动登出（{@code /auth/logout}）用 {@code ignoreUnauthorized} 关掉这套处理 ——
 * 那时 401 是预期结果，不该再弹一句「登录已过期」。
 */
export async function requestApi(path, options = {}) {
  const base = await ensureBaseUrl()
  const timeout = options.timeout ?? REQUEST_TIMEOUT
  const token = options.auth === false ? '' : getToken()

  const request = new Promise((resolve, reject) => {
    uni.request({
      url: `${base}${path}`,
      method: options.method || 'GET',
      data: options.data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data)
          return
        }
        // 登录态失效：清会话并提示一次，让上层重新走登录流程
        if (response.statusCode === 401 && options.ignoreUnauthorized !== true) {
          notifyUnauthorized()
        }
        const message = response.data?.message || `请求失败（${response.statusCode}）`
        reject(new Error(message))
      },
      fail: (error) => {
        const reason = error.errMsg || ''
        if (reason.includes('timeout')) {
          reject(new Error('请求超时，请确认后端已启动且地址正确'))
          return
        }
        // 连不上：说明这个地址可能已经过期（换网络 / 电脑换了 IP）
        markBaseUnreachable()
        reject(new Error(`无法连接后端（${base}），请在"我的"页检查后端地址`))
      }
    })
  })

  // 兜底截止：连不上但也不回错的地址会让 Promise 一直挂着，界面就永远停在骨架屏。
  // timeout 之上再多给 500ms，让 uni.request 自己的超时先有机会说话，报错信息更准确。
  const result = await withDeadline(request, timeout + 500)
  if (result === DEADLINE) {
    // 走到这里说明这个地址连回应都没有，和 fail 一样当作「地址可能过期」处理，
    // 否则探测缓存里的旧 IP 会一直挂着，只能重启才恢复
    markBaseUnreachable()
    throw new Error('请求超时，请确认后端已启动且地址正确')
  }
  return result
}