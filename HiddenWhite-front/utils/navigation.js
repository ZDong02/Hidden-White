import { STORE } from '../config/constants'
import { normalizeCoordinate } from './location'

// 订单 / 会员 / 我的已合并进壳页面（横向 Tab 面板），通过 ?tab= 直达对应分段
const PAGES = Object.freeze({
  index: '/pages/index/index',
  member: '/pages/index/index?tab=member',
  profile: '/pages/index/index?tab=profile',
  auth: '/pages/auth/auth',
  detail: '/pages/detail/detail',
  checkout: '/pages/checkout/checkout',
  order: '/pages/index/index?tab=orders'
})

let navigationPending = false

function getUrl(page, params = {}) {
  const path = PAGES[page]
  if (!path) {
    console.error(`未知页面路由: ${page}`)
    return null
  }

  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')

  // 目标路径自带 query（如 ?tab=orders）时用 & 追加参数，避免出现第二个问号
  return query ? `${path}${path.includes('?') ? '&' : '?'}${query}` : path
}

function currentPageUrl() {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  return current ? `/${current.route}` : ''
}

function navigate(method, url) {
  if (!url || navigationPending || currentPageUrl() === url.split('?')[0]) return

  navigationPending = true
  uni[method]({
    url,
    complete: () => {
      navigationPending = false
    }
  })
}

// Open a page while retaining the current page for the custom back button.
export function openPage(page, params) {
  navigate('navigateTo', getUrl(page, params))
}

// Replace the current page after a completed flow such as checkout.
export function replacePage(page, params) {
  navigate('redirectTo', getUrl(page, params))
}

// Custom bottom navigation uses root destinations without growing the page stack.
export function resetToPage(page, params) {
  navigate('reLaunch', getUrl(page, params))
}

export function navigateBack() {
  if (navigationPending) return

  const pages = getCurrentPages()
  if (pages.length > 1) {
    navigationPending = true
    uni.navigateBack({
      complete: () => {
        navigationPending = false
      }
    })
    return
  }

  resetToPage('index')
}

/**
 * 门店坐标来源，按优先级回落：
 *   1. /api/store 返回的经纬度（管理端可改，DB 为唯一权威）
 *   2. config/constants.js 的 STORE 本地常量（接口失败 / 未加载时的兜底）
 *   3. 两份都非法 → null，调用方走「地址弹窗 + 复制」降级
 * 校验在 normalizeCoordinate 内完成（范围、非 (0,0)、非有限数字）。
 */
export function resolveStoreCoords(store = {}) {
  return (
    normalizeCoordinate(store.latitude, store.longitude) ||
    normalizeCoordinate(STORE.latitude, STORE.longitude)
  )
}

function storeNameOf(store = {}) {
  return store.name || STORE.name
}

function storeAddressOf(store = {}) {
  return store.address || STORE.address
}

/**
 * 降级路径：坐标缺失或地图拉不起来时，不静默失败。
 * 弹窗保留完整地址，并把「复制地址」作为主操作——用户复制后可直接在地图 App 里搜，
 * 比只显示一行错误提示有用。复制内容用 shortAddress（可被地图 App 直接解析的写法）。
 */
function fallbackToAddress(store = {}) {
  uni.showModal({
    title: storeNameOf(store),
    content: `${storeAddressOf(store)}\n\n暂时无法打开地图，可复制地址后在地图 App 中搜索。`,
    confirmText: '复制地址',
    cancelText: '知道了',
    success: (res) => {
      if (!res.confirm) return
      uni.setClipboardData({
        data: STORE.shortAddress || storeAddressOf(store)
      })
    }
  })
}

/**
 * 打开地图页面并标记门店位置（点店铺行的默认行为）。
 *
 * 目标平台：微信小程序内置地图页 wx.openLocation（uni 封装为 uni.openLocation）——
 * 一次性给出「定位到门店 + 门店水滴标记 + 店名/地址卡片 + 导航按钮」，
 * 不需要新增页面、不需要腾讯地图 key、不需要拿用户定位权限。
 * H5 端由 uni 转成高德网页 URI（uri.amap.com）在新窗口打开，语义一致。
 *
 * @param {object} store 可选，{ name, address, latitude, longitude }；不传则只用本地常量
 */
export function openStoreMap(store = {}) {
  const coords = resolveStoreCoords(store)
  const name = storeNameOf(store)
  const address = storeAddressOf(store)

  if (!coords) {
    fallbackToAddress(store)
    return
  }

  uni.openLocation({
    latitude: coords.latitude,
    longitude: coords.longitude,
    name,
    address,
    // 18 级 ≈ 街道级，能看清店面所在楼栋；再高会丢失周边参照
    scale: 18,
    fail: () => fallbackToAddress(store)
  })
}

// 订单面板「导航到店」沿用旧入口名，行为统一收敛到 openStoreMap
export function navigateToStore(store = {}) {
  openStoreMap(store)
}

export function showStoreInfo() {
  uni.showModal({
    title: STORE.name,
    content: `${STORE.address}\n营业时间 ${STORE.hours}\n到店自提，请向咖啡师出示取餐号。`,
    showCancel: false
  })
}
