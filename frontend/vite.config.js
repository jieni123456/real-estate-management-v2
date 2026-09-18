import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * Vite 配置。对应需求报告 R-004 阶段 2。
 *
 * 开发代理（server.proxy）是这里最值得讲的一段：
 *
 * 浏览器有「同源策略」——页面在 localhost:5173，直接请求 localhost:8080 属于跨域，
 * 浏览器会先发一个 OPTIONS 预检，后端必须返回 CORS 响应头才放行。开发期绕开它的
 * 常规做法是让请求先发给 Vite（同源，不触发跨域），再由 Vite 在服务端转发给后端：
 *
 *   浏览器 ──/api/houses──▶ Vite :5173 ──▶ 后端 :8080
 *           （同源，无预检）        （服务端到服务端，不受同源策略约束）
 *
 * 所以开发期**不需要后端开 CORS**。后端那份可配置的 CORS（web/config/CorsConfig.java）
 * 是给「前端与后端真的不同源」的场景准备的（例如 dist 交给 nginx 托管、后端另占一个域名）。
 */
export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      // 用 @ 指代 src，避免写成一串 ../../../
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  server: {
    port: 5173,
    // 端口被占用时直接报错，而不是悄悄换一个 —— 否则改了端口自己不知道
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        // 把请求的 Host 头改成目标地址，后端按域名做校验时才不会误判
        changeOrigin: true
      }
    }
  },

  build: {
    // 生产构建的输出目录，交给 nginx 或后端静态目录托管
    outDir: 'dist',
    sourcemap: false
  }
})
