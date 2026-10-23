import { request } from './request'
import { REALIZED_TOOLS } from '@/config/tools'

// 我的提示词列表（按工具隔离，toolCode 为工具 code）
export const promptListApi = (toolCode) => request({ url: '/api/prompt/list', method: 'GET', data: { toolCode } })

// 新增提示词（promptUse: format 格式 / generate 生成内容；toolCode 所属工具）
export const promptAddApi = (promptText, promptUse, toolCode, promptName) => request({ url: '/api/prompt/add', method: 'POST', data: { promptText, promptUse, toolCode, promptName } })

// 更新提示词（promptUse: format 格式 / generate 生成内容；toolCode 所属工具）
export const promptUpdateApi = (id, promptText, promptUse, toolCode, promptName) => request({ url: `/api/prompt/${id}`, method: 'PUT', data: { promptText, promptUse, toolCode, promptName } })

// 删除提示词
export const promptDeleteApi = (id) => request({ url: `/api/prompt/${id}`, method: 'DELETE' })

// 系统提示词列表（按工具场景）
export const systemPromptListApi = (toolCode) => request({
  url: '/api/prompt/system/list',
  method: 'GET',
  data: { toolCode }
})

// ===== 系统提示词管理（仅管理员，后端二次校验角色）=====

// 新增系统提示词
export const systemPromptAddApi = (promptText, promptUse, toolCode, promptName) => request({
  url: '/api/prompt/system/add',
  method: 'POST',
  data: { promptText, promptUse, toolCode, promptName }
})

// 修改系统提示词
export const systemPromptUpdateApi = (id, promptText, promptUse, toolCode, promptName) => request({
  url: `/api/prompt/system/${id}`,
  method: 'PUT',
  data: { promptText, promptUse, toolCode, promptName }
})

// 删除系统提示词
export const systemPromptDeleteApi = (id) => request({ url: `/api/prompt/system/${id}`, method: 'DELETE' })

// 工具列表（按 tool_type 分组，用于提示词管理页工具下拉）
export const toolListApi = () => request({ url: '/api/prompt/tools', method: 'GET' })

/**
 * 「已实现」的工具列表 —— **工作流节点下拉专用**。
 *
 * <p>后端 /api/prompt/tools 只按 `status=1` + `dr=0` 过滤。若旧库漏跑下线脚本
 * （init.sql 的 23.5 段 / migrate_remove_tools.sql），会返回一批已下线工具，
 * 用户在工作流里选中它们 → 节点执行必然失败（「该工具暂未接入」）。
 *
 * <p>这里再按前端 {@code config/tools.js} 的已实现清单过滤一层兜底：
 * 前端拿不到元信息的工具，节点也渲染不出来，不该出现在下拉里。
 *
 * <p>⚠️ 提示词管理页（prompt-list.vue）**不要**用这个 —— 那里需要看到
 * 已下线工具的历史提示词，用原版 {@link toolListApi}。
 *
 * @returns {Promise<Object>} 结构与 request 相同，仅 data 被过滤
 */
export const realizedToolListApi = async () => {
  const res = await toolListApi()
  const list = (res && res.data) || []
  return { ...res, data: list.filter((t) => REALIZED_TOOLS.includes(t.toolCode)) }
}

/**
 * AI 生成提示词：调后端文本模型按用户需求生成一段提示词
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