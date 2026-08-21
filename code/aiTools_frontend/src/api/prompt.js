import { request } from './request'

// 我的提示词列表（按工具隔离，toolCode 为工具 code）
export const promptListApi = (toolCode) => request({ url: '/api/prompt/list', method: 'GET', data: { toolCode } })

// 新增提示词（promptUse: format 格式 / generate 生成内容；toolCode 所属工具）
export const promptAddApi = (promptText, promptUse, toolCode) => request({ url: '/api/prompt/add', method: 'POST', data: { promptText, promptUse, toolCode } })

// 更新提示词（promptUse: format 格式 / generate 生成内容；toolCode 所属工具）
export const promptUpdateApi = (id, promptText, promptUse, toolCode) => request({ url: `/api/prompt/${id}`, method: 'PUT', data: { promptText, promptUse, toolCode } })

// 删除提示词
export const promptDeleteApi = (id) => request({ url: `/api/prompt/${id}`, method: 'DELETE' })

// 系统提示词列表（按工具场景）
export const systemPromptListApi = (toolCode) => request({
  url: '/api/prompt/system/list',
  method: 'GET',
  data: { toolCode }
})

// 工具列表（按 tool_type 分组，用于提示词管理页工具下拉）
export const toolListApi = () => request({ url: '/api/prompt/tools', method: 'GET' })

/**
 * AI 生成提示词：调后端 DeepSeek 按用户需求生成一段提示词
 * @param {Object} params { toolCode, toolName, toolDesc, promptUse, requirement }
 *   - toolCode: 工具编码（如 work-summary）
 *   - toolName: 工具名称（如 工作总结），从 tools.js 传入，避免后端再查表
 *   - toolDesc: 工具描述（可空）
 *   - promptUse: 用途（format 格式 / generate 生成内容）
 *   - requirement: 用户填的"参考需求"
 * @returns {Promise<{ promptText: string }>}
 */
export const generatePromptApi = (params) => request({
  url: '/api/prompt/generate',
  method: 'POST',
  data: params
})