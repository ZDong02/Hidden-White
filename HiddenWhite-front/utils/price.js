/**
 * 价格计算与格式化工具
 */

// 格式化价格显示（保留两位小数）
export function formatPrice(price) {
  const num = Number(price)
  return Number.isFinite(num) ? num.toFixed(2) : '0.00'
}

// 格式化价格显示（整数不显示小数）
export function formatPriceClean(price) {
  const num = Number(price)
  if (!Number.isFinite(num)) return '0'
  return num % 1 === 0 ? String(num) : num.toFixed(2)
}

// 计算购物车项目小计
export function calculateItemTotal(item) {
  if (!item) return 0
  const unitPrice = Number(item.unitPrice) || 0
  const quantity = Number(item.quantity) || 0
  return unitPrice * quantity
}

// 计算购物车总金额
export function calculateCartTotal(cart) {
  if (!Array.isArray(cart)) return 0
  return cart.reduce((sum, item) => sum + calculateItemTotal(item), 0)
}

// 计算购物车商品总数
export function calculateCartCount(cart) {
  if (!Array.isArray(cart)) return 0
  return cart.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0)
}

// 应用折扣
export function applyDiscount(subtotal, discountAmount) {
  const total = Number(subtotal) || 0
  const discount = Number(discountAmount) || 0
  return Math.max(0, total - discount)
}

// 计算最终价格（含包装费）
export function calculateFinalPrice(subtotal, discount, packagingFee) {
  const sub = Number(subtotal) || 0
  const disc = Number(discount) || 0
  const pack = Number(packagingFee) || 0
  return Math.max(0, sub - disc + pack)
}
