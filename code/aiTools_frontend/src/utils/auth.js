/**
 * 解析 JWT 的 payload 部分（base64url 解码）
 * @param {String} token
 * @returns {Object|null} payload 对象，解析失败返回 null
 */
const parseJwt = (token) => {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    // payload 是第二部分
    const base64Url = parts[1]
    // base64url 转 base64
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    // 补齐 padding
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const json = decodeURIComponent(escape(atob(padded)))
    return JSON.parse(json)
  } catch (e) {
    return null
  }
}

/**
 * 判断 token 是否有效（存在且未过期）
 * @param {String} token
 * @returns {Boolean}
 */
const isTokenValid = (token) => {
  if (!token) return false
  const payload = parseJwt(token)
  if (!payload || !payload.exp) return false
  // exp 是秒级时间戳
  const now = Math.floor(Date.now() / 1000)
  return payload.exp > now
}

/**
 * 登录校验工具：未登录或 token 过期时清登录态并跳登录页，返回 false
 * @param {Boolean} redirect 是否跳转登录页
 */
export const requireLogin = (redirect = true) => {
  const token = uni.getStorageSync('token')
  if (isTokenValid(token)) return true

  // token 不存在或已过期，清除登录态
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')

  if (redirect) {
    uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
    setTimeout(() => {
      uni.navigateTo({ url: '/pages/login' })
    }, 600)
  }
  return false
}
