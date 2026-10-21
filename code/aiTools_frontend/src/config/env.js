/**
 * 后端服务地址（运行时可切换）。
 *
 * 【为什么不能硬编码】
 * uni-app 官方明确：localhost / 127.0.0.1 在**手机 App 端无法访问**
 * （「这样的联网地址手机App端是无法访问的，请使用手机可访问的IP进行联网」）。
 * 硬编码 localhost 意味着：H5 调试一切正常，打成 App 放到手机上必连不上。
 *
 * 【切换优先级】
 * 1. storage 里保存的 server（用户在 App 内「设置」页填写，见 pages/settings.vue）
 * 2. 环境变量 VITE_API_BASE（构建时注入，便于 CI / 不同环境出包）
 * 3. 默认 http://localhost:8080（本机 H5 调试兜底）
 *
 * 【为什么用 getter 而不是常量】
 * 常量在模块加载时求值一次，用户在设置页改了地址后不刷新页面不会生效；
 * getter 每次调用都重新读 storage，改完立即生效。
 */

const DEFAULT_BASE_URL = 'http://localhost:8080'

/** storage key：保存后端地址 */
export const SERVER_STORAGE_KEY = 'aiToolbox.serverBaseUrl'

/** 读取当前生效的后端地址（去掉尾部斜杠，避免拼接出 `//`） */
export const getBaseUrl = () => {
  let url = ''
  try {
    url = uni.getStorageSync(SERVER_STORAGE_KEY) || ''
  } catch (e) {
    url = ''
  }
  if (!url) {
    // #ifdef H5
    // Vite 环境变量：构建时注入，H5 端可用
    try {
      url = (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE) || ''
    } catch (e) {
      url = ''
    }
    // #endif
  }
  if (!url) url = DEFAULT_BASE_URL
  return String(url).replace(/\/+$/, '')
}

/**
 * 保存后端地址（设置页调用）。
 * @param {String} url 形如 http://192.168.1.100:8080
 */
export const setBaseUrl = (url) => {
  const v = String(url || '').trim().replace(/\/+$/, '')
  if (!v) {
    uni.removeStorageSync(SERVER_STORAGE_KEY)
    return DEFAULT_BASE_URL
  }
  uni.setStorageSync(SERVER_STORAGE_KEY, v)
  return v
}

/** 恢复默认地址 */
export const resetBaseUrl = () => {
  uni.removeStorageSync(SERVER_STORAGE_KEY)
  return DEFAULT_BASE_URL
}

/**
 * 兼容旧引用：api/request.js 与 api/stream.js 历史上按常量用法 import。
 * 改为函数调用即可（BASE_URL() 返回当前地址）。
 */
export const BASE_URL = getBaseUrl
