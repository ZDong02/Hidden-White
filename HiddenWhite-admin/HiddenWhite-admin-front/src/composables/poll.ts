import { onScopeDispose } from 'vue'

/* ============================================================
   列表页自动轮询：页面停留期间定时刷新（如订单列表等新订单）。
   标签页隐藏时暂停拉取，回到前台立即补刷一次，组件卸载时清理。
   ============================================================ */

export function useAutoRefresh(refresh: () => unknown, intervalMs = 10_000) {
  let timer: number | undefined

  const tick = () => {
    if (document.hidden) return
    void Promise.resolve(refresh()).catch(() => {/* 静默失败，不打断轮询 */})
  }
  const onVisibility = () => {
    if (!document.hidden) tick()
  }

  function start() {
    if (timer !== undefined) return
    timer = window.setInterval(tick, intervalMs)
    document.addEventListener('visibilitychange', onVisibility)
  }

  function stop() {
    if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
    document.removeEventListener('visibilitychange', onVisibility)
  }

  start()
  onScopeDispose(stop)

  return { start, stop }
}
