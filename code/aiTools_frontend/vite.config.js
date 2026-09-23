import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [
    uni()
  ],
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        silenceDeprecations: ['legacy-js-api', 'import'],
        // 全局注入 uni.scss，但跳过 App.vue / animations.scss 等已自管的入口
        // 函数形式 additionalData：fileName 是不含 .scss 后缀的路径
        additionalData: (source, filename) => {
          // 这些入口文件不允许注入（避免"both define a variable"）
          const skipPatterns = ['App.vue', 'styles/animations', 'uni.scss']
          if (skipPatterns.some((p) => filename.includes(p))) {
            return ''
          }
          return `@use "@/uni.scss" as *;\n`
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 3000
  }
})
