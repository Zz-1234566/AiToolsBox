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
