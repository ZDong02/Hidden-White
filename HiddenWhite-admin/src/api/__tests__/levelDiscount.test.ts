import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createLevelDiscountApi } from '../levelDiscount.ts'

/**
 * 会员折扣 API 契约测试。
 *
 * <p>锁住两件容易在重构中悄悄变化的事：
 * ① 等级键走<b>请求体</b>而不是 URL 路径 —— 中文等级名进路径要靠各层编码一致；
 * ② 启停走独立端点、请求体只有两个字段，不退化成整行更新
 *    （整行更新带必填校验，会把「只想关掉」的调用拒掉）。
 */

function createRecordingApi() {
  const calls: Array<{ url: string; method: string; body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({ code: 200, message: 'success', data: null, traceId: 't' }), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    })
  }
  return {
    calls,
    api: createLevelDiscountApi(createHttpClient({ baseUrl: '/api/admin', tokenProvider: () => 'token', fetcher })),
  }
}

test('level discount api maps overview and full save', async () => {
  const { calls, api } = createRecordingApi()

  await api.overview()
  await api.save({
    levelKey: '黄金会员',
    enabled: true,
    baseRate: 0.95,
    minSpend: 0,
    note: '全场 9.5 折',
    dailyRates: { 3: 0.88 },
  })

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/admin/level-discounts',
    'PUT /api/admin/level-discounts',
  ])

  // 等级键在 body 里；dailyRates 只带覆盖的那天，未传的天由服务端回落到基准
  assert.deepEqual(JSON.parse(calls[1].body ?? '{}'), {
    levelKey: '黄金会员',
    enabled: true,
    baseRate: 0.95,
    minSpend: 0,
    note: '全场 9.5 折',
    dailyRates: { 3: 0.88 },
  })
})

test('level discount api maps enabled toggle to its own endpoint', async () => {
  const { calls, api } = createRecordingApi()

  await api.setEnabled({ levelKey: '钻石会员', enabled: false })

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), ['PUT /api/admin/level-discounts/enabled'])
  // 请求体只能有这两个字段：多带一个 baseRate 就等于整行更新，语义变了
  assert.deepEqual(JSON.parse(calls[0].body ?? '{}'), { levelKey: '钻石会员', enabled: false })
})
