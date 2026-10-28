import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createRedeemApi } from '../redeem.ts'

/**
 * 积分运营 API 契约测试。
 *
 * 与 coffee.test.ts 同一目的：锁住「前端调用路径」与「后端 @Mapping」的一致性。
 * 两侧任一端改路径而另一端没跟上，这里就会红。
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
  return { calls, api: createRedeemApi(createHttpClient({ baseUrl: '/api/admin', tokenProvider: () => 'token', fetcher })) }
}

test('redeem api maps product CRUD to backend endpoints', async () => {
  const { calls, api } = createRecordingApi()

  await api.products.list({ pageNum: 1, pageSize: 10, keyword: '拿铁' })
  await api.products.create({
    id: 'rw-latte', name: '经典拿铁', pointsCost: 1280, stock: null, perMemberLimit: 1, sortOrder: 2, enabled: true,
  })
  await api.products.update('rw-latte', {
    name: '经典拿铁', pointsCost: 1280, stock: 50, perMemberLimit: 1, sortOrder: 2, enabled: true,
  })
  await api.products.setEnabled('rw-latte', false)
  await api.products.remove('rw-latte')

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/admin/redeem/products?pageNum=1&pageSize=10&keyword=%E6%8B%BF%E9%93%81',
    'POST /api/admin/redeem/products',
    'PUT /api/admin/redeem/products/rw-latte',
    'PUT /api/admin/redeem/products/rw-latte/enabled',
    'DELETE /api/admin/redeem/products/rw-latte',
  ])

  // stock = null 必须原样出现在请求体里：null 是「不限量」的业务语义，
  // 被 JSON.stringify 丢掉的话后端会当成未传，库存规则就变了
  assert.deepEqual(JSON.parse(calls[1].body ?? '{}'), {
    id: 'rw-latte', name: '经典拿铁', pointsCost: 1280, stock: null, perMemberLimit: 1, sortOrder: 2, enabled: true,
  })
  // 上下架请求体必须只有 enabled 一个字段，不得退化成整行更新
  assert.deepEqual(JSON.parse(calls[3].body ?? '{}'), { enabled: false })
})

test('redeem api maps order listing and verify endpoints', async () => {
  const { calls, api } = createRecordingApi()

  await api.orders.list({ pageNum: 2, pageSize: 20, status: 'PENDING' })
  await api.orders.verify('00012345')

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/admin/redeem/orders?pageNum=2&pageSize=20&status=PENDING',
    'POST /api/admin/redeem/orders/verify',
  ])
  // 核销只传核销码：服务端按码定位，前端不接触 memberId / 兑换单 id
  assert.deepEqual(JSON.parse(calls[1].body ?? '{}'), { code: '00012345' })
})
