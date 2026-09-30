// =========================================================
// 页面转场工具
// 方案A：Tab 切换用淡入，普通页面跳转用原生转场
// =========================================================

// 跳转 Tab（统一入口，后续可扩展）
export function switchTab(url) {
  uni.switchTab({ url })
}

/**
 * 安全返回上一页。
 *
 * 背景：直接访问 / 刷新某个非首页页面时，页面栈里只有当前页，
 *       uni.navigateBack() 会静默失败（表现为「点返回没反应，卡在当前页」）。
 *
 * 策略：栈内有上一页 → navigateBack；否则 reLaunch 到指定兜底页。
 *
 * @param {String} fallbackUrl 无上一页时跳转的兜底页面，默认工作流列表
 * @param {Object} options     透传给 navigateBack 的参数（如 { delta: 2 }）
 */
export function safeBack(fallbackUrl = '/pages/workflow', options = { delta: 1 }) {
  let stackLen = 0
  try {
    stackLen = (typeof getCurrentPages === 'function' ? getCurrentPages().length : 0)
  } catch (e) {
    stackLen = 0
  }
  if (stackLen > 1) {
    uni.navigateBack(options)
  } else {
    uni.reLaunch({ url: fallbackUrl })
  }
}
