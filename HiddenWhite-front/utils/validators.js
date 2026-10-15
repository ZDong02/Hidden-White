/**
 * 增强的验证工具
 * 统一的表单验证逻辑
 */

// 验证手机号
export function validatePhone(phone) {
  const trimmed = String(phone || '').trim()
  if (!trimmed) {
    return { valid: false, message: '请输入手机号' }
  }
  // 支持国际格式：数字、加号、括号、空格、短横线
  if (!/^[0-9+() -]{6,20}$/.test(trimmed)) {
    return { valid: false, message: '请输入有效的手机号' }
  }
  return { valid: true, value: trimmed }
}

// 验证密码
export function validatePassword(password) {
  const value = String(password || '')
  if (!value) {
    return { valid: false, message: '请输入密码' }
  }
  if (value.length < 6) {
    return { valid: false, message: '密码至少需要 6 位' }
  }
  if (value.length > 30) {
    return { valid: false, message: '密码不能超过 30 位' }
  }
  return { valid: true, value }
}

// 验证昵称
export function validateDisplayName(name) {
  const trimmed = String(name || '').trim()
  if (!trimmed) {
    return { valid: false, message: '请输入昵称' }
  }
  if (trimmed.length > 30) {
    return { valid: false, message: '昵称不能超过 30 个字符' }
  }
  return { valid: true, value: trimmed }
}

// 验证购物车
export function validateCart(cart) {
  if (!cart || !Array.isArray(cart)) {
    return { valid: false, message: '购物车数据格式错误' }
  }

  if (cart.length === 0) {
    return { valid: false, message: '购物车不能为空' }
  }

  for (let i = 0; i < cart.length; i++) {
    const item = cart[i]
    if (!item.id || !item.name) {
      return { valid: false, message: `购物车第 ${i + 1} 项数据不完整` }
    }

    if (!Number.isFinite(item.unitPrice) || item.unitPrice <= 0) {
      return { valid: false, message: `购物车第 ${i + 1} 项价格无效` }
    }

    if (!Number.isInteger(item.quantity) || item.quantity <= 0) {
      return { valid: false, message: `购物车第 ${i + 1} 项数量无效` }
    }
  }

  return { valid: true }
}

// 验证商品数量
export function validateQuantity(quantity, min = 1, max = 999) {
  const num = Number(quantity)
  if (!Number.isInteger(num)) {
    return { valid: false, message: '数量必须是整数' }
  }
  if (num < min) {
    return { valid: false, message: `数量不能小于 ${min}` }
  }
  if (num > max) {
    return { valid: false, message: `数量不能大于 ${max}` }
  }
  return { valid: true, value: num }
}

// 通用的非空验证
export function validateRequired(value, fieldName = '此项') {
  const trimmed = String(value || '').trim()
  if (!trimmed) {
    return { valid: false, message: `${fieldName}不能为空` }
  }
  return { valid: true, value: trimmed }
}

// 验证长度范围
export function validateLength(value, min, max, fieldName = '此项') {
  const str = String(value || '')
  if (str.length < min) {
    return { valid: false, message: `${fieldName}至少需要 ${min} 个字符` }
  }
  if (str.length > max) {
    return { valid: false, message: `${fieldName}不能超过 ${max} 个字符` }
  }
  return { valid: true, value: str }
}

// 兼容旧 API（抛出异常）
export { validateCart as default } from './validation'
