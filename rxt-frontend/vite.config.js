import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 配置。
 *
 * 这里最关键的是 server.proxy —— 它解决浏览器同源策略问题。
 *
 * 开发时前端跑在 5173，后端跑在 8080，是两个不同的「源」。
 * 如果前端直接 axios 请求 http://localhost:8080/api/user/login，
 * 浏览器会因为跨域（CORS）拦下它。有两种解法：
 *
 *   1. 后端配 CORS 允许 5173 —— 但要处理预检请求、带 Cookie 时的凭证问题，麻烦；
 *   2. 前端把请求发给**自己的源**（/api/...），由 Vite 开发服务器转发给后端 —— 本配置用的方案。
 *
 * 第 2 种的好处是：浏览器眼里所有请求都是同源的，压根不触发 CORS；
 * 而且请求路径在开发和（用 Nginx 反代时的）生产环境里保持一致，前端代码不用改。
 *
 * 注意 rewrite 那段：proxy 必须把 /api 原样保留，
 * 因为后端 Controller 上写的确实是 @RequestMapping("/api/user")。
 * 如果这里写成 rewrite: path => path.replace(/^\/api/, '')，
 * 转发过去就变成了 /user/login，后端会 404。
 */
export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        // 后端地址。默认 8080（application.yml 里配的端口）。
        // 允许用环境变量覆盖，是为了应对两种情况：
        //   1. 8080 被别的程序占用，后端临时换了端口；
        //   2. 后端跑在另一台机器上（比如队友的电脑）。
        // 用法：VITE_API_TARGET=http://127.0.0.1:9000 npm run dev
        target: process.env.VITE_API_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true
        // 不要加 rewrite —— 后端路径本身就带 /api 前缀
      }
    }
  }
})
