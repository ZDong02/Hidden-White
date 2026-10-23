/**
 * 图标构建脚本：SVG 母版 → 小程序/H5 通用的 PNG 色版
 *
 * 为什么不是运行时着色：
 *   微信小程序的 <image> 无法像 SVG 那样继承 currentColor，CSS filter 在部分
 *   安卓 webview 上也不生效。要让图标随「深色卡片上的金 / 浅底上的深金」切换，
 *   最稳的办法是每种色版各出一张 PNG，由 Icon 组件按 tone 选图。
 *
 * 母版约定：
 *   scripts/icons/<name>.svg，24 网格、stroke-width 1.6、圆头圆角、
 *   描边色统一写 currentColor，本脚本负责替换成具体色值。
 *
 * 用法：npm run assets:icons
 * 清单：config/icon-set.json（母版名 + 需要生成的色版）
 */
const fs = require('node:fs')
const path = require('node:path')
const { Resvg } = require('@resvg/resvg-js')

const sourceDir = path.join(__dirname, 'icons')
const outputDir = path.resolve(__dirname, '../static/icons')
const iconSet = require('../config/icon-set.json')

const { tones, icons, outputWidth = 128 } = iconSet
const written = []

for (const [name, toneList] of Object.entries(icons)) {
  const sourcePath = path.join(sourceDir, `${name}.svg`)
  if (!fs.existsSync(sourcePath)) {
    throw new Error(`缺少图标母版：scripts/icons/${name}.svg（清单 config/icon-set.json 中已声明）`)
  }
  const template = fs.readFileSync(sourcePath, 'utf8')

  for (const tone of toneList) {
    const color = tones[tone]
    if (!color) {
      throw new Error(`图标 ${name} 引用了未定义的色版 ${tone}，请先在 config/icon-set.json 的 tones 里登记`)
    }
    // 母版用 currentColor 占位，这里换成本色版的实际色值
    const svg = template.split('currentColor').join(color)
    const png = new Resvg(svg, {
      fitTo: { mode: 'width', value: outputWidth },
      background: 'rgba(0,0,0,0)'
    })
      .render()
      .asPng()

    const file = `${name}-${tone}.png`
    fs.writeFileSync(path.join(outputDir, file), png)
    written.push(file)
  }
}

// 清掉已从清单移除的旧色版，避免包里留下永远用不到的图标
const expected = new Set(written)
for (const file of fs.readdirSync(outputDir)) {
  if (file.endsWith('.png') && !expected.has(file)) {
    fs.unlinkSync(path.join(outputDir, file))
  }
}

console.log(
  `图标已生成：${written.length} 张（${outputWidth}px 宽，${Object.keys(icons).length} 个母版）`
)
