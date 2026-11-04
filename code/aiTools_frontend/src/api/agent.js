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
export const uploadAgentFile = (filePath) => {
  return uploadFile('/api/file/upload', filePath, 'file', { prefix: 'file' })
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
