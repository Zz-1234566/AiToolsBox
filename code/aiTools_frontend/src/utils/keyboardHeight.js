/**
 * 键盘高度监听（App 端专用）。
 *
 * 【为什么需要这个文件】
 * App 端（尤其 iOS）的 webview 在软键盘弹起时**不会 resize**：
 * - `input` 的 `adjust-position` 官方支持列表里**只有 App-Android**，
 *   iOS 端该属性无效 → 页面不会被顶起；
 * - iOS webview 高度不变 → `position: fixed` 的底部弹层**原地不动**，
 *   被键盘盖住 → 弹层里的输入框打不了字、底部按钮点不到。
 *
 * 唯一可靠的办法是**自己量键盘高度**，把 fixed 弹层整体抬到键盘上方。
 *
 * 【为什么是「唯一出口」】
 * 弹层抬升这套逻辑要同时用在 workflow.vue（运行弹层）、prompt-list.vue（编辑弹层）
 * 等多处，若各页各写一份监听，卸载时容易漏解绑导致回调打到已销毁页面。
 * 故收敛到本文件：页面只管用返回值，绑定/解绑由本文件统一处理。
 *
 * 【跨端行为】
 * - App：`uni.onKeyboardHeightChange` 返回键盘高度（px），键盘收起时为 0。
 * - H5 / 小程序：**不支持**该 API（官方兼容表为 ✗），本文件返回恒为 0 的 ref，
 *   页面据此不做任何抬升 —— H5 由浏览器自身处理（聚焦时自动滚动到可见区）。
 *
 * @example
 *   const { keyboardHeight } = useKeyboardHeight()
 *   // <view class="mask" :style="{ '--kb': keyboardHeight + 'px' }">
 */
import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 订阅键盘高度变化。
 *
 * @returns {{ keyboardHeight: import('vue').Ref<number> }} 键盘高度（px），收起时为 0
 */
export const useKeyboardHeight = () => {
  /** 键盘高度（px）。H5/小程序恒为 0 */
  const keyboardHeight = ref(0)

  // #ifdef APP-PLUS
  const handler = (res) => {
    keyboardHeight.value = (res && res.height) || 0
  }
  onMounted(() => {
    // 能力探测：老版本基础库没有该 API，缺了只是不抬升，不能因此报错
    if (typeof uni.onKeyboardHeightChange === 'function') {
      uni.onKeyboardHeightChange(handler)
    }
  })
  onUnmounted(() => {
    // 必须传同一个函数对象才能解绑；不传会移除**所有**监听，误伤其他页面
    if (typeof uni.offKeyboardHeightChange === 'function') {
      uni.offKeyboardHeightChange(handler)
    }
  })
  // #endif

  return { keyboardHeight }
}
