/**
 * 购物车飞入动画工具函数（v2 高级交互）
 *
 * 用法：
 * import { createFlyToCartAnimation } from '@/utils/cart-animation'
 *
 * // 点击加购按钮时
 * const rect = event.target.getBoundingClientRect()
 * createFlyToCartAnimation(rect, () => {
 *   // 动画完成回调：更新购物车数据
 *   shop.cart.push(product)
 * })
 */

/**
 * 创建飞入购物车动画
 * @param {Object} startRect - 起点元素的位置信息 {left, top, width, height}
 * @param {Function} onComplete - 动画完成回调
 * @param {Object} options - 配置选项
 */
export function createFlyToCartAnimation(startRect, onComplete, options = {}) {
  const {
    duration = 600,
    iconSize = 40,
    curve = 'cubic-bezier(0.16, 1, 0.3, 1)'
  } = options

  // 创建飞行元素
  const flyIcon = document.createElement('view')
  flyIcon.className = 'cart-fly-icon'
  flyIcon.style.cssText = `
    position: fixed;
    left: ${startRect.left + startRect.width / 2 - iconSize / 2}px;
    top: ${startRect.top + startRect.height / 2 - iconSize / 2}px;
    width: ${iconSize}rpx;
    height: ${iconSize}rpx;
    z-index: 1000;
    pointer-events: none;
    background: #171713;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
  `

  // 添加图标内容（简化版咖啡杯图标）
  flyIcon.innerHTML = `
    <text style="color: #e9d191; font-size: 24rpx;">☕</text>
  `

  document.body.appendChild(flyIcon)

  // 获取购物车位置（底部固定栏）
  const cartBar = document.querySelector('.cart-bar')
  if (!cartBar) {
    // 如果找不到购物车，直接完成
    setTimeout(() => {
      flyIcon.remove()
      onComplete?.()
    }, 100)
    return
  }

  const cartRect = cartBar.getBoundingClientRect()
  const targetX = cartRect.left + 50
  const targetY = cartRect.top + cartRect.height / 2

  // 计算偏移量
  const deltaX = targetX - (startRect.left + startRect.width / 2)
  const deltaY = targetY - (startRect.top + startRect.height / 2)

  // 应用动画
  requestAnimationFrame(() => {
    flyIcon.style.transition = `transform ${duration}ms ${curve}, opacity ${duration}ms ${curve}`
    flyIcon.style.transform = `translate(${deltaX}px, ${deltaY}px) scale(0.3)`
    flyIcon.style.opacity = '0'
  })

  // 动画结束清理
  setTimeout(() => {
    flyIcon.remove()
    onComplete?.()

    // 触发购物车晃动动画
    if (cartBar) {
      cartBar.style.animation = 'cart-shake 400ms cubic-bezier(0.34, 1.4, 0.64, 1)'
      setTimeout(() => {
        cartBar.style.animation = ''
      }, 400)
    }
  }, duration)
}

/**
 * 小程序环境适配版本（使用 uni.createAnimation）
 * @param {Object} startRect - 起点位置 {left, top, width, height}
 * @param {Function} onComplete - 完成回调
 */
export function createFlyToCartAnimationMp(startRect, onComplete) {
  // 小程序环境下使用简化版动画
  // 实际项目中可以用 uni.createAnimation 实现

  // #ifdef MP-WEIXIN
  // 触觉反馈
  uni.vibrateShort({ type: 'light' })
  // #endif

  // 延迟执行回调，模拟动画时长
  setTimeout(() => {
    onComplete?.()
  }, 300)
}

/**
 * 统一入口：自动检测环境
 */
export function flyToCart(event, onComplete) {
  // #ifdef H5
  const rect = event.target?.getBoundingClientRect?.()
  if (rect) {
    createFlyToCartAnimation(rect, onComplete)
    return
  }
  // #endif

  // #ifdef MP-WEIXIN
  // 小程序环境：获取元素位置需要用 createSelectorQuery
  uni.createSelectorQuery()
    .select('.add-button')
    .boundingClientRect()
    .exec(res => {
      if (res[0]) {
        createFlyToCartAnimationMp(res[0], onComplete)
      } else {
        onComplete?.()
      }
    })
  return
  // #endif

  // 其他环境直接回调
  onComplete?.()
}
