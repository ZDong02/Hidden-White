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
  calculateItemTotal,
  calculateCartTotal,
  calculateCartCount
} from '../utils/price.js'
import {
  validatePhone,
  validatePassword,
  validateDisplayName,
  validateCart
} from '../utils/validators.js'

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
