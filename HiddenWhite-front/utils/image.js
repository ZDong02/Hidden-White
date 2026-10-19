/**
 * 图片资源管理工具
 * 统一处理图片路径与占位图回退
 */

import { IMAGES } from '../config/constants'

/**
 * 获取咖啡图片
 */
export function getCoffeeImage(type) {
  return IMAGES.coffee[type] || IMAGES.coffee.latte
}

/**
 * 获取商品详情图（优先详情图，回退到列表图）
 */
export function getProductDetailImage(product) {
  return product?.detailImage || product?.image || getCoffeeImage('latteDetail')
}
