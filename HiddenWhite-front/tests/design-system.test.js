/**
 * 设计一致性守卫测试。
 *
 * 锁死两件容易被后续迭代破坏的事：
 *   1. 点单端不允许出现 emoji / 字形符号冒充图标（☕ ✦ › ↗ ✓ 这类），
 *      它们在不同系统上是三种粗细三种比例，无法与设计规范对齐；
 *   2. 每个 <Icon> 引用的图标名与色版都必须在 config/icon-set.json 里登记过，
 *      并且 static/icons 下确实生成了对应的 PNG（否则线上就是一张空白图）。
 *
 * 运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const iconSet = JSON.parse(fs.readFileSync(path.join(root, 'config/icon-set.json'), 'utf8'))

/** 需要扫描的源码目录（不含构建产物与依赖） */
const SOURCE_DIRS = ['pages', 'components', 'utils', 'config', 'store', 'styles']
const SOURCE_EXTENSIONS = ['.vue', '.js', '.json', '.scss']
const IGNORED_DIRS = new Set(['node_modules', 'dist', 'unpackage', 'static'])

/**
 * 禁止出现的字符区间。
 * 只覆盖「被当作图标使用」的符号区，不误伤中文排版标点（… —— · 「」）与
 * 数学/文案符号（× − ¥）；→ 与 ← 在注释里作为流程箭头，单独放行。
 */
const BANNED_RANGES = [
  [0x1f000, 0x1faff], // emoji 表情
  [0x2190, 0x21ff], // 箭头（↗ 等）
  [0x2300, 0x23ff], // 技术符号（⌂ ⌖ 等）
  [0x25a0, 0x25ff], // 几何图形（◉ ◇ 等）
  [0x2600, 0x27bf], // 杂项符号与装饰符（☕ ☰ ✦ ✧ ✓）
  [0x2b00, 0x2bff], // 各类星形/箭头（⭐ 等）
  [0xfe0f, 0xfe0f] // 变体选择符（把普通字符变成 emoji）
]
const ALLOWED_CHARS = new Set(['\u2192', '\u2190'])

function isBanned(char) {
  if (ALLOWED_CHARS.has(char)) return false
  const code = char.codePointAt(0)
  return BANNED_RANGES.some(([from, to]) => code >= from && code <= to)
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (IGNORED_DIRS.has(entry.name)) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, files)
    } else if (SOURCE_EXTENSIONS.includes(path.extname(entry.name))) {
      files.push(full)
    }
  }
  return files
}

const sourceFiles = SOURCE_DIRS.flatMap((dir) => walk(path.join(root, dir)))
const read = (file) => fs.readFileSync(file, 'utf8')
const rel = (file) => path.relative(root, file).replace(/\\/g, '/')

test('源码里不允许出现 emoji 或字形符号图标', () => {
  const hits = []
  for (const file of sourceFiles) {
    read(file)
      .split('\n')
      .forEach((line, index) => {
        for (const char of line) {
          if (isBanned(char)) {
            hits.push(`${rel(file)}:${index + 1} 出现 U+${char.codePointAt(0).toString(16).toUpperCase()}`)
            break
          }
        }
      })
  }
  assert.deepEqual(hits, [], `发现 ${hits.length} 处 emoji/符号图标，请改用 <Icon>：\n${hits.join('\n')}`)
})

test('config/icon-set.json 的色版都已定义颜色', () => {
  for (const [name, tones] of Object.entries(iconSet.icons)) {
    assert.ok(tones.length > 0, `${name} 未声明任何色版`)
    for (const tone of tones) {
      assert.match(
        iconSet.tones[tone] || '',
        /^#[0-9a-f]{6}$/i,
        `${name} 引用的色版 ${tone} 未在 tones 中定义颜色`
      )
    }
  }
})

test('每个声明的图标色版都已生成 PNG，且没有多余产物', () => {
  const expected = new Set()
  for (const [name, tones] of Object.entries(iconSet.icons)) {
    for (const tone of tones) expected.add(`${name}-${tone}.png`)
  }

  const actual = new Set(
    fs
      .readdirSync(path.join(root, 'static/icons'))
      .filter((file) => file.endsWith('.png'))
  )

  const missing = [...expected].filter((file) => !actual.has(file))
  const orphan = [...actual].filter((file) => !expected.has(file))

  assert.deepEqual(missing, [], `缺少图标产物，请运行 npm run assets:icons：${missing.join(', ')}`)
  assert.deepEqual(orphan, [], `存在已下线的图标产物，请运行 npm run assets:icons 清理：${orphan.join(', ')}`)
})

test('模板里的 <Icon> 只引用已登记的图标名与色版', () => {
  const problems = []
  for (const file of sourceFiles.filter((item) => item.endsWith('.vue'))) {
    const source = read(file)
    const tags = source.match(/<Icon\b[\s\S]*?\/>/g) || []
    for (const tag of tags) {
      const name = tag.match(/(?:^|\s)name="([\w-]+)"/)
      const tone = tag.match(/(?:^|\s)tone="([\w-]+)"/)
      if (name && !iconSet.icons[name[1]]) {
        problems.push(`${rel(file)}: 未登记的图标 ${name[1]}`)
        continue
      }
      if (tone && !iconSet.tones[tone[1]]) {
        problems.push(`${rel(file)}: 未定义的色版 ${tone[1]}`)
      }
      if (name && tone && iconSet.icons[name[1]] && !iconSet.icons[name[1]].includes(tone[1])) {
        problems.push(`${rel(file)}: 图标 ${name[1]} 没有 ${tone[1]} 色版`)
      }
    }
  }
  assert.deepEqual(problems, [], problems.join('\n'))
})

test('数据表里声明的图标名（icon: xxx）都已登记', () => {
  // uni.showToast / showLoading 的 icon 是原生枚举（none / success / ...），
  // 与自有图标集无关，校验时排除
  const NATIVE_ICON_VALUES = new Set(['none', 'success', 'error', 'loading', 'info', 'warn'])
  const problems = []
  for (const file of sourceFiles) {
    const source = read(file)
    for (const match of source.matchAll(/\bicon:\s*'([\w-]+)'/g)) {
      if (NATIVE_ICON_VALUES.has(match[1])) continue
      if (!iconSet.icons[match[1]]) {
        problems.push(`${rel(file)}: 数据里引用了未登记的图标 ${match[1]}`)
      }
    }
  }
  assert.deepEqual(problems, [], problems.join('\n'))
})
