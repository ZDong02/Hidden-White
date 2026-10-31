/**
 * 首页推荐位：轮播抽样的纯函数。
 *
 * 为什么抽样放在端上而不是 SQL 的 ORDER BY RAND()：
 *   推荐池是「管理端配置的结果」，抽哪 3 款是「本次浏览的结果」。
 *   若由服务端随机，前端每 15 秒的同步刷新都会拿回一批不一样的数据，
 *   用户在首页停留期间轮播会整块换人——看起来像 bug，而不是惊喜。
 *   这里只做纯计算，池子内容不变时上层会保留已抽好的 3 屏（见 store/index.js）。
 */

/** 首页轮播一屏显示几款推荐 */
export const SLIDE_COUNT = 3

/**
 * 从推荐池里随机抽 count 款（不重复、保持池内的随机顺序）。
 *
 * 用「部分洗牌」而不是 sort(() => Math.random() - 0.5)：
 * 后者在 V8 的小数组上分布并不均匀，且会打乱全部元素、
 * 我们只需要前 count 个。
 *
 * @param {Array} pool   推荐池
 * @param {number} count 需要几款，超过池子大小时按池子大小给
 * @param {() => number} random 随机源，测试时注入固定序列
 */
export function pickSlides(pool, count = SLIDE_COUNT, random = Math.random) {
  const source = Array.isArray(pool) ? pool.filter(Boolean) : []
  if (!source.length) return []
  const limit = Math.max(0, Math.min(count, source.length))
  const rest = source.slice()
  for (let i = 0; i < limit; i += 1) {
    // 只在「还没抽到的区间」里取，已抽出的前 i 项不再参与
    const offset = Math.floor(random() * (rest.length - i))
    const j = Math.min(rest.length - 1, i + offset)
    const picked = rest[j]
    rest[j] = rest[i]
    rest[i] = picked
  }
  return rest.slice(0, limit)
}

/**
 * 推荐池的内容指纹。
 *
 * 只包含「会影响轮播观感」的字段：配置侧改一句文案、换一张图、调一次价格，
 * 指纹就变；仅仅重新拉了一次接口不会变。上层据此决定要不要重新抽样。
 */
export function poolSignature(pool) {
  if (!Array.isArray(pool) || !pool.length) return ''
  return pool
    .map((row) => [
      row.id,
      row.productId,
      row.price,
      row.image,
      row.eyebrow,
      row.headline,
      row.caption,
      row.linkText
    ].join('\u0001'))
    .join('\u0002')
}

/**
 * 某一屏的相邻屏下标（含首尾循环）。
 *
 * 用于图片预加载：轮播是循环的，第 0 屏的「上一屏」是最后一屏。
 * 返回顺序是「上一屏 → 当前 → 下一屏」，便于调试时直接看出预热了哪几张。
 *
 * @param {number} index 当前屏下标
 * @param {number} total 总屏数
 */
export function neighborIndexes(index, total) {
  if (!Number.isInteger(total) || total <= 0) return []
  const safe = ((index % total) + total) % total
  if (total === 1) return [0]
  // 只有两屏时，前一个和后一个是同一个，去重后就是两屏全加载
  if (total === 2) return [safe, (safe + 1) % total]
  return [(safe - 1 + total) % total, safe, (safe + 1) % total]
}

/**
 * 合并两组下标，保序去重。
 *
 * 已放行的下标只增不减：图片已经在内存里，把下标撤掉反而会让 image 重新挂 src。
 */
export function mergeIndexes(current, next) {
  const merged = Array.isArray(current) ? current.slice() : []
  for (const item of Array.isArray(next) ? next : []) {
    if (!merged.includes(item)) merged.push(item)
  }
  return merged
}
