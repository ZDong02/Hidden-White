/**
 * 购物车金额计算工具。
 *
 * <p>只保留点单端真正使用的四个函数：两个计算、一个计数、一个展示格式化。
 * 改造前这里还有 formatPrice / formatPriceClean / applyDiscount / calculateFinalPrice
 * 四个导出，全库零调用（仅被自身单测覆盖）——属于「测试自己养着自己」的死代码，
 * 已随本轮清理移除；价格展示由模板里的 `¥${金额}` 承担。
 * 现在的 formatMoney 只做一件事：把浮点尾数抹掉（86.69999999999999 → 86.7）。
 */

// 金额按「分」四舍五入：单价是 DECIMAL(10,2)，直接相乘/相加会出现
// 28.9 × 3 = 86.69999999999999 这类二进制尾数，展示到界面上就是错价格。
// 结算金额由服务端用 BigDecimal 计算，这里只负责「显示出来的数与服务端一致」。
function roundMoney(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return 0
  return Math.round((num + Number.EPSILON) * 100) / 100
}

// 计算购物车项目小计
export function calculateItemTotal(item) {
  if (!item) return 0
  const unitPrice = Number(item.unitPrice) || 0
  const quantity = Number(item.quantity) || 0
  return roundMoney(unitPrice * quantity)
}

/** 展示用金额：去掉浮点尾数（86.69999999999999 → 86.7），不做货币符号。 */
export function formatMoney(value) {
  return String(roundMoney(value))
}

// 计算购物车总金额
export function calculateCartTotal(cart) {
  if (!Array.isArray(cart)) return 0
  return roundMoney(cart.reduce((sum, item) => sum + calculateItemTotal(item), 0))
}

// 计算购物车商品总数
export function calculateCartCount(cart) {
  if (!Array.isArray(cart)) return 0
  return cart.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0)
}
