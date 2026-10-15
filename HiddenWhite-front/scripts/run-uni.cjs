const path = require('node:path')
const root = path.resolve(__dirname, '..')
process.env.UNI_INPUT_DIR = root
if (process.argv.includes('mp-weixin')) {
  // Both development and release builds use the directory imported by DevTools.
  process.env.UNI_OUTPUT_DIR = path.join(root, 'unpackage/dist/build/mp-weixin')
}
require('@dcloudio/vite-plugin-uni/bin/uni.js')
