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
/* 全局动效工具类（点击反馈 / 入场 / 错落 / 加载 / 页面转场）
 * 注：uni.scss 通过 vite.config.js 的 additionalData 自动注入。
 * App.vue 不能再写 @use '@/uni.scss'，否则会触发 both-define 错误。
 * App.vue 里只引用 animations.scss（其内部不使用 token，不冲突）。
 */
@use './styles/animations.scss';

* {
  box-sizing: border-box;
}

.safe-area-bottom {
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
