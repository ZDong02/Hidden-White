/**
 * 价格与校验工具测试。
 *
 * 用 Node 内置测试运行器（node:test），与管理端保持一致，
 * 不引入 vitest / jest —— 这两个模块都是纯函数，无 uni 运行时依赖。
 *
 * 运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatPrice,
  formatPriceClean,
  calculateItemTotal,
  calculateCartTotal,
  calculateCartCount,
  applyDiscount,
  calculateFinalPrice
} from '../utils/price.js'
import {
  validatePhone,
  validatePassword,
  validateDisplayName,
  validateCart,
  validateQuantity,
  validateRequired,
  validateLength
} from '../utils/validators.js'

// ---- 价格格式化 ----

test('formatPrice 固定两位小数，非法输入回退 0.00', () => {
  assert.equal(formatPrice(32), '32.00')
  assert.equal(formatPrice(32.5), '32.50')
  assert.equal(formatPrice('45.9'), '45.90')
  assert.equal(formatPrice(null), '0.00')
  assert.equal(formatPrice('abc'), '0.00')
  assert.equal(formatPrice(NaN), '0.00')
})

test('formatPriceClean 整数不显示小数', () => {
  assert.equal(formatPriceClean(32), '32')
  assert.equal(formatPriceClean(32.5), '32.50')
  assert.equal(formatPriceClean('abc'), '0')
})

// ---- 购物车计算 ----

test('calculateItemTotal 计算单行小计，缺字段按 0 处理', () => {
  assert.equal(calculateItemTotal({ unitPrice: 32, quantity: 2 }), 64)
  assert.equal(calculateItemTotal({ unitPrice: 32 }), 0)
  assert.equal(calculateItemTotal(null), 0)
})

test('calculateCartTotal 与 calculateCartCount 汇总购物车', () => {
  const cart = [
    { unitPrice: 32, quantity: 2 },
    { unitPrice: 35, quantity: 1 }
  ]
  assert.equal(calculateCartTotal(cart), 99)
  assert.equal(calculateCartCount(cart), 3)
})

test('购物车计算对非法入参返回 0 而非抛错', () => {
  assert.equal(calculateCartTotal(null), 0)
  assert.equal(calculateCartTotal('not-an-array'), 0)
  assert.equal(calculateCartCount(undefined), 0)
})

// ---- 折扣与最终价 ----

test('applyDiscount 不会把总价压成负数', () => {
  assert.equal(applyDiscount(100, 6), 94)
  // 折扣大于小计时应归零，不能出现负数金额
  assert.equal(applyDiscount(10, 50), 0)
  assert.equal(applyDiscount(0, 6), 0)
})

test('calculateFinalPrice 计算含包装费的实付金额', () => {
  // 首单场景：32 - 6 = 26
  assert.equal(calculateFinalPrice(32, 6, 0), 26)
  assert.equal(calculateFinalPrice(32, 0, 2), 34)
  assert.equal(calculateFinalPrice(10, 50, 0), 0, '折扣超过小计时归零')
})

// ---- 表单校验 ----

test('validatePhone 接受数字与国际格式，拒绝非法字符', () => {
  assert.equal(validatePhone('13800138000').valid, true)
  assert.equal(validatePhone('+86 138 0013 8000').valid, true)
  assert.equal(validatePhone('12345').valid, false, '少于 6 位应拒绝')
  assert.equal(validatePhone('138abc00138').valid, false, '含字母应拒绝')
  assert.equal(validatePhone('').valid, false)
  // 前后空格应被裁剪
  assert.equal(validatePhone('  13800138000  ').value, '13800138000')
})

test('validatePassword 限制 6 到 30 位', () => {
  assert.equal(validatePassword('123456').valid, true)
  assert.equal(validatePassword('12345').valid, false)
  assert.equal(validatePassword('x'.repeat(31)).valid, false)
})

test('validateDisplayName 限制 30 个字符', () => {
  assert.equal(validateDisplayName('小隐').valid, true)
  assert.equal(validateDisplayName('x'.repeat(31)).valid, false)
  assert.equal(validateDisplayName('  ').valid, false)
})

test('validateCart 逐项校验数据完整性', () => {
  assert.equal(validateCart([{ id: 'latte', name: '拿铁', unitPrice: 32, quantity: 1 }]).valid, true)

  assert.equal(validateCart([]).valid, false, '空购物车应拒绝')
  assert.equal(validateCart(null).valid, false)
  assert.equal(validateCart([{ name: '拿铁', unitPrice: 32, quantity: 1 }]).valid, false, '缺 id 应拒绝')
  assert.equal(validateCart([{ id: 'latte', unitPrice: 32, quantity: 1 }]).valid, false, '缺 name 应拒绝')
  assert.equal(validateCart([{ id: 'latte', name: '拿铁', unitPrice: 0, quantity: 1 }]).valid, false, '价格为 0 应拒绝')
  assert.equal(validateCart([{ id: 'latte', name: '拿铁', unitPrice: 32, quantity: 1.5 }]).valid, false, '非整数数量应拒绝')
})

test('validateCart 的错误信息带项序号', () => {
  const result = validateCart([
    { id: 'latte', name: '拿铁', unitPrice: 32, quantity: 1 },
    { id: 'americano', name: '美式', unitPrice: 28, quantity: 0 }
  ])
  assert.equal(result.valid, false)
  assert.match(result.message, /第 2 项/)
})

test('validateQuantity 限制整数与上下界', () => {
  assert.equal(validateQuantity(5).valid, true)
  assert.equal(validateQuantity(5, 1, 3).valid, false)
  assert.equal(validateQuantity(0).valid, false)
  assert.equal(validateQuantity(1.5).valid, false)
  assert.equal(validateQuantity('3').valid, true, '数字字符串应被接受')
})

test('通用校验工具按字段名返回可读提示', () => {
  assert.equal(validateRequired('  ', '昵称').message, '昵称不能为空')
  assert.equal(validateRequired(' 值 ').value, '值')
  assert.equal(validateLength('ab', 3, 5, '密码').message, '密码至少需要 3 个字符')
  assert.equal(validateLength('abcdef', 3, 5, '密码').message, '密码不能超过 5 个字符')
})
