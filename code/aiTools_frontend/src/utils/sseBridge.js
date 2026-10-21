/**
 * App 端 SSE 传输桥（逻辑层 ↔ 视图层 renderjs）。
 *
 * 【为什么需要这个文件】
 * App 端 uni-app 是「逻辑层(jscore) / 视图层(webview)」分离架构：
 * - 逻辑层**没有** XMLHttpRequest（官方明确 js 运行在 jscore 下，无浏览器专有对象）
 *   → SSE 请求必须由视图层发起
 * - 视图层**不能**使用 uni API、**不能**直接访问逻辑层数据
 *   → 请求参数（url / token / body）必须由逻辑层传过去
 * - renderjs 只能内联在组件里、且 `callMethod` 只能调用逻辑层 Options API 的 methods
 *   → 视图层组件需通过本文件与逻辑层握手
 *
 * 本文件是**双向通道的唯一实现**，两端各引一次：
 * - 逻辑层（api/stream.js）：registerSseEmitter / setSseCallbacks / emitSseEvent
 * - 视图层组件（components/sse-renderjs）：注册自身 + 上报数据
 *
 * H5 端**完全不使用**本文件（走原生 XHR，无跨层通信）。
 */

/**
 * 已注册的视图层组件**栈**（后注册的在前）。
 *
 * 为什么用栈而不是单个变量：uni-app 的 navigateTo 会把上一页保留在页面栈中，
 * 它的 sse-renderjs 组件**不会卸载**。若只存「最后一个注册者」，
 * 从 workflow-run 返回 tool-common 时（workflow-run 的组件被销毁、tool-common 的不会），
 * 注册表会指向已销毁的实例 → tool-common 的流式请求直接报「组件未就绪」。
 * 栈可以在销毁时把上一位恢复回来。
 */
const emitterStack = []

/**
 * 视图层组件注册自己（组件 mounted 时调用）。
 * @param {Object} inst 组件实例（需提供 start / abort 方法）
 */
export const registerSseEmitter = (inst) => {
  if (!inst) return
  // 同一实例重复注册时先移除旧的，避免栈里出现同一实例多次
  const idx = emitterStack.indexOf(inst)
  if (idx >= 0) emitterStack.splice(idx, 1)
  emitterStack.push(inst)
}

/**
 * 视图层组件注销自己（组件卸载时调用）。
 * @param {Object} [inst] 要移除的实例；不传则移除栈顶
 */
export const unregisterSseEmitter = (inst) => {
  if (inst) {
    const idx = emitterStack.indexOf(inst)
    if (idx >= 0) emitterStack.splice(idx, 1)
    return
  }
  emitterStack.pop()
}

/** 取当前生效的视图层组件实例；未就绪返回 null */
export const getSseEmitter = () => {
  return emitterStack.length ? emitterStack[emitterStack.length - 1] : null
}

/** reqId → 逻辑层回调集合（视图层按 reqId 精确回传，避免并发串流） */
const pendingCallbacks = new Map()

/**
 * 登记一次请求的回调（逻辑层发起请求前调用）。
 * @param {String} reqId 请求唯一标识
 * @param {Object} cbs   { onData, onDone, onError }
 */
export const setSseCallbacks = (reqId, cbs) => {
  pendingCallbacks.set(reqId, cbs)
}

/**
 * 清除某次请求的回调（请求结束/取消后调用，避免 Map 泄漏）。
 * @param {String} reqId
 */
export const clearSseCallbacks = (reqId) => {
  pendingCallbacks.delete(reqId)
}

/**
 * 视图层 → 逻辑层：上报一个事件（视图层组件调用）。
 * @param {String} reqId
 * @param {String} type  'data' | 'done' | 'error'
 * @param {*}      payload
 */
export const emitSseEvent = (reqId, type, payload) => {
  const cbs = pendingCallbacks.get(reqId)
  if (!cbs) return
  if (type === 'data') {
    if (cbs.onData) cbs.onData(payload)
    return
  }
  // done / error 属终态，回调后立即清理
  if (type === 'done') {
    if (cbs.onDone) cbs.onDone(payload)
  } else if (type === 'error') {
    if (cbs.onError) cbs.onError(payload)
  }
  pendingCallbacks.delete(reqId)
}
