const path = require('node:path')

const root = path.resolve(__dirname, '..')
const argv = process.argv.slice(2)
const isBuild = argv.includes('build')
const platform = argv.find((arg) => arg === 'mp-weixin' || arg === 'h5')

process.env.UNI_INPUT_DIR = root

if (platform) {
  // dev 与 build 写入不同目录。
  // 原实现让两者共用 unpackage/dist/build/mp-weixin，导致调试产物被正式构建
  // 覆盖、反之亦然，微信开发者工具很容易打开到上一次的残留产物。
  // 现在约定：dev → dist/dev/<platform>，build → dist/build/<platform>，
  // 微信开发者工具固定导入 dist/build/mp-weixin（正式产物目录）。
  const mode = isBuild ? 'build' : 'dev'
  process.env.UNI_OUTPUT_DIR = path.join(root, 'dist', mode, platform)
}

require('@dcloudio/vite-plugin-uni/bin/uni.js')
