import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createCoffeeApi } from '../coffee.ts'

/**
 * 咖啡业务 API 契约测试。
 *
 * 目的是锁住「前端调用路径」与「后端 @Mapping」的一致性：
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
  return { calls, api: createCoffeeApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher })) }
}

test('coffee api maps product CRUD to backend endpoints', async () => {
  const { calls, api } = createRecordingApi()

  await api.products.list({ pageNum: 1, pageSize: 10, keyword: '拿铁' })
  await api.products.create({ name: '澳白', price: 32, category: 1, hasMilk: true, enabled: true })
  // 后端 updateProduct 是整行更新、必填校验齐全，故必须传全量字段
  await api.products.update('latte', { name: '拿铁', price: 35, category: 2, hasMilk: true, enabled: true })
  // 上下架走独立端点：只带 enabled，不会触发整行更新的必填校验
  await api.products.setEnabled('latte', false)
  await api.products.remove('latte')

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/coffee/products?pageNum=1&pageSize=10&keyword=%E6%8B%BF%E9%93%81',
    'POST /api/coffee/products',
    'PUT /api/coffee/products/latte',
    'PUT /api/coffee/products/latte/enabled',
    'DELETE /api/coffee/products/latte',
  ])
  assert.deepEqual(JSON.parse(calls[1].body ?? '{}'), { name: '澳白', price: 32, category: 1, hasMilk: true, enabled: true })
  assert.deepEqual(JSON.parse(calls[2].body ?? '{}'), { name: '拿铁', price: 35, category: 2, hasMilk: true, enabled: true })
  // 防回归：上下架请求体必须只有 enabled 一个字段
  assert.deepEqual(JSON.parse(calls[3].body ?? '{}'), { enabled: false })
})

test('product toggle never reuses the full-row update endpoint', async () => {
  // 2026-10-26 缺陷回归：上下架曾调用 PUT /coffee/products/{id} 只带 { enabled }，
  // 后端按整行更新校验 name/price/category，直接抛「商品名称不能为空」，商品下不掉。
  const { calls, api } = createRecordingApi()

  await api.products.setEnabled('latte', false)
  await api.products.setEnabled('latte', true)

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'PUT /api/coffee/products/latte/enabled',
    'PUT /api/coffee/products/latte/enabled',
  ])
  assert.ok(
    calls.every(c => c.url.endsWith('/enabled')),
    '上下架不得落到不带 /enabled 后缀的整行更新路径上',
  )
})

test('coffee api maps option and benefit endpoints', async () => {
  const { calls, api } = createRecordingApi()

  await api.options.list()
  await api.options.create({ type: 'SIZE', label: '大杯', extraPrice: 4, sortOrder: 3, enabled: true })
  await api.options.update(7, { label: '超大杯', extraPrice: 8 })
  await api.options.remove(7)

  await api.benefits.list()
  await api.benefits.create({ icon: '◇', title: '积分兑换', description: '好礼随心兑', sortOrder: 1, enabled: true })
  await api.benefits.update(3, { title: '生日礼遇', description: '生日专属惊喜' })
  await api.benefits.remove(3)

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/coffee/options',
    'POST /api/coffee/options',
    'PUT /api/coffee/options/7',
    'DELETE /api/coffee/options/7',
    'GET /api/coffee/benefits',
    'POST /api/coffee/benefits',
    'PUT /api/coffee/benefits/3',
    'DELETE /api/coffee/benefits/3',
  ])
})

test('coffee api maps order, member and store endpoints', async () => {
  const { calls, api } = createRecordingApi()

  await api.orders.list({ pageNum: 1, pageSize: 20 })
  await api.orders.complete('a1b2c3d4')
  await api.orders.remove('a1b2c3d4')

  await api.members.list({ pageNum: 1, pageSize: 10, keyword: 'demo' })
  await api.members.update('member-1', { level: '金卡会员', points: 120, coupons: 2, growthValue: 300 })
  await api.members.remove('member-1')

  await api.store.get()
  await api.store.update({
    name: '隐白咖啡 · 静安嘉里店',
    address: '上海市静安区南京西路 1515 号',
    shortAddress: '静安嘉里中心',
    latitude: 31.2237,
    longitude: 121.4453,
    distance: '',
    openingTime: '08:00',
    closingTime: '20:00',
  })

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/coffee/orders?pageNum=1&pageSize=20',
    'PUT /api/coffee/orders/a1b2c3d4/complete',
    'DELETE /api/coffee/orders/a1b2c3d4',
    'GET /api/coffee/members?pageNum=1&pageSize=10&keyword=demo',
    'PUT /api/coffee/members/member-1',
    'DELETE /api/coffee/members/member-1',
    'GET /api/coffee/store',
    'PUT /api/coffee/store',
  ])
  assert.deepEqual(JSON.parse(calls[4].body ?? '{}'), { level: '金卡会员', points: 120, coupons: 2, growthValue: 300 })
})

test('coffee api exposes summary and accepts soft-delete notice on product removal', async () => {
  // 删除已产生订单的商品时后端改为软下架，并返回提示文案；
  // remove 的返回类型必须能承载该 notice，否则页面会丢掉这层提示。
  const calls: Array<{ url: string; method: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET' })
    return new Response(
      JSON.stringify({ code: 200, message: 'success', data: { notice: '该商品已产生订单，已改为下架' }, traceId: 't' }),
      { status: 200, headers: { 'content-type': 'application/json' } },
    )
  }
  const api = createCoffeeApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  await api.summary()
  const result = await api.products.remove('latte')

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/coffee/summary',
    'DELETE /api/coffee/products/latte',
  ])
  assert.equal(result.notice, '该商品已产生订单，已改为下架')
})
