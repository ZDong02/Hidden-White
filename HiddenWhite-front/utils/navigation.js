import { STORE } from '../config/constants'

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

// Navigate to the configured store location.
export function navigateToStore() {
  uni.openLocation({
    latitude: STORE.latitude,
    longitude: STORE.longitude,
    name: STORE.name,
    address: STORE.address,
    fail: () => {
      uni.showModal({
        title: '门店地址',
        content: STORE.address,
        confirmText: '复制地址',
        success: (res) => {
          if (res.confirm) {
            uni.setClipboardData({ data: STORE.shortAddress })
          }
        }
      })
    }
  })
}

export function showStoreInfo() {
  uni.showModal({
    title: STORE.name,
    content: `${STORE.address}\n营业时间 ${STORE.hours}\n到店自提，请向咖啡师出示取餐号。`,
    showCancel: false
  })
}
