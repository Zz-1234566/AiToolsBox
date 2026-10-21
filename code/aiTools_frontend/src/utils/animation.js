/**
 * 跨端动画调度（requestAnimationFrame 的安全封装）。
 *
 * 【为什么需要】
 * uni-app 官方明确：App 端与小程序端的 js 运行在 jscore 下，**没有浏览器专有对象**。
 * requestAnimationFrame 属于浏览器 API，在 App **逻辑层**不存在
 * （实测 node_modules/@dcloudio/uni-app-plus/dist/ 中 requestAnimationFrame
 * 只出现在 uni-app-view.umd.js —— 那是视图层 webview，逻辑层拿不到）。
 *
 * 直接用 rAF 的后果：App 端推字第一帧就抛 ReferenceError，
 * 流式打字机完全不可用，且因为异常发生在回调里，控制台往往只有一句
 * "requestAnimationFrame is not defined"，很难定位。
 *
 * 【策略】
 * - 有 rAF（H5 / App 视图层）：用 rAF，保证 60fps 与浏览器刷新同步；
 * - 无 rAF（App 逻辑层）：降级为 setTimeout（≈16ms），观感接近但不同步；
 * - 统一返回 id / 取消接口，调用方无需关心底层用的是哪个。
 */

/** 是否有原生 rAF */
const hasRaf = typeof requestAnimationFrame === 'function' && typeof cancelAnimationFrame === 'function'

/**
 * 申请一帧回调。
 * @param {Function} cb 帧回调
 * @returns {Number} 调度 id（可用于取消）
 */
export const requestFrame = (cb) => {
  if (hasRaf) {
    return requestAnimationFrame(cb)
  }
  // 降级：16ms ≈ 60fps
  return setTimeout(() => cb(Date.now()), 16)
}

/**
 * 取消已申请的帧回调。
 * @param {Number} id requestFrame 返回的 id
 */
export const cancelFrame = (id) => {
  if (id === null || id === undefined) return
  if (hasRaf) {
    cancelAnimationFrame(id)
    return
  }
  clearTimeout(id)
}

/** 当前环境是否支持原生 rAF（供调用方做能力判断） */
export const hasNativeRaf = () => hasRaf
