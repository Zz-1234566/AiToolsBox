<script>
import { initTheme } from '@/utils/theme'

export default {
  onLaunch() {
    initTheme()
    console.log('App Launch')
  },
  onShow() {
    console.log('App Show')
  },
  onHide() {
    console.log('App Hide')
  }
}
</script>

<!--
  App.vue 使用纯 <style> 而非 <style lang="scss">，原因：
  vite.config.js 的 css.preprocessorOptions.scss.additionalData
  会把 @use '@/uni.scss' as *; 注入到所有 <style lang="scss">，
  若 App.vue 也走 sass 编译，它内部需要再 @use animations.scss，
  而 animations.scss 自己也会被注入一次 @use '@/uni.scss'，
  sass 会报 "both define a variable" 错误。
  解决办法：App.vue 走纯 CSS，全局动效类直接 inline。
-->

<style>
/* ---------- 全局基础样式 ---------- */
* {
  box-sizing: border-box;
}

.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}

/* ---------- 全局动效工具类 ---------- */
/* 点击反馈：统一使用透明度，去掉 scale 与弹性曲线 */
.press-scale {
  transition: opacity 0.1s ease-in-out;
}
.press-scale:active {
  opacity: 0.85;
}

.press-opacity {
  transition: opacity 0.1s ease-in-out;
}
.press-opacity:active {
  opacity: 0.85;
}

/* 抖动（表单错误） */
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-8rpx); }
  40%, 80% { transform: translateX(8rpx); }
}
.shake {
  animation: shake 0.4s ease;
}

/* 入场动画 */
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(40rpx); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in-up {
  animation: fadeInUp 0.4s ease both;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.animate-fade-in {
  animation: fadeIn 0.3s ease both;
}

@keyframes scaleIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
.animate-scale-in {
  animation: scaleIn 0.25s ease-out both;
}

/* 错落入场（列表） */
.animate-stagger {
  opacity: 0;
  animation: fadeInUp 0.4s ease forwards;
}

/* 加载动画 */
@keyframes pulse-dot {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
.loading-dots {
  display: inline-flex;
  gap: 12rpx;
}
.loading-dots .dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background-color: currentColor;
  animation: pulse-dot 1.2s infinite ease-in-out;
}
.loading-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.loading-dots .dot:nth-child(3) { animation-delay: 0.4s; }
</style>