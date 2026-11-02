/**
 * 首页推荐轮播的版式守卫测试。
 *
 * 这两个组件出过两次真实故障，都是「样式看起来只是微调」引起的，肉眼在 H5 上还可能看不出来：
 *   1. 卡片高度固定 + 文案列是 flex column，子元素默认 flex-shrink:1。
 *      内容一超，浏览器不去溢出，而是把行盒压扁 —— 主标题只剩半截、短句被压成一条线。
 *   2. swiper-item 宽度恒等于 swiper 宽度，卡片铺满 item 后相邻两屏是**零间隙**的两块深色面，
 *      圆角、描边、阴影互相压住，滑动时像一张被切开的卡。
 *
 * 这里不跑浏览器，只锁「结构必须成立」的几条：高度联动、缝隙对称、文案不被压缩、
 * 文案列给图片让位。改版式时如果非要动这些，请连带把断言一起想清楚再改。
 *
 * 运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')
const carousel = read('components/RecommendCarousel.vue')
const slide = read('components/RecommendSlide.vue')

/** 取出某个选择器的独立样式块（跳过「.a, .b, .c {}」这种组合选择器里的成员） */
function block(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const pattern = new RegExp(escaped + '\\s*\\{([^}]*)\\}', 'g')
  for (const matched of css.matchAll(pattern)) {
    if (css.slice(0, matched.index).trimEnd().endsWith(',')) continue
    return matched[1]
  }
  assert.fail(`找不到独立的样式块 ${selector}`)
}

/** 取出 SCSS 变量声明值 */
function token(css, name) {
  const matched = css.match(new RegExp(`\\${name}:\\s*([^;]+);`))
  assert.ok(matched, `找不到变量 ${name}`)
  return matched[1].trim()
}

test('卡片高度与 swiper 高度必须来自同一个数字', () => {
  const slideHeight = token(slide, '$hero-card-h')
  const swiperHeight = token(carousel, '$hero-card-h')

  assert.equal(
    slideHeight,
    swiperHeight,
    '两个组件各自声明了 $hero-card-h，数值必须一致，否则卡片会被 swiper 裁掉一截'
  )
  assert.match(block(slide, '.hero-slide'), /height:\s*\$hero-card-h/)
  assert.match(
    block(carousel, '.hero-swiper'),
    /height:\s*\$hero-card-h \+ \$hero-gap-half \* 2/,
    'swiper 高度 = 卡片高度 + 上下两份呼吸位，否则卡片阴影会被裁成硬边'
  )
})

test('相邻两屏之间必须留缝，且首屏仍与页面栅格对齐', () => {
  const slot = block(carousel, '.hero-slot')
  const swiper = block(carousel, '.hero-swiper')

  assert.match(slot, /padding:\s*\$hero-gap-half/, 'slot 内边距就是卡片间距的一半')
  assert.match(
    swiper,
    /margin:\s*\(-\$hero-gap-half\)\s*\(-\$hero-gap-half\)\s*0/,
    'swiper 必须用等量负边距抵消 slot 内边距：既不把卡片撑出栅格，又让相邻卡之间留下缝'
  )
})

test('文案列的子元素一律禁止 flex 收缩', () => {
  const copy = block(slide, '.hero-copy')

  assert.match(copy, /flex-direction:\s*column/)
  assert.doesNotMatch(
    copy,
    /flex-shrink:\s*1/,
    '文案列自己不许收缩'
  )

  for (const selector of ['.hero-eyebrow-row', '.hero-headline', '.hero-caption', '.hero-foot']) {
    assert.match(
      block(slide, selector),
      /flex-shrink:\s*0/,
      `${selector} 必须 flex-shrink: 0：被压缩的行盒会削掉字，这是轮播文字截断的直接原因`
    )
  }
})

test('文案列给图片让位，图片不参与文案列的宽度计算', () => {
  const shared = slide.match(
    /\.hero-headline,\s*\.hero-caption,\s*\.hero-foot\s*\{([^}]*)\}/
  )
  assert.ok(shared, '标题 / 短句 / 操作行应共用同一条右边界')
  assert.match(shared[1], /padding-right:\s*\$hero-copy-right/)

  assert.match(
    block(slide, '.hero-media'),
    /position:\s*absolute/,
    '图片必须绝对定位：参与流式布局会把文案列挤窄，字号再大就必然折行'
  )
  assert.match(
    block(slide, '.hero-headline'),
    /-webkit-line-clamp:\s*2/,
    '主标题最多两行：行数不定会让三屏的文字块高度各自跳动'
  )
})
