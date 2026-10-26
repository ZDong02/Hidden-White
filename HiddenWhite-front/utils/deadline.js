/**
 * 给 Promise 加一道绝对截止。
 *
 * 为什么需要它：uni.request 的 timeout 是「空闲超时」，连接阶段卡住时不一定回调
 * （换网络后旧 IP 变成静默丢包最常见）。任何 await 挂在这种 Promise 上都会永远停住，
 * 绑定它的界面状态（骨架屏、加载态）随之永远停住 —— 比报错更糟，
 * 因为用户连「重试」的入口都看不到。
 *
 * 用法：
 *   const value = await withDeadline(task, 3000, '兜底值')   // 超时按兜底值继续
 *   const value = await withDeadline(task, 3000)             // 超时返回 DEADLINE 哨兵
 *
 * @param {Promise} promise 被保护的异步任务
 * @param {number} ms 上限（毫秒）
 * @param {any} fallback 到点仍未 settle 时用于 resolve 的值，默认 DEADLINE 哨兵
 * @returns {Promise<any>}
 */
export const DEADLINE = Symbol('deadline')

export function withDeadline(promise, ms, fallback = DEADLINE) {
  return new Promise((resolve, reject) => {
    let settled = false

    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      resolve(fallback)
    }, ms)

    promise.then(
      (value) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        resolve(value)
      },
      (error) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        reject(error)
      }
    )
  })
}
