import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 统一后端（HiddenWhite-backend）默认占用 8080
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

// 联调「入口守卫」用：后端启用 hiddenwhite.gateway.secret 时，
// 给 vite 设同一个 HW_GATEWAY_SECRET，代理会自动带上 X-HW-Gateway。
// 未设置时不加任何头，日常开发流程零变化。
const gatewaySecret = (process.env.HW_GATEWAY_SECRET || '').trim()
const gatewayHeaders = gatewaySecret ? { 'X-HW-Gateway': gatewaySecret } : {}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        headers: gatewayHeaders,
      },
      // 管理端上传的商品图由后端以 /uploads/** 暴露，
      // 预览图同样需要代理到后端，否则开发态下图片 404。
      '/uploads': {
        target: apiProxyTarget,
        changeOrigin: true,
        headers: gatewayHeaders,
      },
    },
  },
})
