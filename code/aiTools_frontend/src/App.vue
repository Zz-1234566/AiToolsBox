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

<style lang="scss">
/* 全局动效工具类（点击反馈 / 入场 / 错落 / 加载 / 页面转场） */
@use './styles/animations.scss';

/* 全局基础样式 */
page {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  background-color: $bg-color;
  color: $text-primary;
  -webkit-font-smoothing: antialiased;
}

* {
  box-sizing: border-box;
}

.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
