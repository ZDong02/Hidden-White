// 验证购物车数据
export function validateCart(cart) {
  if (!cart || !Array.isArray(cart)) {
    throw new Error('购物车数据格式错误')
  }
  
  if (cart.length === 0) {
    throw new Error('购物车不能为空')
  }
  
  cart.forEach((item, index) => {
    if (!item.id || !item.name || !Number.isFinite(item.unitPrice) || !Number.isInteger(item.quantity)) {
      throw new Error(`购物车第 ${index + 1} 项数据不完整`)
    }
    
    if (item.quantity <= 0) {
      throw new Error(`购物车第 ${index + 1} 项数量必须大于 0`)
    }
    
    if (item.unitPrice <= 0) {
      throw new Error(`购物车第 ${index + 1} 项价格必须大于 0`)
    }
  })
  
  return true
}
