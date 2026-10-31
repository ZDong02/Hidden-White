/**
 * 首页推荐轮播抽样的行为测试。
 *
 * 锁住三件事：
 *   1. 抽出来的永远是「池子里真实存在的项」，且不重复；
 *   2. 池子不足 3 款时按实际数量给（不补空卡片，空卡片在首页就是一块黑洞）；
 *   3. 池子没变时抽样结果保持稳定 —— 这是「随机但不在用户眼前乱跳」的判定依据。
 *
 * 运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { mergeIndexes, neighborIndexes, pickSlides, poolSignature, SLIDE_COUNT } from '../utils/recommend.js'

const makePool = (count) =>
  Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    productId: `p${index + 1}`,
    price: 20 + index,
    image: `/static/coffee/p${index + 1}.png`,
    eyebrow: '本周推荐',
    headline: `第 ${index + 1} 款`,
    caption: '描述',
    linkText: '查看',
  }))

/** 固定序列随机源：按给定顺序回放，便于断言「抽到哪几款」 */
function seeded(values) {
  let index = 0
  return () => values[index++ % values.length]
}

test('首页最多展示 3 款推荐', () => {
  assert.equal(SLIDE_COUNT, 3)
  assert.equal(pickSlides(makePool(8)).length, 3)
})

test('抽样结果不重复且都来自推荐池', () => {
  const pool = makePool(6)
  const ids = new Set(pool.map(item => item.id))
  // 连续抽 50 次，每次都必须是 3 个互不相同的真实项
  for (let round = 0; round < 50; round += 1) {
    const slides = pickSlides(pool)
    assert.equal(slides.length, 3)
    assert.equal(new Set(slides.map(item => item.id)).size, 3)
    slides.forEach(slide => assert.ok(ids.has(slide.id)))
  }
})

test('池子不足 3 款时按实际数量给，不补空位', () => {
  assert.equal(pickSlides(makePool(2)).length, 2)
  assert.equal(pickSlides(makePool(1)).length, 1)
  assert.deepEqual(pickSlides([]), [])
  assert.deepEqual(pickSlides(null), [])
})

test('随机源可控时抽样结果可预期', () => {
  // random 恒为 0 → 每次都取区间内第一个，等价于按池内顺序取前 3
  assert.deepEqual(
    pickSlides(makePool(5), 3, seeded([0])).map(item => item.id),
    [1, 2, 3]
  )
  // random 恒为 0.99 → 每轮都取区间内最后一个，第一屏必然是池尾那一款
  const tail = pickSlides(makePool(5), 3, seeded([0.99]))
  assert.equal(tail[0].id, 5)
  assert.equal(new Set(tail.map(item => item.id)).size, 3)
})

test('抽样不会改动传入的池子（reactive 数组尤其重要）', () => {
  const pool = makePool(4)
  const before = pool.map(item => item.id)
  pickSlides(pool)
  assert.deepEqual(pool.map(item => item.id), before)
})

test('内容指纹只在配置变化时改变', () => {
  const pool = makePool(3)
  const same = makePool(3)
  assert.equal(poolSignature(pool), poolSignature(same))

  // 改一句文案 → 指纹变（前台需要重抽并展示新文案）
  const edited = makePool(3)
  edited[1].headline = '换了一句'
  assert.notEqual(poolSignature(pool), poolSignature(edited))

  // 改价格 → 指纹变（轮播上显示的是实时价）
  const repriced = makePool(3)
  repriced[0].price = 99
  assert.notEqual(poolSignature(pool), poolSignature(repriced))

  // 停用一款（池子少一项）→ 指纹变
  assert.notEqual(poolSignature(pool), poolSignature(makePool(2)))

  // 空池的指纹是空串，与「只有一项」区分得开
  assert.equal(poolSignature([]), '')
})

test('相邻屏下标按循环计算，首尾相连', () => {
  // 三屏：第一屏的「上一屏」是最后一屏，这是 circular 轮播的真实相邻关系
  assert.deepEqual(neighborIndexes(0, 3), [2, 0, 1])
  assert.deepEqual(neighborIndexes(1, 3), [0, 1, 2])
  assert.deepEqual(neighborIndexes(2, 3), [1, 2, 0])

  // 只有两屏时前一个和后一个是同一屏，不能重复放行
  assert.deepEqual(neighborIndexes(0, 2), [0, 1])
  assert.deepEqual(neighborIndexes(1, 2), [1, 0])

  // 只有一屏、以及异常入参：不能返回越界下标，否则 eagerList 里会挂着不存在的屏
  assert.deepEqual(neighborIndexes(0, 1), [0])
  assert.deepEqual(neighborIndexes(0, 0), [])
  assert.deepEqual(neighborIndexes(0, -3), [])
  assert.deepEqual(neighborIndexes(0, 2.5), [])
})

test('相邻屏下标对越界下标做环绕', () => {
  // 5 → 2、-1 → 2：先规整到合法区间，再取前后邻
  assert.deepEqual(neighborIndexes(5, 3), [1, 2, 0])
  assert.deepEqual(neighborIndexes(-1, 3), [1, 2, 0])
})

test('合并下标保序去重，已放行的不会被撤掉', () => {
  assert.deepEqual(mergeIndexes([0], [0, 1]), [0, 1])
  assert.deepEqual(mergeIndexes([0, 1], [1, 2]), [0, 1, 2])
  // 图片已经加载过就不该撤回下标：撤了 image 会重新挂 src、重新发请求
  assert.deepEqual(mergeIndexes([2, 0, 1], [1]), [2, 0, 1])
  assert.deepEqual(mergeIndexes(undefined, [1, 2]), [1, 2])
  assert.deepEqual(mergeIndexes([0], null), [0])
})
