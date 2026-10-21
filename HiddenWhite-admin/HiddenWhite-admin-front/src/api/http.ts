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

  async function request<T>(method: string, path: string, body?: unknown, query?: QueryParams): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = tokenProvider()
    if (token) headers.Authorization = `Bearer ${token}`
    const init: RequestInit = { method, headers }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      init.body = JSON.stringify(body)
    }

    const response = await fetcher(buildUrl(baseUrl, path, query), init)
    const payload = await parseJson<ApiResponse<T>>(response)
    if (!response.ok || payload.code !== 200) {
      if ((payload.code === 401 || response.status === 401) && token && token === tokenProvider()) unauthorizedHandler?.()
      throw new ApiError(payload.message || '请求失败', payload.code || response.status, response.status, payload.traceId)
    }
    return payload.data
  }

  async function download(path: string, query?: QueryParams): Promise<Blob> {
    const headers: Record<string, string> = { Accept: 'text/csv,application/octet-stream,*/*' }
    const token = tokenProvider()
    if (token) headers.Authorization = `Bearer ${token}`

    const response = await fetcher(buildUrl(baseUrl, path, query), { method: 'GET', headers })
    if (!response.ok) {
      const payload = await parseJson<ApiResponse<unknown>>(response)
      if ((payload.code === 401 || response.status === 401) && token && token === tokenProvider()) unauthorizedHandler?.()
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
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = tokenProvider()
    if (token) headers.Authorization = `Bearer ${token}`

    const response = await fetcher(buildUrl(baseUrl, path), { method: 'POST', headers, body: form })
    const payload = await parseJson<ApiResponse<T>>(response)
    if (!response.ok || payload.code !== 200) {
      if ((payload.code === 401 || response.status === 401) && token && token === tokenProvider()) unauthorizedHandler?.()
      throw new ApiError(payload.message || '上传失败', payload.code || response.status, response.status, payload.traceId)
    }
    return payload.data
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

async function parseJson<T>(response: Response): Promise<T> {
  const text = await response.text()
  if (!text) {
    return { code: response.status, message: response.statusText, data: null } as T
  }
  return JSON.parse(text) as T
}

import { authSession } from './session.ts'

export const httpClient = createHttpClient()
