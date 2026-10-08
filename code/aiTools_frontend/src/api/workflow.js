import { request } from './request'

/**
 * 工作流 API（对应后端 /api/workflow）。
 * 鉴权走 request.js 统一注入的 Authorization: Bearer。
 */

/** 保存工作流（workflowId 为空 = 新建，否则覆盖） */
export const workflowSaveApi = ({ workflowId, name, description, nodes }) =>
  request({
    url: '/api/workflow/save',
    method: 'POST',
    data: { workflowId, name, description, nodes: JSON.stringify(nodes || []) }
  })

/** 工作流列表 */
export const workflowListApi = () => request({ url: '/api/workflow/list', method: 'GET' })

/** 工作流详情 */
export const workflowDetailApi = (workflowId) =>
  request({ url: `/api/workflow/${workflowId}`, method: 'GET' })

/** 删除工作流 */
export const workflowDeleteApi = (workflowId) =>
  request({ url: `/api/workflow/${workflowId}`, method: 'DELETE' })

/**
 * 运行工作流
 * @param {String} workflowId
 * @param {Object} inputs  nodeId → 输入数组（文本内容 或 文件路径/dataURL）
 */
export const workflowRunApi = (workflowId, inputs) =>
  request({
    url: `/api/workflow/${workflowId}/run`,
    method: 'POST',
    data: { inputs: inputs || {} }
  })

/** 运行历史列表（可按 workflowId 过滤） */
export const workflowRunsApi = (workflowId, limit = 20) =>
  request({
    url: '/api/workflow/runs',
    method: 'GET',
    data: { workflowId, limit }
  })

/** 运行历史详情 */
export const workflowRunDetailApi = (runId) =>
  request({ url: `/api/workflow/runs/${runId}`, method: 'GET' })
