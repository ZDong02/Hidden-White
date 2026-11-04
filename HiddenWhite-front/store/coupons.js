import { reactive, readonly } from 'vue'
import { requestApi } from '../config/backend'
import { isLoggedIn } from './auth'

/**
 * 优惠券状态（券包 + 本周领取状态）。
 *
 * <p>抽成独立模块而不是塞在券包页里，是因为会员面板也要读同一份数据：
 * 「优惠券」格子上的数字与「本周可领」角标必须与券包页完全一致，
 * 两处各请求一次就会出现「面板显示 0 张、点进去有 2 张」。
 *
 * <p><b>状态、重置时间、能否领取全部由服务端算好</b>。自然周的边界涉及时区与跨年，
 * 下放到客户端会让同一会员在不同机型上看到不同结论，而结论直接决定按钮亮不亮。
 */
const state = reactive({
  /** 服务端下发的券包；未登录 / 加载失败为 null */
  data: null,
  loading: false,
  error: '',
  /** 领取请求进行中：按钮据此防连点（服务端本身幂等，这里只是避免重复请求） */
  claiming: false,
  /** 领取成功计数：会员面板据此重拉会员资料，让券数跟着跳一下 */
  claimedTick: 0
})

export const coupons = readonly(state)

/** 券包；已加载过就不重复请求（除非 force）。 */
export async function loadCoupons(force = false) {
  if (!isLoggedIn.value) {
    state.data = null
    return null
  }
  if (state.loading) return state.data
  if (state.data && !force) return state.data

  state.loading = true
  state.error = ''
  try {
    state.data = await requestApi('/coupons')
    return state.data
  } catch (error) {
    // 券包失败不能连累会员页的积分与礼遇清单
    state.error = error.message || '优惠券加载失败'
    return null
  } finally {
    state.loading = false
  }
}

/**
 * 领取本周优惠券。
 *
 * <p>服务端对「本周已领过」返回 200 + 同一张券（幂等），所以这里不需要额外判重。
 * 真正会抛错的是：发行量领完、持有超限、功能关闭 —— 这些必须把原文案透出去，
 * 顾客需要知道「为什么领不到」以及「什么时候再来」。
 */
export async function claimCoupon() {
  if (state.claiming) return state.data
  state.claiming = true
  try {
    const wallet = await requestApi('/coupons/claim', { method: 'POST' })
    state.data = wallet
    state.error = ''
    if (wallet?.weekState === 'CLAIMED') state.claimedTick += 1
    return wallet
  } finally {
    state.claiming = false
  }
}

/**
 * 结算页选券视图（按可用 / 不可用分组）。
 *
 * <p>不进全局状态：它依赖当前订单金额，是一个随购物车变化的瞬时视图，
 * 缓存下来只会在顾客加了一杯之后给出过期的可用性判断。
 */
export async function loadUsableCoupons(subtotal) {
  const amount = Number(subtotal) > 0 ? Number(subtotal) : 0
  return requestApi(`/coupons/usable?subtotal=${amount}`)
}

/** 退出登录时清空，避免下一个人登录后看到上一个人的券。 */
export function resetCoupons() {
  state.data = null
  state.error = ''
  state.loading = false
  state.claiming = false
  state.claimedTick = 0
}
