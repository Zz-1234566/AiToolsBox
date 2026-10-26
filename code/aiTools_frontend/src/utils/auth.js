import { base64Decode } from './base64'

/**
 * 本地存储（storage）的 key —— **全项目唯一定义处**。
 *
 * <p>storage key 是**跨模块共享契约**：`auth.js` 写、`request.js` 读、
 * `auth-error-handler.js` 删、`settings.vue` 清登录态。
 * 散落成字面量时，改一处漏一处就会出现
 * 「写的是 `'Token'`、读的是 `'token'`」→ **登录态诡异失效，且极难排查**。
 *
 * <p>新增 key 时加到这里，并同步检查所有读写点。
 */
export const STORAGE_KEYS = {
  /** 登录 token（JWT） */
  TOKEN: 'token',
  /** 登录用户信息（含 id / account / username / avatar / role） */
  USER_INFO: 'userInfo',
  /** 忘记密码 / 注册流程中暂存的账号（登录页 onShow 读取并清空） */
  PENDING_ACCOUNT: 'pendingAccount'
}

/**
 * 解析 JWT 的 payload 部分（base64url 解码）
 * @param {String} token
 * @returns {Object|null} payload 对象，解析失败返回 null
 */
const parseJwt = (token) => {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    // payload 是第二部分（base64url，解码由 utils/base64 跨端安全实现）
    const json = base64Decode(parts[1])
    if (!json) return null
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
  const token = uni.getStorageSync(STORAGE_KEYS.TOKEN)
  if (isTokenValid(token)) return true

  // token 不存在或已过期，清除登录态
  uni.removeStorageSync(STORAGE_KEYS.TOKEN)
  uni.removeStorageSync(STORAGE_KEYS.USER_INFO)

  if (redirect) {
    uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
    setTimeout(() => {
      uni.navigateTo({ url: '/pages/login' })
    }, 600)
  }
  return false
}

/**
 * 当前用户是否为管理员（角色来自登录响应 userInfo.role）
 * 注意：仅用于前端显隐控制，真正的权限由后端接口校验
 * @returns {Boolean}
 */
export const isAdmin = () => {
  const userInfo = uni.getStorageSync(STORAGE_KEYS.USER_INFO)
  return !!(userInfo && userInfo.role === 'admin')
}
