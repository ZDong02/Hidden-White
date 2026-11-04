import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createCouponApi } from '../coupon.ts'

/**
 * 优惠券运营 API 契约测试。
 *
 * 与其余 api 测试同一目的：锁住「前端调用路径 / 请求体形状」与后端 @Mapping 的一致性。
 * 注意这里<b>只锁拼装，不校验后端语义</b> —— 「前端能发出去、后端拒绝」这类缺陷
 * 它抓不到，必须靠服务层单测与真接口跑一遍（参见 CouponAdminService 的校验）。
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
  return { calls, api: createCouponApi(createHttpClient({ baseUrl: '/api/admin', tokenProvider: () => 'token', fetcher })) }
}

test('coupon api maps config read and write', async () => {
  const { calls, api } = createRecordingApi()

  await api.config()
  await api.saveConfig({
    enabled: true,
    amount: 6,
    minSpend: 20,
    validDays: 7,
    maxHeld: 2,
    totalStock: null,
    title: '每周好券',
    subtitle: '满 20 元减 6 元',
    description: '每周一张',
  })

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/admin/coupons/config',
    'PUT /api/admin/coupons/config',
  ])

  // totalStock = null 必须原样出现在请求体里：null 是「不限量」的业务语义，
  // 被 JSON.stringify 丢掉的话后端会当成未传，配置从「不限量」变成另一回事
  assert.deepEqual(JSON.parse(calls[1].body ?? '{}'), {
    enabled: true,
    amount: 6,
    minSpend: 20,
    validDays: 7,
    maxHeld: 2,
    totalStock: null,
    title: '每周好券',
    subtitle: '满 20 元减 6 元',
    description: '每周一张',
  })
})

test('coupon api maps records listing with filters', async () => {
  const { calls, api } = createRecordingApi()

  await api.records({ pageNum: 2, pageSize: 10, status: 'USED', weekKey: '2026-11-02', keyword: '138' })
  await api.weeks()

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/admin/coupons/records?pageNum=2&pageSize=10&status=USED&weekKey=2026-11-02&keyword=138',
    'GET /api/admin/coupons/weeks',
  ])
})
