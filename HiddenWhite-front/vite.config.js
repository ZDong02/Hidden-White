import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// H5 开发态把 /api 代理到本地 Spring Boot（HiddenWhite-backend，默认 8080）。
// 这样开发时无需关心本机 IP，也不会触发跨域；换端口时用 VITE_API_PROXY_TARGET 覆盖即可。
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

// 联调「入口守卫」用：本地想验证后端的 IngressGuardFilter 时，
// 把同一个密钥给 vite（HW_GATEWAY_SECRET=...），代理会自动带上 X-HW-Gateway。
// 未设置时不加任何头，日常开发流程零变化。
const gatewaySecret = (process.env.HW_GATEWAY_SECRET || '').trim()
const gatewayHeaders = gatewaySecret ? { 'X-HW-Gateway': gatewaySecret } : {}

/**
 * 生产构建把「开发用候选后端地址」清空。
 *
 * <p>`config/backend-hosts.js` 里登记的是开发机内网 IP（办公室 / 热点 / 历史网段）。
 * 这些地址属于网络拓扑信息，不该随网页或小程序包发到用户手上；
 * 生产环境的入口是固定的（同源 /api 或 VITE_API_BASE_URL），本来也不需要它们。
 *
 * <p>为什么不用运行时判断：uni-app 把 `import.meta.env` 注入成一个对象，
 * `env.PROD` 在产物里是普通属性访问，压缩器无法当成常量折叠，
 * 数组会原样留下。这里在构建阶段直接换掉模块内容，才真正不进产物。
 */
function stripDevBackendHosts() {
  return {
    name: 'hw-strip-dev-backend-hosts',
    apply: 'build',
    transform(code, id) {
      if (/config[\\/]backend-hosts\.js$/.test(id)) {
        return { code: 'export const BACKEND_HOSTS = []', map: null }
      }
      return null
    }
  }
}

export default defineConfig({
  plugins: [uni.default ? uni.default() : uni(), stripDevBackendHosts()],
  server: {
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        headers: gatewayHeaders
      },
      // 管理端上传的商品图由后端以 /uploads/** 暴露，同样代理到后端，
      // 否则 H5 开发态下图片会 404。
      '/uploads': {
        target: apiProxyTarget,
        changeOrigin: true,
        headers: gatewayHeaders
      }
    }
  }
})