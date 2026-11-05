import { reactive, readonly, watch } from 'vue'
import { requestApi } from '../config/backend'
import { currentUser, isLoggedIn } from './auth'

/**
 * 会员等级折扣状态。
 *
 * <p><b>「今天打不打折」由服务端判定</b>，这里只负责取回结论并缓存。
 * 星期几、时区、跨零点都不在客户端解释 —— 会员在 23:59 打开页面、00:01 下单时，
 * 客户端算出来的「今天 9.5 折」与服务端的判定必然对不上，而结论直接决定
 * 顾客以为自己要付多少钱。
 *
 * <p>不落 storage：折扣是「等级 + 今天星期几」的即时函数，缓存下来只会产生过期数据。
 */
const state = reactive({
  /** 服务端下发的 MemberView（当前等级视图）；未登录 / 加载失败为 null */
  data: null,
  /** 全部等级的方案（详情页用）；与 data 分开缓存，两个页面的新鲜度需求不同 */
  catalog: null,
  catalogLoading: false,
  /** 详情页加载失败的原因。**必须与「等级不在阶梯」区分开**：
      接口挂了与等级异常在页面上是完全不同的两件事，混在一起会把人引向错误的排查方向。 */
  catalogError: '',
  loading: false,
  error: ''
})

export const levelDiscount = readonly(state)

export async function loadLevelDiscount(force = false) {
  if (!isLoggedIn.value) {
    state.data = null
    return null
  }
  if (state.loading) return state.data
  if (state.data && !force) return state.data

  state.loading = true
  state.error = ''
  try {
    state.data = await requestApi('/members/discount')
    return state.data
  } catch (error) {
    // 折扣读取失败不能连累会员页的积分与礼遇：它只是一条增值信息
    state.error = error.message || '会员折扣加载失败'
    return null
  } finally {
    state.loading = false
  }
}

/**
 * 全部等级的折扣方案（会员折扣详情页）。
 *
 * <p>与 {@link loadLevelDiscount} 分开：那个回答「我今天打几折」，这个回答
 * 「各档分别是什么、我还差多少」。详情页要横向对比，所以单独一个接口 ——
 * 把五档都塞进会员页的响应里，等于让首页为一个二级页多读 5 遍策略。
 */
export async function loadLevelCatalog(force = false) {
  if (!isLoggedIn.value) {
    state.catalog = null
    return null
  }
  if (state.catalog && !force) return state.catalog

  state.catalogLoading = true
  state.catalogError = ''
  try {
    state.catalog = await requestApi('/members/level-discounts')
    return state.catalog
  } catch (error) {
    // 不清空已有数据：强制刷新失败时保留上一次的内容，比整页变空好
    state.catalogError = error.message || '会员折扣加载失败'
    return null
  } finally {
    state.catalogLoading = false
  }
}

/** 退出登录时清空，避免下一位登录者看到上一个人的等级折扣。 */
export function resetLevelDiscount() {
  state.data = null
  state.catalog = null
  state.catalogError = ''
  state.catalogLoading = false
  state.error = ''
  state.loading = false
}

/**
 * 会话失效（401 / 主动登出）时立刻清空。
 *
 * <p>不做这一步的话，界面在被重新进入之前会继续显示上一个账号的等级折扣 ——
 * 数据已经不属于当前用户了。
 */
watch(currentUser, (user) => {
  if (!user) resetLevelDiscount()
})
