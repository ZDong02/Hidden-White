/**
 * 开屏动画「一次冷启动放一次」的开关。
 *
 * 为什么是模块级布尔而不是 storage：
 *   小程序冷启动时 JS 运行时是新建的，模块状态自然重置 → 本次启动会播；
 *   从后台切回、或从详情页 navigateBack 回首页时运行时还在 → 不重播。
 *   若写进 storage，就变成「永久只播一次」，第二次启动再也看不到开屏，
 *   与开屏动画的用途（每次启动给品牌一个亮相）相反。
 *
 * 为什么由调用方「取走」而不是 has + mark 两步：
 *   两步之间可以被打断（例如页面被复用时重复执行 setup），
 *   而取走是同步原子的，重复调用只会拿到一次 true。
 */
let available = true

/**
 * 取走本次启动的开屏机会。
 * @returns {boolean} 本次需要播放开屏时返回 true，之后恒为 false
 */
export function consumeSplash() {
  if (!available) return false
  available = false
  return true
}
