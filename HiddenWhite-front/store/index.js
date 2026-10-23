import { reactive, computed } from 'vue'
import { BUSINESS } from '../config/constants'
import { formatDateTime, formatTime } from '../utils/datetime'
import { validateCart } from '../utils/validators'
import { requestApi } from '../config/backend'
import { resolveImageUrl } from '../utils/image'
import { currentUser, isLoggedIn } from './auth'
import { getStorage, setStorageDebounced, setStorageImmediate } from '../utils/storage'
import { calculateCartTotal, calculateCartCount } from '../utils/price'
import { normalizeCoordinate } from '../utils/location'

const CART_KEY = 'hidden-white-cart'
const ORDER_KEY = 'hidden-white-order'
const ORDER_HISTORY_KEY = 'hidden-white-order-history'
const MAX_ORDER_HISTORY = 100

// 初始化状态 - 使用新的 storage 工具
const storedCart = getStorage(CART_KEY, [])
const cart = Array.isArray(storedCart) ? storedCart : []
const order = getStorage(ORDER_KEY, null)
const orderHistory = getStorage(ORDER_HISTORY_KEY, [])
// 商品目录一律以 /api/products 为准：初始为空，首屏由 loadProducts 填充。
// 早期这里用本地 mock 商品打底，后果是后端连不上时界面照样能「下单」，
// 而门店端根本收不到这笔单——比空列表更糟，故不再保留本地兜底数据。
const apiProducts = reactive([])

export const shop = reactive({
  cart: Array.isArray(cart) ? cart : [],
  order: order,
  orderHistory: Array.isArray(orderHistory) ? orderHistory.slice(0, MAX_ORDER_HISTORY) : [],
  note: ''
})

export const products = apiProducts

function mapBackendProduct(product) {
  const price = Number(product.price)
  return {
    ...product,
    id: String(product.id),
    price: Number.isFinite(price) ? price : 0,
    category: Number(product.category) || 0,
    hasMilk: product.hasMilk === true || product.hasMilk === 1,
    // 图片在数据层统一解析成可加载地址：
    // 管理端上传的图片是 /uploads/... 相对路径，小程序无法直接加载。
    image: resolveImageUrl(product.image),
    detailImage: resolveImageUrl(product.detailImage || product.image)
  }
}

function syncProducts(rows) {
  const next = rows.map(mapBackendProduct)
  const current = apiProducts
  if (current.length === next.length) {
    const unchanged = current.every((item, index) => {
      const target = next[index]
      return item.id === target.id &&
        item.name === target.name &&
        item.subtitle === target.subtitle &&
        item.price === target.price &&
        item.image === target.image &&
        item.detailImage === target.detailImage &&
        item.category === target.category &&
        item.hasMilk === target.hasMilk
    })
    if (unchanged) return current
  }
  current.splice(0, current.length, ...next)
  return current
}

const CATALOG_TTL = 30000
let catalogLoadedAt = 0
let catalogPending = null

export function invalidateCatalog() {
  catalogLoadedAt = 0
}

export async function loadProducts(options = {}) {
  const now = Date.now()
  if (!options.force && catalogLoadedAt && now - catalogLoadedAt < CATALOG_TTL) {
    return apiProducts
  }
  if (catalogPending) return catalogPending

  catalogPending = requestApi('/products')
    .then((rows) => {
      const list = Array.isArray(rows) ? rows : []
      const result = syncProducts(list)
      catalogLoadedAt = Date.now()
      return result
    })
    .finally(() => {
      catalogPending = null
    })

  return catalogPending
}

export const storeInfo = reactive({
  name: '',
  address: '',
  hours: '',
  distance: '',
  // 门店坐标（GCJ-02）：点店铺行跳地图时用。null 表示接口没给或给了非法值，
  // 由 utils/navigation 的 resolveStoreCoords 回落 config/constants.js 的常量
  latitude: null,
  longitude: null,
  open: true,
  loaded: false
})

// 门店状态是时效敏感的轻量数据（管理端切换打烊/营业需尽快可见），TTL 远短于商品目录
const STORE_TTL = 5000
let storeLoadedAt = 0
let storePending = null

export async function loadStoreInfo(options = {}) {
  const now = Date.now()
  if (!options.force && storeInfo.loaded && now - storeLoadedAt < STORE_TTL) {
    return storeInfo
  }
  if (storePending) return storePending

  storePending = requestApi('/store')
    .then((info) => {
      storeInfo.name = info.name || ''
      storeInfo.address = info.shortAddress || info.address || ''
      storeInfo.hours = info.hours || ''
      storeInfo.distance = info.distance || ''
      // 后端 BigDecimal 可能序列化成数字或字符串，交给 normalizeCoordinate 统一收敛；
      // 拿不准就直接存 null，让调用方回落常量，别把坏坐标带给地图 API
      const coords = normalizeCoordinate(info.latitude, info.longitude)
      storeInfo.latitude = coords ? coords.latitude : null
      storeInfo.longitude = coords ? coords.longitude : null
      storeInfo.open = info.open !== false
      storeInfo.loaded = true
      storeLoadedAt = Date.now()
      return storeInfo
    })
    .finally(() => {
      storePending = null
    })

  return storePending
}

// 计算属性 - 使用新的 price 工具
export const count = computed(() => calculateCartCount(shop.cart))
export const subtotal = computed(() => calculateCartTotal(shop.cart))

export const discount = computed(() => {
  return shop.order || shop.orderHistory.length
    ? 0
    : Math.min(BUSINESS.firstOrderDiscount, subtotal.value)
})

export const total = computed(() => {
  return subtotal.value - discount.value + BUSINESS.packagingFee
})

// 操作方法 - 使用新的 storage 工具
export function saveCart() {
  setStorageDebounced(CART_KEY, shop.cart)
}

function saveCartNow() {
  setStorageImmediate(CART_KEY, shop.cart)
}

export function addItem(item, replace = false) {
  const existingItem = shop.cart.find(
    row =>
      row.id === item.id &&
      row.size === item.size &&
      row.temperature === item.temperature &&
      row.milk === item.milk
  )

  if (existingItem) {
    existingItem.quantity = replace ? item.quantity : existingItem.quantity + item.quantity
  } else {
    shop.cart.push({ ...item })
  }

  saveCart()
}

function toBackendPayload() {
  return {
    // 仅 /cart/quote 报价接口使用（用于判断首单减免）。
    // 真正下单时服务端会以 token 中的会员 ID 为准，忽略此字段。
    memberId: currentUser.value.id,
    items: shop.cart.map((item) => ({
      productId: item.id,
      size: item.size,
      temperature: item.temperature,
      milk: item.milk,
      quantity: item.quantity
    })),
    note: shop.note || ''
  }
}

function mapBackendOrder(order) {
  const created = new Date(order.createdAt)
  const ready = new Date(order.readyAt)
  const productIndex = new Map(apiProducts.map((product) => [product.id, product]))

  return {
    ...order,
    items: (order.items || []).map((item) => {
      const product = productIndex.get(item.productId)
      return {
        ...item,
        id: item.productId,
        image: product?.image || '',
        detailImage: product?.detailImage || '',
        unitPrice: Number(item.unitPrice),
        quantity: item.quantity
      }
    }),
    subtotal: Number(order.subtotal),
    total: Number(order.total),
    count: order.count,
    created: formatDateTime(created).datetime,
    ready: formatTime(ready),
    preparationTime: Math.max(0, Math.round((ready - created) / 60000)),
    // 与后端 OrderStatus 五态对应：
    // CREATED / PENDING_PAYMENT → pending-payment（待支付）
    // PAID → in-progress（制作中）
    // COMPLETED → completed，CANCELLED → cancelled
    status: order.status === 'COMPLETED' ? 'completed'
      : order.status === 'CANCELLED' ? 'cancelled'
        : order.status === 'PAID' ? 'in-progress'
          : 'pending-payment'
  }
}

export async function refreshOrders() {
  if (!isLoggedIn.value) {
    clearOrderState()
    return { current: null, history: [] }
  }

  const rows = await requestApi('/orders')
  const mapped = rows.map(mapBackendOrder)
  // 待支付与制作中都属于「进行中」，都占用下单名额；已取消的不再占用
  shop.order = mapped.find((row) => row.status === 'in-progress' || row.status === 'pending-payment') || null
  shop.orderHistory = mapped
    .filter((row) => row.status === 'completed' || row.status === 'cancelled')
    .slice(0, MAX_ORDER_HISTORY)

  setStorageDebounced(ORDER_KEY, shop.order)
  setStorageDebounced(ORDER_HISTORY_KEY, shop.orderHistory)

  return { current: shop.order, history: shop.orderHistory }
}

/** 下单前校验购物车：结构化结果转成抛错，保持对外错误语义不变。 */
function assertCart() {
  const result = validateCart(shop.cart)
  if (!result.valid) throw new Error(result.message)
}

/**
 * 清空当前订单与历史，并同步清掉本地缓存。
 *
 * <p><b>为什么必须清缓存</b>：订单状态是「谁登录就属于谁」的数据，
 * 只清内存会让上一账号的订单留在 storage 里，下次冷启动又被读回来。
 * 后果不只是看到别人的取餐号：首单减免判定用的是 orderHistory 是否为空，
 * 于是新注册会员也会被判成「已下过单」，白丢 6 元优惠。
 */
export function clearOrderState() {
  shop.order = null
  shop.orderHistory = []
  setStorageImmediate(ORDER_KEY, null)
  setStorageImmediate(ORDER_HISTORY_KEY, [])
}

export async function quoteCart() {
  if (!isLoggedIn.value) throw new Error('请先登录后再下单')
  assertCart()
  return requestApi('/cart/quote', { method: 'POST', data: toBackendPayload() })
}

export async function createOrder() {
  if (!isLoggedIn.value) {
    throw new Error('请先登录后再下单')
  }
  if (shop.order) {
    throw new Error('当前有订单待支付或待取餐，请先处理或等待订单超时')
  }

  assertCart()

  const backendOrder = await requestApi('/orders', {
    method: 'POST',
    data: toBackendPayload()
  })
  shop.order = mapBackendOrder(backendOrder)
  setStorageImmediate(ORDER_KEY, shop.order)
  setStorageImmediate(ORDER_HISTORY_KEY, shop.orderHistory)
  shop.cart = []
  shop.note = ''
  saveCartNow()
  return shop.order
}

/**
 * 完成取餐。
 *
 * <p>状态由服务端条件更新（PAID → COMPLETED）决定，前端不再自行「假装完成」：
 * 订单以哪一端为准，取餐记录就必须来自同一端，否则门店账面与顾客看到的不一致。
 */
export async function completeOrder() {
  if (!shop.order) return

  // 未支付不允许标记取餐——与后端状态机同一条不变式，防止漏收
  if (shop.order.status === 'pending-payment') {
    throw new Error('订单尚未支付完成，无法标记取餐')
  }

  const backendOrder = await requestApi(`/orders/${shop.order.id}/complete`, { method: 'POST' })
  const completedOrder = mapBackendOrder(backendOrder)
  // 服务端返回已完成态时，先按 id 去重再入历史，避免切 Tab 重复确认push 出两条
  shop.orderHistory = shop.orderHistory.filter((row) => row.id !== completedOrder.id)
  shop.orderHistory.unshift(completedOrder)
  shop.orderHistory.splice(MAX_ORDER_HISTORY)
  shop.order = null
  setStorageImmediate(ORDER_KEY, null)
  setStorageImmediate(ORDER_HISTORY_KEY, shop.orderHistory)
}

/**
 * 主动取消当前订单。
 *
 * 存在的意义：下单后不想要了、或支付未接入时放弃，
 * 不必被动等待服务端 35 分钟超时才能重新下单。
 * 取消后立即释放「同一会员仅一张进行中订单」的名额。
 */
export async function cancelOrder() {
  if (!shop.order) return

  // 与后端同一条不变式：已付款不能由顾客直接取消，否则钱收了订单没了
  if (shop.order.status === 'in-progress') {
    throw new Error('订单已支付，请联系门店协助取消与退款')
  }

  const backendOrder = await requestApi(`/orders/${shop.order.id}/cancel`, { method: 'POST' })
  const cancelledOrder = mapBackendOrder(backendOrder)
  shop.orderHistory = shop.orderHistory.filter((row) => row.id !== cancelledOrder.id)
  shop.orderHistory.unshift(cancelledOrder)
  shop.orderHistory.splice(MAX_ORDER_HISTORY)
  shop.order = null
  setStorageImmediate(ORDER_KEY, null)
  setStorageImmediate(ORDER_HISTORY_KEY, shop.orderHistory)
}
