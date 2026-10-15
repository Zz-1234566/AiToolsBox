import { uploadFile, request } from './request'

/**
 * 图片背景去除 / 换底色 / 换背景图 API
 *
 * 后端端点：
 *   POST /api/ai-office/image-bg-remover
 *     入参：files (multipart, 1-10)、mode (transparent/color/image)、backgroundColor (color 模式)、background (image 模式)
 *     出参：Result<{ batchId, fileCount }>
 *   GET  /api/ai-office/batch/{batchId}/completed?since=N
 *     出参：Result<{ status, processedIndex, results[], fileCount, successCount, failCount, statusLabel, ... }>
 *     状态码：0=待处理 1=处理中 2=已完成 3=部分失败 4=全部失败
 *
 * 注意：image-bg-remover 当前 resultSummary 写死为 "[]"，所以前端从 results 里拿不到 fileUrl；
 *       产物实际落库到 sys_tool_output 表（toolCode='image-bg-remover'），但暂无按 batchId 查 fileUrl 的接口。
 *       本模块在 results 里发现 fileUrl/output 仍兼容解析。
 */

/**
 * 提交图片去背景 / 换底色 / 换背景图任务
 * @param {String|Blob|File} filePath - 本地路径（H5 可为 File/Blob）
 * @param {Object} [opts]
 * @param {String} [opts.mode] - transparent | color | image，默认 transparent（去背景 → 透明 PNG）
 * @param {String} [opts.backgroundColor] - color 模式必传：red | blue | white
 * @returns {Promise<{ code, data: { batchId, fileCount } }>}
 */
export const imageBgRemover = (filePath, opts = {}) => {
  const formData = {}
  if (opts.mode) formData.mode = opts.mode
  if (opts.backgroundColor) formData.backgroundColor = opts.backgroundColor
  return uploadFile('/api/ai-office/image-bg-remover', filePath, 'files', formData)
}

/**
 * 轮询批量任务增量结果
 * @param {String} batchId
 * @param {Number} [since=0] - 已收到的 items 数；0 表示全量
 * @returns {Promise<{ code, data: BatchStatusVO }>}
 */
export const pollBatchCompleted = (batchId, since = 0) => {
  return request({
    url: `/api/ai-office/batch/${batchId}/completed`,
    method: 'GET',
    data: { since }
  })
}
