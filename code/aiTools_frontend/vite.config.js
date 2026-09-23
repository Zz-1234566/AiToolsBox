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
    port: 3000
  }
})
