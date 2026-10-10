/**
 * 工作流「本次运行」的入参暂存（运行页与列表页之间的单例传递通道）。
 *
 * 为什么不用 query 传 inputs：
 *   inputs 里装的是上传后返回的文件地址（带签名/时间戳，单条就可能几百字符）
 *   + 用户输入的文本（单节点最多 5000 字）。uni.navigateTo 的 query 在
 *   H5 走 URL（长度受限、中文需 encodeURIComponent、文件 URL 常含 & 与 =），
 *   在 App 走原生参数也有长度上限，多文件时极易被截断或转义坏。
 *   内容量不可控 → 走单例；query 只传稳定的 workflowId。
 *   生命周期：写页 navigateTo 前 setTarget()，运行页 onLoad 里 take()（读取即清空，
 *   避免下次进页面拿到上一次的旧输入）。
 */

let pending = null

/**
 * 暂存本次运行的入参
 * @param {Object} payload { workflowId, name, description, inputs, inputFileNames }
 */
export function setWorkflowRunTarget(payload) {
  pending = payload
}

/**
 * 取出并清空暂存的入参（运行页 onLoad 调用）
 * @returns {Object|null}
 */
export function takeWorkflowRunTarget() {
  const p = pending
  pending = null
  return p
}
