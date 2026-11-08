/**
 * 健康检查判定的双路径一致性。
 *
 * <p>背景：`probeCandidates`（地址探测）与 `checkApiHealth`（设置页自检）原先各写一份
 * `/health` 请求，超时、状态码判定、失败文案都是复制品 —— 只改一边就会出现
 * 「探测说连得上、自检说不通」。现在两者共用 `probeHealth`，这个测试锁住它的两种结论。
 *
 * <p>运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'

const storage = new Map()
let statusCode = 200

globalThis.uni = {
  getStorageSync: (key) => (storage.has(key) ? storage.get(key) : ''),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  request: (options) => {
    // 立即回包：不依赖 setTimeout，避免与硬截止赛跑导致偶发失败
    options.success?.({ statusCode, data: { code: 200 } })
  }
}

const { checkApiHealth } = await import('../config/backend.js')

test('健康检查 2xx：判定连通并给出「连接正常」', async () => {
  statusCode = 200
  const health = await checkApiHealth()

  assert.equal(health.ok, true)
  assert.equal(health.message, '连接正常')
  assert.match(health.baseUrl, /\/api$/)
})

test('健康检查非 2xx/3xx：判定不通并带上状态码', async () => {
  statusCode = 503
  const health = await checkApiHealth()

  assert.equal(health.ok, false)
  assert.match(health.message, /503/)
})
