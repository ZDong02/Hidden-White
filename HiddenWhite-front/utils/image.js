/**
 * 图片资源管理工具
 * 统一处理图片路径与占位图回退
 */

import { IMAGES } from '../config/constants'
import { getApiOrigin } from '../config/backend'

/**
 * 把后端返回的图片路径解析为可直接加载的地址。
 *
 * 商品图有两种来源：
 * 1. 管理端上传的图片 —— 存的是 /uploads/xxx.jpg 相对路径，
 *    由后端静态资源暴露。小程序无法直接加载相对路径，必须补成完整地址。
 * 2. 包内置的静态图 —— /static/xxx.png，是小程序包内资源，保持原样即可。
 *
 * 地址未探测出来时（首次启动尚未连上后端）保留原值，
 * 避免拼出无效地址；此时由图片自带的占位样式兜底。
 */
export function resolveImageUrl(path) {
  const value = String(path || '').trim()
  if (!value) return ''
  // 已是完整地址（后端返回绝对地址或外链）时不再处理
  if (/^(https?:)?\/\//i.test(value)) return value
  // 只有后端上传目录才需要拼站点根，包内静态图保持原样
  if (!value.startsWith('/uploads/')) return value
  const origin = getApiOrigin()
  return origin ? `${origin}${value}` : value
}

/**
 * 获取咖啡图片（包内置静态图回退）
 */
function getCoffeeImage(type) {
  return IMAGES.coffee[type] || IMAGES.coffee.latte
}

/**
 * 获取商品详情图（优先详情图，回退到列表图）
 */
export function getProductDetailImage(product) {
  return resolveImageUrl(product?.detailImage || product?.image || getCoffeeImage('latteDetail'))
}
