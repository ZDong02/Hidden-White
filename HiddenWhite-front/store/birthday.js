import { reactive, readonly } from 'vue'
import { requestApi } from '../config/backend'
import { isLoggedIn } from './auth'

/**
 * 生日礼遇状态。
 *
 * 抽成独立模块而不是塞进 MemberPanel，是因为「卡片」和「弹层」必须分处两个组件：
 * 弹层是 position: fixed，若挂在 swiper-item 内部，微信端会相对 swiper-item 定位而错位
 * （pages/index/index.vue 里对取餐凭证弹窗有同样的说明）。弹层只能留在页面层，
 * 两个组件共享同一份状态，所以状态放在这里。
 *
 * 状态、窗口起止、倒计时全部由服务端算好下发，前端不做任何日期比较 ——
 * 闰年、月末、时区差异都不该由各机型各自解释。
 */
const state = reactive({
  /** 服务端下发的 BirthdayStatus；未登录/加载失败为 null */
  data: null,
  loading: false,
  error: '',
  /** 领取成功计数：卡片据此重新拉一次会员资料（刷新积分数字） */
  claimedTick: 0
})

export const birthday = readonly(state)

/** 读取状态；已加载过就不重复请求（除非 force）。 */
export async function loadBirthday(force = false) {
  if (!isLoggedIn.value) {
    state.data = null
    return null
  }
  if (state.loading) return state.data
  if (state.data && !force) return state.data

  state.loading = true
  state.error = ''
  try {
    state.data = await requestApi('/members/birthday')
    return state.data
  } catch (error) {
    // 会员页是首屏信息密度最高的一屏，生日礼遇挂掉不能连累积分与礼遇列表
    state.error = error.message || '生日礼遇加载失败'
    return null
  } finally {
    state.loading = false
  }
}

/** 填写 / 修改生日（一年一改，被拒绝时抛错由弹层展示文案）。 */
export async function saveBirthday(month, day) {
  const data = await requestApi('/members/me/birthday', {
    method: 'PUT',
    data: { month, day }
  })
  state.data = data
  return data
}

/** 领取；重复领取服务端也返回 200，这里不需要额外判重。 */
export async function claimBirthday() {
  const data = await requestApi('/members/birthday/claim', { method: 'POST' })
  state.data = data
  if (data?.state === 'CLAIMED') state.claimedTick += 1
  return data
}

/** 退出登录时清空，避免下一个人登录后看到上一个人的生日状态。 */
export function resetBirthday() {
  state.data = null
  state.error = ''
  state.loading = false
  state.claimedTick = 0
}
