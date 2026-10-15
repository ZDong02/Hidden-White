import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// H5 开发态把 /api 代理到本地 Spring Boot（HiddenWhite-backend，默认 8080）。
// 这样开发时无需关心本机 IP，也不会触发跨域；换端口时用 VITE_API_PROXY_TARGET 覆盖即可。
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [uni.default ? uni.default() : uni()],
  server: {
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true
      }
    }
  }
})