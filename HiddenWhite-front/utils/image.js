/**
 * 图片资源管理工具
 * 统一处理图片路径、懒加载、占位符等
 */

import { IMAGES } from '../config/constants'

/**
 * 获取静态资源路径
 */
export function getStaticPath(path) {
  if (!path) return ''
  // 如果已经是完整路径，直接返回
  if (path.startsWith('http://') || path.startsWith('https://') || path.startsWith('/')) {
    return path
  }
  // 否则添加 /static/ 前缀
  return `/static/${path}`
}

/**
 * 获取咖啡图片
 */
export function getCoffeeImage(type) {
  return IMAGES.coffee[type] || IMAGES.coffee.latte
}

/**
 * 获取图标
 */
export function getIcon(name) {
  return IMAGES.icons[name] || ''
}

/**
 * 图片加载失败处理
 */
export function handleImageError(event, fallbackUrl = '') {
  if (fallbackUrl) {
    event.target.src = fallbackUrl
  }
}

/**
 * 获取商品图片（带默认值）
 */
export function getProductImage(product) {
  return product?.image || product?.detailImage || getCoffeeImage('latte')
}

/**
 * 获取商品详情图（优先详情图，回退到列表图）
 */
export function getProductDetailImage(product) {
  return product?.detailImage || product?.image || getCoffeeImage('latteDetail')
}
