// AI 模型 BYOK API 封装（前端）
// - 列表：GET /api/user-ai-model/list
// - 保存：POST /api/user-ai-model/save
// - 删除：DELETE /api/user-ai-model/{id}
// - 设默认：POST /api/user-ai-model/set-default/{id}
// - 测试连通：GET /api/user-ai-model/test/{id}  （后端如有则调用）
// - 系统模型（管理员可见）：GET /api/ai-model/system  （若后端有）
//
// ⚠️ API Key 永远只传给后端保存，前端绝不复显。
//    列表返回的 DTO 不含明文 Key，只有 apiKeyPrefix（前 4 位）。
import { request } from './request'

/**
 * 当前用户已加的 AI 模型列表（含自己设的默认 + 系统默认混排，取决于后端）。
 * @returns {Promise<Array<{id, modelCode, nickname, capability, isDefault, apiKeyPrefix, status}>>}
 */
export const listUserAiModels = () => {
  return request({ url: '/api/user-ai-model/list', method: 'GET' })
}

/**
 * 添加 / 更新一个用户 AI 模型（BYOK）。
 * @param {Object} payload { modelCode, apiKey, apiUrl?, apiModel?, nickname?, capability, isDefault? }
 */
export const saveUserAiModel = (payload) => {
  return request({
    url: '/api/user-ai-model/save',
    method: 'POST',
    data: payload,
  })
}

/**
 * 软删一个用户 AI 模型。
 */
export const deleteUserAiModel = (id) => {
  return request({ url: `/api/user-ai-model/${id}`, method: 'DELETE' })
}

/**
 * 设为默认（按 capability 同组内互斥）。
 */
export const setDefaultUserAiModel = (id) => {
  return request({ url: `/api/user-ai-model/set-default/${id}`, method: 'POST' })
}

/**
 * 工具 → 必需 capability 映射（前端默认映射，后端仍会再校验一次）。
 * id-photo-bg-change 不调 LLM，不返回 capability。
 */
export const TOOL_CAPABILITY_MAP = {
  'work-summary': 'text',
  'weekly-report': 'text',
  'meeting-minutes': 'text',
  'doc-keypoint-extract': 'text',
  'document-summary': 'text',
  'audio-transcribe': 'asr',
  'ocr-recognize': 'ocr',
  'ocr-recognition': 'ocr',
  'id-photo-bg-change': null,   // ONNX 分割，不调 LLM
}

/**
 * 给定 toolId，过滤出可用模型列表。
 * @param {Array} models  listUserAiModels() 返回的数组
 * @param {String} toolId
 * @returns {Array}       过滤后的模型（含系统默认兜底）
 */
export const filterModelsByTool = (models, toolId) => {
  const required = TOOL_CAPABILITY_MAP[toolId]
  if (!required) return []   // 该工具不调 LLM
  if (!Array.isArray(models)) return []
  return models.filter((m) => m && m.capability === required && (m.status === 1 || m.status === undefined))
}
