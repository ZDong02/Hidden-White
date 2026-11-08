/**
 * 弹窗一致性守卫。
 *
 * 锁死 repo 里两件容易被后续迭代破坏的事：
 *   1. 点单端不允许再直接调用系统弹窗（uni.showModal / wx.showModal / alert / confirm / prompt）——
 *      原生弹窗的字体、按钮、圆角由系统决定，引用不到设计令牌，三端表现各不相同。
 *      唯一允许的落点是 `store/dialog.js` 的降级分支（页面忘了挂宿主时的兜底）。
 *   2. 用到弹窗 API 的页面必须挂 `<DialogHost />`，否则请求只能走降级路径，
 *      界面又会退回系统弹窗 —— 这是「改了却看不出效果」的典型原因。
 *
 * 运行：npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/** 需要扫描的源码目录（不含构建产物与依赖） */
const SOURCE_DIRS = ['pages', 'components', 'utils', 'store', 'config']
const IGNORED_DIRS = new Set(['node_modules', 'dist', 'unpackage', 'static', 'preview'])

/** 原生弹窗的降级实现集中在这里，其它文件一律不许再出现 */
const NATIVE_DIALOG_WHITELIST = new Set(['store/dialog.js'])

const PATTERNS = [
  { re: /\b(uni|wx)\.showModal\b/g, label: 'native.showModal' },
  { re: /(?<![\w.$])(window\.)?alert\s*\(/g, label: 'alert()' },
  { re: /(?<![\w.$])confirm\s*\(/g, label: 'confirm()' },
  { re: /(?<![\w.$])prompt\s*\(/g, label: 'prompt()' }
]

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (IGNORED_DIRS.has(entry.name)) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, files)
    else if (['.vue', '.js', '.ts'].includes(path.extname(entry.name))) files.push(full)
  }
  return files
}

const read = (file) => fs.readFileSync(file, 'utf8')
const rel = (file) => path.relative(root, file).replace(/\\/g, '/')

/**
 * 注释里讨论「不要用某某原生弹窗」是正常的，扫描时跳过整行注释，
 * 否则守卫测试会逼着大家不敢在代码里留说明。
 */
const isCommentLine = (line) => /^\s*(\/\/|\/\*|\*)/.test(line)

const sourceFiles = SOURCE_DIRS.flatMap((dir) => {
  const full = path.join(root, dir)
  return fs.existsSync(full) ? walk(full) : []
})

test('不允许直接调用系统弹窗，统一走 components/ui/Dialog', () => {
  const hits = []
  for (const file of sourceFiles) {
    const name = rel(file)
    if (NATIVE_DIALOG_WHITELIST.has(name)) continue
    read(file)
      .split('\n')
      .forEach((line, index) => {
        if (isCommentLine(line)) return
        for (const { re, label } of PATTERNS) {
          re.lastIndex = 0
          if (re.test(line)) {
            hits.push(`${name}:${index + 1} 使用了 ${label}`)
            break
          }
        }
      })
  }
  assert.deepEqual(
    hits,
    [],
    `发现 ${hits.length} 处系统弹窗调用，请改用 dialogNotice / dialogConfirm / dialogPrompt：\n${hits.join('\n')}`
  )
})

test('用到弹窗 API 的页面必须挂 <DialogHost />', () => {
  /** 只有这几个函数会真的渲染弹窗；showError / showInfo 仍是 toast，不要求宿主 */
  const DIALOG_EXPORTS = /^(showNotice|showConfirm|showPrompt|dialogNotice|dialogConfirm|dialogPrompt)$/
  const IMPORT_FROM = /import\s*\{([^}]*)\}\s*from\s*'([^']*(?:utils\/toast|store\/dialog))'/g
  const problems = []

  for (const file of sourceFiles.filter((item) => item.endsWith('.vue'))) {
    const name = rel(file)

    // 只看页面：组件由所在页面提供宿主（如四个 Tab 面板挂在首页壳页里）
    if (!name.startsWith('pages/')) continue

    const source = read(file)
    const importsDialogApi = [...source.matchAll(IMPORT_FROM)].some(([, names]) =>
      names
        .split(',')
        .map((item) => item.trim().split(/\s+as\s+/)[0])
        .some((item) => DIALOG_EXPORTS.test(item))
    )

    if (importsDialogApi && !/<DialogHost\b/.test(source)) {
      problems.push(`${name}: 使用了弹窗 API 但没有挂载 <DialogHost />`)
    }
  }

  assert.deepEqual(problems, [], problems.join('\n'))
})
