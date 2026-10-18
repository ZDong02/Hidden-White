import { reactive, computed } from 'vue'
import { productData } from '../data/products'
import { BUSINESS } from '../config/constants'
import { formatDateTime, calculateReadyTime } from '../utils/datetime'
import { validateCart } from '../utils/validation'
import { BACKEND_ENABLED, requestApi } from '../config/backend'
import { currentUser, isLoggedIn } from './auth'
import { getStorage, setStorageDebounced, setStorageImmediate } from '../utils/storage'
import { calculateCartTotal, calculateCartCount } from '../utils/price'

const CART_KEY = 'hidden-white-cart'
const ORDER_KEY = 'hidden-white-order'
const ORDER_HISTORY_KEY = 'hidden-white-order-history'
const MAX_ORDER_HISTORY = 100

// 初始化状态 - 使用新的 storage 工具
const storedCart = getStorage(CART_KEY, [])
const cart = Array.isArray(storedCart) ? storedCart : []
const order = getStorage(ORDER_KEY, null)
const orderHistory = getStorage(ORDER_HISTORY_KEY, [])
const apiProducts = reactive([...productData])

export const shop = reactive({
  cart: Array.isArray(cart) ? cart : [],
  order: order,
  orderHistory: Array.isArray(orderHistory) ? orderHistory.slice(0, MAX_ORDER_HISTORY) : [],
  note: ''
})

export const products = apiProducts

export function mapBackendProduct(product) {
  const price = Number(product.price)
  return {
    ...product,
    id: String(product.id),
    price: Number.isFinite(price) ? price : 0,
    category: Number(product.category) || 0,
    hasMilk: product.hasMilk === true || product.hasMilk === 1,
    image: product.image || '',
    detailImage: product.detailImage || product.image || ''
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
  if (!BACKEND_ENABLED) return apiProducts

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
  open: true,
  loaded: false
})

// 门店状态是时效敏感的轻量数据（管理端切换打烊/营业需尽快可见），TTL 远短于商品目录
const STORE_TTL = 5000
let storeLoadedAt = 0
let storePending = null

export async function loadStoreInfo(options = {}) {
  if (!BACKEND_ENABLED) return storeInfo
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

export function saveCartNow() {
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

export function quickAdd(product) {
  const item = {
    ...product,
    size: '标准杯 240 毫升',
    temperature: '热',
    milk: product.hasMilk ? '全脂奶' : '无奶',
    quantity: 1,
    unitPrice: product.price
  }
  addItem(item)
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

export function mapBackendOrder(order) {
  const created = new Date(order.createdAt)
  const ready = new Date(order.readyAt)
  const pad = (value) => String(value).padStart(2, '0')
  const productIndex = new Map(apiProducts.map((product) => [product.id, product]))

  return {
    ...order,
    items: order.items.map((item) => {
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
    created: `${created.getFullYear()}年${created.getMonth() + 1}月${created.getDate()}日 ${pad(created.getHours())}:${pad(created.getMinutes())}`,
    ready: `${pad(ready.getHours())}:${pad(ready.getMinutes())}`,
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
    shop.order = null
    shop.orderHistory = []
    return { current: null, history: [] }
  }
  if (!BACKEND_ENABLED) return { current: shop.order, history: shop.orderHistory }

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

export async function quoteCart() {
  if (!isLoggedIn.value) throw new Error('请先登录后再下单')
  validateCart(shop.cart)
  return requestApi('/cart/quote', { method: 'POST', data: toBackendPayload() })
}

export async function createOrder() {
  try {
    if (!isLoggedIn.value) {
      throw new Error('请先登录后再下单')
    }
    if (shop.order) {
      throw new Error('当前有订单待支付或待取餐，请先处理或等待订单超时')
    }

    validateCart(shop.cart)

    if (BACKEND_ENABLED) {
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

// 本地模式（后端不可用时的降级路径）
const now = new Date()
const formatted = formatDateTime(now)
const readyTime = calculateReadyTime(BUSINESS.preparationTime)
// 与后端 PickupNumber 规则保持一致：当日 1~999 循环、固定 3 位。
// 原实现是Math.random()，随机数必然可能重复——顾客报同一个号取到两杯咖啡，
// 是门店真实会出事故的缺陷。改为从当日历史订单里推出的最大号继续递增。
const todayKey = formatted.orderNumber
const usedToday = shop.orderHistory.filter((row) => String(row.number || '').startsWith(todayKey))
const lastUsed = usedToday.reduce((max, row) => {
  const raw = String(row.pickup || '').replace(/^0+/, '')
  const value = Number(raw || 0)
  return Number.isFinite(value) && value > max ? value : max
}, 0)
const nextPickup = lastUsed >= 999 ? 1 : lastUsed + 1
const pickupNumber = String(nextPickup).padStart(3, '0')

    shop.order = {
      items: JSON.parse(JSON.stringify(shop.cart)),
      subtotal: subtotal.value,
      total: total.value,
      count: count.value,
      pickup: pickupNumber,
      number: `${formatted.orderNumber}${pickupNumber}`,
      created: formatted.datetime,
      ready: readyTime,
      preparationTime: BUSINESS.preparationTime,
      status: 'in-progress',
      note: shop.note
    }

    setStorageImmediate(ORDER_KEY, shop.order)
    setStorageImmediate(ORDER_HISTORY_KEY, shop.orderHistory)
    shop.cart = []
    shop.note = ''
    saveCartNow()
    return shop.order
  } catch (error) {
    console.error('创建订单失败:', error)
    throw error
  }
}

export async function completeOrder() {
  if (!shop.order) return

  // 未支付不允许标记取餐——与后端状态机同一条不变式，防止漏收
  if (shop.order.status === 'pending-payment') {
    throw new Error('订单尚未支付完成，无法标记取餐')
  }

  if (BACKEND_ENABLED && shop.order.id) {
    const backendOrder = await requestApi(`/orders/${shop.order.id}/complete`, {
      method: 'POST'
    })
    const completedOrder = mapBackendOrder(backendOrder)
    shop.orderHistory.unshift(completedOrder)
    shop.orderHistory.splice(MAX_ORDER_HISTORY)
    shop.order = null
    setStorageImmediate(ORDER_KEY, null)
    setStorageImmediate(ORDER_HISTORY_KEY, shop.orderHistory)
    return
  }

  const completedAt = formatDateTime(new Date())
  const completedOrder = {
    ...JSON.parse(JSON.stringify(shop.order)),
    status: 'completed',
    completedAt: completedAt.datetime
  }
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

  if (BACKEND_ENABLED && shop.order.id) {
    const backendOrder = await requestApi(`/orders/${shop.order.id}/cancel`, {
      method: 'POST'
    })
    const cancelledOrder = mapBackendOrder(backendOrder)
    shop.orderHistory.unshift(cancelledOrder)
    shop.orderHistory.splice(MAX_ORDER_HISTORY)
    shop.order = null
    setStorageImmediate(ORDER_KEY, null)
    setStorageImmediate(ORDER_HISTORY_KEY, shop.orderHistory)
    return
  }

  const cancelledOrder = {
    ...JSON.parse(JSON.stringify(shop.order)),
    status: 'cancelled'
  }
  shop.orderHistory.unshift(cancelledOrder)
  shop.orderHistory.splice(MAX_ORDER_HISTORY)
  shop.order = null
  setStorageImmediate(ORDER_KEY, null)
  setStorageImmediate(ORDER_HISTORY_KEY, shop.orderHistory)
}

export function clearCart() {
  shop.cart = []
  saveCartNow()
}
