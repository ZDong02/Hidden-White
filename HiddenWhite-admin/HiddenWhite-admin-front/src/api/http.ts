import { authSession } from './session.ts'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface HttpClientOptions {
  baseUrl?: string
  tokenProvider?: () => string | null
  fetcher?: typeof fetch
  /** 单次请求超时（毫秒）。超时后会抛出 ApiError，避免界面无限等待。 */
  timeoutMs?: number
}

export interface HttpClient {
  get<T>(path: string, query?: QueryParams): Promise<T>
  post<T>(path: string, body?: unknown): Promise<T>
  put<T>(path: string, body?: unknown): Promise<T>
  delete<T>(path: string): Promise<T>
  download(path: string, query?: QueryParams): Promise<Blob>
  /**
   * multipart/form-data 上传。
   *
   * 刻意不复用 post：FormData 的 Content-Type 必须由浏览器生成（带 boundary），
   * 手动设置会导致服务端解析不出文件边界。
   */
  upload<T>(path: string, form: FormData): Promise<T>
}

export type QueryParams = Record<string, string | number | boolean | null | undefined>

/**
 * 默认超时。
 *
 * <p>取 15 秒：普通查询远用不到，而导出、批量操作这类慢接口也不至于被误杀。
 * 没有超时的时候，后端假死（线程池打满、连接不释放）会让页面永远转圈，
 * 用户既看不到失败也无法重试。
 */
const DEFAULT_TIMEOUT_MS = 15_000

export class ApiError extends Error {
  code: number
  status: number
  traceId?: string

  constructor(message: string, code: number, status: number, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

let unauthorizedHandler: (() => void) | null = null

/** 登录失效（401）时的统一处理，由路由层注册 */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

export function createHttpClient(options: HttpClientOptions = {}): HttpClient {
  const baseUrl = normalizeBaseUrl(options.baseUrl ?? defaultBaseUrl())
  const tokenProvider = options.tokenProvider ?? authSession.token
  const fetcher = options.fetcher ?? fetch
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS

  function authHeaders(accept: string, token: string | null): Record<string, string> {
    const headers: Record<string, string> = { Accept: accept }
    if (token) headers.Authorization = `Bearer ${token}`
    return headers
  }

  /**
   * 发请求并统一处理「超时 / 网络不通」。
   *
   * <p>两类失败原先都以原生 TypeError 冒出去，页面里的 `catch (e) => e.message`
   * 拿到的是「Failed to fetch」这种英文且无上下文的文案；
   * 现在统一转成 ApiError，文案可直接展示给运营者。
   */
  async function send(url: string, init: RequestInit): Promise<Response> {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
      return await fetcher(url, { ...init, signal: controller.signal })
    } catch (error) {
      if (controller.signal.aborted) {
        throw new ApiError('请求超时，请稍后重试', 0, 0)
      }
      throw new ApiError(describeNetworkError(error), 0, 0)
    } finally {
      clearTimeout(timer)
    }
  }

  /** 401 时通知路由层清会话；只对「发起请求时的那个 token」生效，避免旧请求踢掉新会话。 */
  function notifyUnauthorized(response: Response, code: number | undefined, token: string | null) {
    if ((code === 401 || response.status === 401) && token && token === tokenProvider()) {
      unauthorizedHandler?.()
    }
  }

  async function request<T>(method: string, path: string, body?: unknown, query?: QueryParams): Promise<T> {
    const token = tokenProvider()
    const headers = authHeaders('application/json', token)
    const init: RequestInit = { method, headers }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }
    const response = await send(buildUrl(baseUrl, path, query), init)
    const payload = await readPayload<T>(response)
    assertOk(response, payload, '请求失败', () => notifyUnauthorized(response, payload.code, token))
    return payload.data as T
  }

  async function download(path: string, query?: QueryParams): Promise<Blob> {
    const token = tokenProvider()
    const response = await send(buildUrl(baseUrl, path, query), {
      method: 'GET',
      headers: authHeaders('text/csv,application/octet-stream,*/*', token),
    })
    if (!response.ok) {
      const payload = await readPayload<unknown>(response)
      notifyUnauthorized(response, payload.code, token)
      throw new ApiError(payload.message || '下载失败', payload.code || response.status, response.status, payload.traceId)
    }
    return response.blob()
  }

  /**
   * multipart/form-data 上传。
   *
   * 不设置 Content-Type：交给浏览器按 FormData 生成带 boundary 的请求头，
   * 手动设置会覆盖掉 boundary，导致服务端读不到文件内容。
   */
  async function upload<T>(path: string, form: FormData): Promise<T> {
    const token = tokenProvider()
    const response = await send(buildUrl(baseUrl, path), {
      method: 'POST',
      headers: authHeaders('application/json', token),
      body: form,
    })
    const payload = await readPayload<T>(response)
    assertOk(response, payload, '上传失败', () => notifyUnauthorized(response, payload.code, token))
    return payload.data as T
  }

  return {
    get: (path, query) => request('GET', path, undefined, query),
    post: (path, body) => request('POST', path, body),
    put: (path, body) => request('PUT', path, body),
    delete: (path) => request('DELETE', path),
    download,
    upload,
  }
}

function normalizeBaseUrl(value: string) {
  return value.endsWith('/') ? value.slice(0, -1) : value
}

// 管理端后端接口统一挂在 /api/admin/**（2026-10-13 与小程序端后端合并为单应用）
function defaultBaseUrl() {
  return ((import.meta as ImportMeta & { env?: { VITE_API_BASE_URL?: string } }).env?.VITE_API_BASE_URL) || '/api/admin'
}

function buildUrl(baseUrl: string, path: string, query?: QueryParams) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const queryString = query ? toQueryString(query) : ''
  return `${baseUrl}${normalizedPath}${queryString ? `?${queryString}` : ''}`
}

function toQueryString(query: QueryParams) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  })
  return params.toString()
}

/**
 * 读取响应体。
 *
 * <p>非 JSON 响应必须自己兜住：反向代理/容器在 502、504 时会直接返回一段 HTML 错误页，
 * 裸 `JSON.parse` 会抛 SyntaxError —— 它不是 ApiError，页面里 `instanceof ApiError`
 * 的分支全部落空，最终展示的是「Unexpected token '<'」这种东西。
 * 空响应体（204）视为成功、`data` 为 null。
 */
async function readPayload<T>(response: Response): Promise<Partial<ApiResponse<T>>> {
  const text = await response.text()
  if (!text) {
    return { data: null as T }
  }
  try {
    return JSON.parse(text) as ApiResponse<T>
  } catch {
    throw new ApiError(
      response.ok ? '服务返回了无法解析的响应' : `服务暂时不可用（HTTP ${response.status}）`,
      0,
      response.status,
    )
  }
}

function assertOk(
  response: Response,
  payload: Partial<ApiResponse<unknown>>,
  fallbackMessage: string,
  onUnauthorized: () => void,
) {
  // code 缺失说明响应体是空的（204 之类），此时只以 HTTP 状态码为准
  const failed = !response.ok || (payload.code !== undefined && payload.code !== 200)
  if (!failed) return
  onUnauthorized()
  throw new ApiError(payload.message || fallbackMessage, payload.code ?? response.status, response.status, payload.traceId)
}

function describeNetworkError(error: unknown) {
  const message = error instanceof Error ? error.message : String(error)
  // fetch 在跨域被拦、后端没起、断网时都只给一句 Failed to fetch，补上可操作的方向
  if (/failed to fetch|networkerror|load failed/i.test(message)) {
    return '无法连接后端服务，请确认后端已启动或网络可达'
  }
  return message || '网络异常，请稍后重试'
}

export const httpClient = createHttpClient()
