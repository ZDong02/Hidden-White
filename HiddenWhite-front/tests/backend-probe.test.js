/**
 * 后端地址解析的「永不返回」防护测试。
 *
 * 故障场景（2026-10-26 线上复现）：
 *   换网络后，config/backend-hosts.js 里的旧 IP 变成静默丢包 —— 连接不成也不报错。
 *   uni.request 的 timeout 只管空闲超时，握手阶段卡住时不一定回调；
 *   而地址解析要等「所有候选都回来」才收场，于是整个解析永不返回，
 *   首屏骨架屏一直转、连错误提示都没有。
 *
 * 这里用「永远不回调的 uni.request」把这个场景钉死：
 *   - 解析必须在硬截止内收场，并回落兜底地址；
 *   - 单次请求也必须有终局（超时 reject），不能挂住界面。
 *
 * 运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'

/* 假的 uni：所有网络请求都「静默丢包」，只记录不回调；storage 用内存 Map */
const storage = new Map()
const hung = []

globalThis.uni = {
  getStorageSync: (key) => (storage.has(key) ? storage.get(key) : ''),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  request: (options) => hung.push(options.url)
}

const { getApiBaseUrl, requestApi } = await import('../config/backend.js')

test('候选地址全部静默丢包时，地址解析必须在硬截止内收场', { timeout: 15000 }, async () => {
  const started = Date.now()
  const base = await getApiBaseUrl()
  const elapsed = Date.now() - started

  assert.equal(base, 'http://localhost:8080/api', '应回落到本机兜底地址')
  assert.ok(elapsed < 6000, `解析耗时 ${elapsed}ms，超出硬截止（应 ≤ PROBE_DEADLINE + 余量）`)
  assert.ok(hung.length >= 2, '探测应真的发出过请求')
})

test('请求挂住不回调时，requestApi 必须超时拒绝而不是一直 pending', { timeout: 5000 }, async () => {
  const started = Date.now()
  await assert.rejects(
    () => requestApi('/products', { timeout: 200 }),
    /请求超时/
  )
  const elapsed = Date.now() - started
  assert.ok(elapsed < 2000, `拒绝耗时 ${elapsed}ms，远超 timeout + 余量`)
})
