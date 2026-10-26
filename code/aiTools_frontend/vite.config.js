import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [
    uni()
  ],
  css: {
    preprocessorOptions: {
      scss: {
        api: 'legacy-js-api',
        silenceDeprecations: ['legacy-js-api', 'import', 'global-builtin', 'color-functions'],
        // 用 @import 把 uni.scss 内容 inline 到每个组件
        // 与 @use 不同，@import 是文件内容直接合并（不是模块加载），
        // 不会被 vite-plugin-uni 重复编译导致 'both define' 错误。
        additionalData: `@import "@/uni.scss";\n`
      }
    }
  },
  server: {
    host: '0.0.0.0',
    // 端口可用环境变量覆盖：VITE_DEV_PORT=5173 npm run dev:h5
    // 为什么要可配：3000 是「谁都想用」的端口。本机若已有别的服务占了
    // 127.0.0.1:3000（Windows 上 0.0.0.0 与 127.0.0.1 可以分别绑定同一个端口），
    // 浏览器访问 localhost:3000 会打到**那个**服务上，表现为「打开是别人的 401 页面」，
    // 很容易误判成自己项目的问题。换个端口即可。
    port: Number(process.env.VITE_DEV_PORT) || 3000,
    // 端口被**完全**占用时不要静默换端口（自动换端口会让「我访问的是哪个端口」变得不可知）
    strictPort: false
  }
})
