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
        // 全局注入 uni.scss，所有 <style lang="scss"> 自动可用 token 变量
        additionalData: `@use "@/uni.scss" as *;\n`
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 3000
  }
})
