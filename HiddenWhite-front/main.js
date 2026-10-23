import { createSSRApp } from 'vue'
import App from './App'

// 本工程固定 Vue 3（manifest.json → vueVersion: "3"），
// 因此不再保留 uni-app 模板自带的 Vue 2 分支与其 polyfill 适配器
// （`uni.promisify.adaptor` 只在 Vue 2 分支里被引用，已一并移除）。
export function createApp() {
  const app = createSSRApp(App)
  return {
    app
  }
}
