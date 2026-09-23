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
        silenceDeprecations: ['legacy-js-api', 'import']
        // 不再使用 additionalData 自动注入 uni.scss
        // 改为每个组件 <style lang="scss"> 第一行手动写：
        //   @use '@/uni.scss' as *;
        // 这样可以避免 vite-plugin-uni 对 .vue 重复注入导致的 "both define" 错误。
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 3000
  }
})
