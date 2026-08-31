/**
 * 401 / 业务 401 统一处理（P2-B11 + P0 用户反馈）
 *
 * 用户反馈：原 request.js 业务 401 只 Toast 不跳登录，导致用户继续在页面上操作
 * 全部请求 401 → Toast 雪崩。现在统一为：Toast + 清登录态 + 跳登录页。
 *
 * 行为：业务 401 与 HTTP 401 完全一致（都跳登录）
 */

/** 单一来源：清登录态 + Toast + 跳登录页 */
export const handleAuthError = (message, options = {}) => {
  const { showToast = true, redirectToLogin = true } = options

  // 1) 清登录态
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')

  // 2) Toast
  if (showToast) {
    uni.showToast({
      title: message || '登录已过期，请重新登录',
      icon: 'none',
      duration: 1500
    })
  }

  // 3) 跳登录页（业务 401 也跳，避免 401 雪崩）
  if (redirectToLogin) {
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/login' })
    }, 600)
  }
}

/**
 * @deprecated P0 用户反馈：业务 401 应跳登录。
 * 保留为 alias 避免破坏旧 import；新代码请直接用 handleAuthError。
 */
export const handleBusiness401 = (message) => handleAuthError(message)
