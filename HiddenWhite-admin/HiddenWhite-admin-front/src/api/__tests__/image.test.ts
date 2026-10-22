import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createCoffeeApi } from '../coffee.ts'

/**
 * 图片上传 API 契约测试。
 *
 * 锁住三件事：
 * 1. 路径与后端 @Mapping 一致（改了一侧另一侧没跟上，这里就会红）；
 * 2. 上传走 multipart 且不手写 Content-Type（手写会丢掉 boundary，文件传不上去）；
 * 3. 列表图 / 详情图各自带上自己的 slot，取值不同不能串。
 */

function createRecordingApi() {
  const calls: Array<{ url: string; method: string; body: unknown; headers: Record<string, string> }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({
      url: String(url),
      method: init?.method ?? 'GET',
      body: init?.body,
      headers: (init?.headers ?? {}) as Record<string, string>,
    })
    return new Response(
      JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          maxBytes: 10485760,
          list: { width: 284, height: 240, mode: 'cover' },
          detail: { width: 1200, height: 636, mode: 'cover' },
        },
        traceId: 't',
      }),
      { status: 200, headers: { 'content-type': 'application/json' } },
    )
  }
  return { calls, api: createCoffeeApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher })) }
}

test('coffee api reads image target spec from backend', async () => {
  const { calls, api } = createRecordingApi()

  const spec = await api.images.spec()

  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), ['GET /api/coffee/images/spec'])
  // 列表图与详情图目标宽高独立配置，前端按用途取用
  assert.equal(spec.list.width, 284)
  assert.equal(spec.detail.width, 1200)
})

test('coffee api uploads image as multipart with slot parameter', async () => {
  const { calls, api } = createRecordingApi()
  const file = new File([new Uint8Array([1, 2, 3])], 'latte.png', { type: 'image/png' })

  await api.images.upload(file, 'detail')

  assert.equal(calls.length, 1)
  assert.equal(calls[0].method, 'POST')
  assert.equal(calls[0].url, '/api/coffee/images/upload')

  const body = calls[0].body as FormData
  assert.ok(body instanceof FormData)
  assert.equal(body.get('slot'), 'detail')
  assert.ok(body.get('file') instanceof File)
})

test('image upload does not set content-type header manually', async () => {
  // FormData 的 Content-Type 必须由浏览器生成（带 boundary）；
  // 一旦手动设置，服务端解析不出文件边界，上传会直接失败。
  const { calls, api } = createRecordingApi()
  const file = new File([new Uint8Array([1])], 'latte.png', { type: 'image/png' })

  await api.images.upload(file, 'list')

  const headers = calls[0].headers
  const contentType = Object.entries(headers).find(([key]) => key.toLowerCase() === 'content-type')
  assert.equal(contentType, undefined)
  // 鉴权头仍需带上
  assert.equal(headers.Authorization, 'Bearer token')
})

test('image upload keeps list and detail slots distinct', async () => {
  const { calls, api } = createRecordingApi()
  const file = new File([new Uint8Array([1])], 'latte.png', { type: 'image/png' })

  await api.images.upload(file, 'list')
  await api.images.upload(file, 'detail')

  const slots = calls.map(c => (c.body as FormData).get('slot'))
  assert.deepEqual(slots, ['list', 'detail'])
})
