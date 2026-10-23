/**
 * 购物车金额计算工具。
 *
 * <p>只保留点单端真正使用的三个函数。改造前这里还有 formatPrice /
 * formatPriceClean / applyDiscount / calculateFinalPrice 四个导出，
 * 全库零调用（仅被自身单测覆盖）——属于「测试自己养着自己」的死代码，
 * 已随本轮清理移除；价格展示由模板里的 `¥${金额}` 承担。
 */

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
