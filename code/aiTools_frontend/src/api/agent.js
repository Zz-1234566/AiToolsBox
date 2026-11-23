import { streamRequest } from './stream'
import { uploadFile, request } from './request'

/**
 * 智能体 API。
 *
 * 后端端点：
 *   POST /api/agent/run
 *     入参（JSON body）：{ goal, modelCode, files: [{name, url}] }
 *     出参：SSE 流
 *       --- [思考:xxx] ---        模型这一步在想什么
 *       --- [工具:xxx 耗时 123ms] ---   开始调用某个工具
 *       （普通 chunk）              工具结果正文 / 最终答案正文
 *       --- [工具结果结束:xxx] --- 工具结果结束
 *       --- [答案] ---             之后的普通 chunk 是最终答案
 *       --- [完成] ---             全部结束
 *       --- [ERROR:文案] ---       出错（前端按 error 回调处理）
 *
 *   POST /api/file/upload
 *     附件上传（multipart），返回 { fileUrl, fileName }。
 *     附件**先上传拿 URL**再传给智能体 —— SSE 走 JSON POST，带不了 multipart。
 */

/**
 * 跑一次智能体（SSE 流式）
 * @param {Object} payload { goal, modelCode, files }
 * @param {Object} handlers { onChunk, onMarker, onDone, onError }
 * @returns {Function} 取消函数
 */
export const agentRun = (payload, handlers = {}) => {
  return streamRequest({
    url: '/api/agent/run',
    data: payload,
    onChunk: handlers.onChunk,
    onMarker: handlers.onMarker,
    onDone: handlers.onDone,
    onError: handlers.onError
  })
}

/**
 * 上传智能体附件
 * @param {String|Blob|File} filePath 本地路径 / File
 * @returns {Promise<{ code, data: { fileUrl, fileName } }>}
 */
export const uploadAgentFile = (picked) => {
  // pickFiles 返回的是归一化对象 { fileName, file, path, size }：
  //   - H5 端：原生 File 在 file 字段，path 可能为空串
  //   - App / 小程序端：本地路径在 path 字段，file 为 null
  // 直接把对象传给 uni.uploadFile 会命中其内部的 filePath.indexOf() → TypeError，
  // 表现为「附件上传失败」且后端收不到请求。
  const target = (picked && typeof picked === 'object' && ('file' in picked || 'path' in picked))
    ? (picked.file || picked.path)
    : picked
  return uploadFile('/api/file/upload', target, 'file', { prefix: 'file' })
}

/**
 * 我最近的智能体运行记录
 * @param {Number} [limit=20]
 * @returns {Promise<{ code, data: Array<AgentRunVO> }>}
 */
export const listAgentRuns = (limit = 20) => {
  return request({
    url: '/api/agent/runs',
    method: 'GET',
    data: { limit }
  })
}

/**
 * 运行详情（含步骤明细）
 * @param {String} runId
 * @returns {Promise<{ code, data: AgentRunVO }>}
 */
export const getAgentRun = (runId) => {
  return request({
    url: `/api/agent/runs/${runId}`,
    method: 'GET'
  })
}

/**
 * 取消一次正在运行的智能体
 * @param {String} runId 运行 ID（运行首帧下发）
 * @returns {Promise<{ code, data: true }>}
 */
export const cancelAgentRun = (runId) => {
  return request({
    url: '/api/agent/cancel',
    method: 'POST',
    data: { runId }
  })
}

/* ==================== 会话 ==================== */

/** 最近会话列表 */
export const listAgentSessions = (limit = 20) =>
  request({ url: '/api/agent/sessions', method: 'GET', data: { limit } })

/** 新建会话 */
export const createAgentSession = () =>
  request({ url: '/api/agent/sessions', method: 'POST' })

/** 会话消息回放 */
export const listAgentMessages = (sessionId, limit = 200) =>
  request({ url: `/api/agent/sessions/${sessionId}/messages`, method: 'GET', data: { limit } })

/** 重命名会话 */
export const renameAgentSession = (sessionId, title) =>
  request({ url: `/api/agent/sessions/${sessionId}`, method: 'PATCH', data: { title } })

/** 删除会话 */
export const deleteAgentSession = (sessionId) =>
  request({ url: `/api/agent/sessions/${sessionId}`, method: 'DELETE' })
