/**
 * 登录态失效（401）的统一处理测试。
 *
 * 真实故障（2026-11-05）：后端重启后，内存态 TokenStore 清空，小程序里存的 token 全部失效。
 * 改造前 `requestApi` 收到 401 只清 token、**不清 `currentUser`** —— 界面仍然认为「已登录」，
 * 于是四个面板各自发请求、各自弹一次错，一次失效变成满屏 401 加一串报错。
 *
 * 这里钉住两件事：
 *   ① 401 必须清整个会话（`currentUser` 置空 → `isLoggedIn` 变 false）；
 *   ② 并发/连续 401 只处理一次，不能连弹好几条「登录已过期」。
 *
 * 运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'

/* 假的 uni：storage 用内存 Map；request 按队列返回，健康探测固定 200 */
const storage = new Map()
const toasts = []
let queue = []

globalThis.uni = {
  getStorageSync: (key) => (storage.has(key) ? storage.get(key) : ''),
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: (key) => storage.delete(key),
  showToast: (options) => toasts.push(options.title),
  request: (options) => {
    const url = String(options.url)
    if (url.includes('/health')) {
      setTimeout(() => options.success({ statusCode: 200, data: { code: 200 } }), 0)
      return
    }
    const next = queue.shift() || { statusCode: 200, data: {} }
    setTimeout(() => options.success({ statusCode: next.statusCode, data: next.data }), 0)
  }
}

/* 预置「已登录」：手填地址（跳过候选探测）+ token + 用户信息 */
storage.set('hw-api-base', 'http://test.local/api')
storage.set('hw-api-base-manual', '1')
storage.set('hidden-white-token', 'stale-token')
storage.set('hidden-white-current-user', { id: 'member-1', displayName: '测试', token: 'stale-token' })

const { requestApi, setUnauthorizedHandler } = await import('../config/backend.js')
const { currentUser, isLoggedIn } = await import('../store/auth.js')

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

test('401 会清整个会话，而不只是 token', async () => {
  assert.equal(isLoggedIn.value, true, '前置：应处于已登录状态')

  queue = [{ statusCode: 401, data: { message: '未认证或登录已过期' } }]
  await assert.rejects(() => requestApi('/orders'), /未认证/)

  assert.equal(storage.has('hidden-white-token'), false, 'token 必须被清掉')
  assert.equal(currentUser.value, null, 'currentUser 必须被清空 —— 否则界面仍以为已登录')
  assert.equal(isLoggedIn.value, false, 'isLoggedIn 应随 currentUser 一起变为 false')
  assert.equal(
    toasts.filter((title) => String(title).includes('登录已过期')).length,
    1,
    '只应提示一次'
  )
})

test('连续 401 只触发一次处理（冷却窗口内合并）', async () => {
  // 上面的用例刚处理过，仍在 3s 冷却窗口内 —— 这里验证「合并」确实生效
  let handled = 0
  setUnauthorizedHandler(() => {
    handled += 1
  })

  currentUser.value = { id: 'member-1', displayName: '测试' }
  storage.set('hidden-white-token', 'stale-token')

  queue = [
    { statusCode: 401, data: { message: '未认证' } },
    { statusCode: 401, data: { message: '未认证' } },
    { statusCode: 401, data: { message: '未认证' } }
  ]
  await Promise.allSettled([requestApi('/orders'), requestApi('/members/1'), requestApi('/coupons')])

  assert.equal(handled, 0, '冷却窗口内的 401 不应重复触发处理器')

  // 冷却结束后，新的一次失效仍要能正常处理（不能永久哑掉）
  await sleep(3100)
  queue = [{ statusCode: 401, data: { message: '未认证' } }]
  await assert.rejects(() => requestApi('/orders'))

  assert.equal(handled, 1, '冷却结束后的 401 必须重新触发处理')
})
