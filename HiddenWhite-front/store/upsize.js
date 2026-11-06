import { reactive, readonly, watch } from 'vue'
import { requestApi } from '../config/backend'
import { currentUser, isLoggedIn } from './auth'

/**
 * 免费升杯状态：本月额度、结算方式、杯型。
 *
 * <p><b>所有判定都在服务端</b>，这里只负责取回结论并缓存。哪一天是月初、这个月
 * 已经用了几次、还剩几次免费、这次该扣多少积分 —— 全部由 {@code UpsizeService}
 * 按数据库里的策略算。客户端再算一遍的话，跨零点、换时区、后台改了额度都会让
 * 「会员页说还能升、结算页说不行」。
 *
 * <p>不落 storage：额度是每月变化的即时状态，缓存下来只会显示上月的数据。
 */
const state = reactive({
  /** 服务端下发的 UpsizeDecision；未登录 / 加载失败为 null */
  data: null,
  loading: false,
  error: ''
})

export const upsize = readonly(state)

export async function loadUpsize(force = false) {
  if (!isLoggedIn.value) {
    state.data = null
    return null
  }
  if (state.loading) return state.data
  if (state.data && !force) return state.data

  state.loading = true
  state.error = ''
  try {
    state.data = await requestApi('/members/upsize')
    return state.data
  } catch (error) {
    // 升杯读取失败只是少一条权益说明，不该连累会员页的积分与礼遇
    state.error = error.message || '免费升杯加载失败'
    return null
  } finally {
    state.loading = false
  }
}

/** 退出登录时清空，避免下一位登录者看到上一个人的额度。 */
export function resetUpsize() {
  state.data = null
  state.error = ''
  state.loading = false
}

/**
 * 礼遇卡文案：四种状态只换第二行，卡片本身不变。
 *
 * <p>文案由 {@code reason} 的枚举码决定，<b>不解析中文</b>——
 * 服务端改一个字的说明，前端不该跟着失效。
 *
 * @param {object|null} data 服务端下发的 UpsizeDecision
 */
export function upsizeCardText(data) {
  if (!data) return { description: '每月专属礼遇', state: '' }

  if (data.available) {
    // freeLeft = -1 表示该等级免费不限次
    if (data.freeLeft === -1) return { description: '本月免费升杯不限次', state: 'AVAILABLE' }
    if (data.freeLeft > 0) {
      const cost = data.payType === 'POINTS' ? `，之后 ${data.pointsCost} 积分` : ''
      return { description: `本月还可免费升 ${data.freeLeft} 次${cost}`, state: 'AVAILABLE' }
    }
    return { description: `${data.pointsCost} 积分 / 次`, state: '' }
  }

  switch (data.reason) {
    case 'INSUFFICIENT_POINTS':
      return { description: `积分不足 · 需 ${data.pointsCost} 分`, state: '' }
    case 'LIMIT_REACHED':
      return { description: '本月已用完', state: '' }
    case 'NO_POINTS_COST':
      return { description: '本月免费次数已用完', state: '' }
    case 'DISABLED':
    case 'NO_POLICY':
    case 'SIZE_MISSING':
      return { description: '暂不可用', state: '' }
    default:
      return { description: '每月专属礼遇', state: '' }
  }
}

/** 会话失效（401 / 主动登出）时立刻清空，否则界面会继续显示上一个账号的额度。 */
watch(currentUser, (user) => {
  if (!user) resetUpsize()
})
