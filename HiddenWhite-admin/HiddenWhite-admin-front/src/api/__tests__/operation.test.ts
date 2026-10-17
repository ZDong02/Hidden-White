import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createOperationApi } from '../operation.ts'

/**
 * 门店运营 API 契约测试。
 *
 * 重点锁住「营业状态 / 运营时段 / 日流量」三组路径，
 * 其中 traffic.adjust 把日期拼进 URL，是最容易在改路径时漏改的地方。
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
  return { calls, api: createOperationApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher })) }
}

test('operation api maps status switch to backend endpoint', async () => {
  const { calls, api } = createRecordingApi()

  await api.status.get()
  await api.status.update({ manualClosed: true, statusNote: '设备维护暂停营业' })
  await api.status.update({ manualClosed: false })

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/operation/status',
    'PUT /api/operation/status',
    'PUT /api/operation/status',
  ])
  assert.deepEqual(JSON.parse(calls[1].body ?? '{}'), { manualClosed: true, statusNote: '设备维护暂停营业' })
  assert.deepEqual(JSON.parse(calls[2].body ?? '{}'), { manualClosed: false })
})

test('operation api maps weekly hours endpoints', async () => {
  const { calls, api } = createRecordingApi()

  await api.hours.get()
  await api.hours.update({
    defaultOpen: '08:00',
    defaultClose: '20:00',
    days: [
      { dayOfWeek: 1, openTime: '08:00', closeTime: '20:00', closed: false },
      { dayOfWeek: 2, openTime: '08:00', closeTime: '20:00', closed: false },
      { dayOfWeek: 0, openTime: '09:00', closeTime: '18:00', closed: true },
    ],
  })

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/operation/hours',
    'PUT /api/operation/hours',
  ])
  const body = JSON.parse(calls[1].body ?? '{}')
  assert.equal(body.defaultOpen, '08:00')
  assert.equal(body.days.length, 3)
  assert.equal(body.days[2].closed, true, '例休日必须带 closed 标记')
})

test('operation api maps traffic queries and per-date adjustment', async () => {
  const { calls, api } = createRecordingApi()

  await api.traffic.today()
  await api.traffic.list({ pageNum: 1, pageSize: 10 })
  await api.traffic.adjust('2026-10-16', { orderCount: 128, revenue: 4120, reason: '线下活动补录' })

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /api/operation/traffic/today',
    'GET /api/operation/traffic?pageNum=1&pageSize=10',
    'PUT /api/operation/traffic/2026-10-16',
  ])
  assert.deepEqual(JSON.parse(calls[2].body ?? '{}'), { orderCount: 128, revenue: 4120, reason: '线下活动补录' })
})

test('operation api sends adjustment reason that backend requires', async () => {
  // 后端 OperationService 强制要求调整原因非空且 ≤200 字，
  // 缺 reason 会被 400 拒绝；这里固定带上，锁住调用侧约定。
  const { calls, api } = createRecordingApi()

  await api.traffic.adjust('2026-10-15', { orderCount: 96, reason: '手工核对后修正' })

  assert.equal(calls[0].method, 'PUT')
  assert.ok(JSON.parse(calls[0].body ?? '{}').reason, '调整必须携带原因')
})
