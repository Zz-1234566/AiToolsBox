import { uploadFile, request } from './request'
import { BASE_URL } from '../config/env'

// ==================== 文件上传 ====================

/**
 * 上传文件
 * @param {String} filePath - 本地文件路径
 * @param {String} prefix - 存储分区前缀（默认 avatar，工具文件传 'file'）
 */
export const uploadFileApi = (filePath, prefix = 'avatar') => {
  return uploadFile('/api/file/upload', filePath, 'file', { prefix })
}

// ==================== B2 多文件批量 AI 处理（轮询方案） ====================
// 流程：batchCreate（POST）拿 batchId → batchCompleted（GET 轮询）拉增量 items → 终态自动停
// 后端端点：
//   POST /api/ai-office/document-summary/batch-upload  → 普通 JSON，返回 { batchId, fileCount }
//   POST /api/ai-office/ocr-recognize/batch-upload     → 同上
//   GET  /api/ai-office/batch/{batchId}/completed?since=N  → 返回 { processedIndex, results[], status }

/**
 * 提交批量任务（同步返回 batchId）
 * 实现策略：
 *   H5：用原生 fetch + FormData 一次提交（同名多 files[]），走 Authorization 头
 *   小程序/APP：uni.uploadFile 不支持同名多值，先循环单文件提交（后端会按 1 文件 1 任务处理多次）
 * @param {String} url - 端点 URL（doc 或 ocr）
 * @param {Array} files - 文件数组 [{ filePath, file }]
 * @param {Object} fields - 附加表单字段 { promptFormat, promptGenerate, promptId }
 * @returns {Promise<{ batchId, fileCount }>}
 */
const batchCreate = (url, files, fields = {}) => {
  return new Promise((resolve, reject) => {
    // 提取原生 File / 临时路径
    const fileItems = files.map((f) => {
      if (f.file && typeof f.file === 'object') return f.file        // H5 原生 File
      if (f.filePath) return f.filePath                              // 小程序/APP 临时路径
      return f
    }).filter(Boolean)
    if (!fileItems.length) {
      reject(new Error('请至少选择 1 个文件'))
      return
    }
    // #ifdef H5
    // H5：用 XMLHttpRequest + FormData 一次提交（XHR 在 H5 下能正确设 multipart boundary + Content-Length，
    //    Spring 能正确读到 MultipartFile.getSize()，避免 fetch + FormData 在某些浏览器下 size=0 的边界 case）
    const formData = new FormData()
    fileItems.forEach((f) => {
      // f 是 H5 原生 File 对象（来自 BatchFilePicker 的 <input type="file">）
      if (typeof f === 'string') {
        // 兜底：H5 给了临时路径（不该发生）
        formData.append('files', new Blob([''], { type: 'application/octet-stream' }), f)
      } else {
        formData.append('files', f, f.name || 'file')
      }
    })
    Object.keys(fields).forEach((k) => {
      if (fields[k] != null && fields[k] !== '') formData.append(k, String(fields[k]))
    })
    const token3 = uni.getStorageSync('token')
    const xhr = new XMLHttpRequest()
    xhr.open('POST', BASE_URL + url, true)
    // 不手动设 Content-Type，让浏览器自动加 multipart boundary
    if (token3) {
      xhr.setRequestHeader('Authorization', 'Bearer ' + token3)
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const data = typeof xhr.responseText === 'string' ? JSON.parse(xhr.responseText) : xhr.responseText
          if (data && data.data && data.data.batchId) {
            resolve({ batchId: data.data.batchId, fileCount: data.data.fileCount })
          } else {
            reject(new Error((data && data.message) || '创建批量任务失败'))
          }
        } catch (e) {
          reject(new Error('响应解析失败: ' + e.message))
        }
      } else if (xhr.status === 401) {
        uni.removeStorageSync('token')
        uni.removeStorageSync('userInfo')
        reject(new Error('登录已过期'))
      } else {
        let msg = '创建批量任务失败 (HTTP ' + xhr.status + ')'
        try {
          const errData = JSON.parse(xhr.responseText)
          if (errData && (errData.message || errData.msg)) msg = errData.message || errData.msg
        } catch (ignore) {}
        reject(new Error(msg))
      }
    }
    xhr.onerror = () => reject(new Error('网络请求失败'))
    xhr.send(formData)
    // #endif
    // #ifndef H5
    // 小程序/APP：单次 uni.uploadFile 不支持同名多值，循环单文件提交后端（后端会拒）
    // 临时方案：仅第 1 个文件生效；多文件场景请用户在 H5 测试
    const token2 = uni.getStorageSync('token')
    const header2 = token2 ? { 'Authorization': 'Bearer ' + token2 } : {}
    const formData2 = { ...fields }
    fileItems.forEach((fp, i) => {
      if (i === 0) formData2.files = fp
    })
    uni.uploadFile({
      url: BASE_URL + url,
      filePath: fileItems[0],
      name: 'files',
      formData: formData2,
      header: header2,
      success: (res) => {
        try {
          const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (data && data.data && data.data.batchId) {
            resolve({ batchId: data.data.batchId, fileCount: data.data.fileCount })
          } else {
            reject(new Error((data && data.message) || '创建批量任务失败'))
          }
        } catch (e) {
          reject(e)
        }
      },
      fail: (err) => reject(err)
    })
    // #endif
  })
}

/**
 * 拉取批量任务增量完成项
 * @param {String} batchId
 * @param {Number} since - 已拉取数（0=全部；N=只返回 N 之后的新 items）
 * @returns {Promise<{ processedIndex, results, status, statusLabel, ... }>}
 */
export const batchCompleted = (batchId, since = 0) => {
  return request({
    url: `/api/ai-office/batch/${batchId}/completed`,
    method: 'GET',
    data: { since }
  }).then((res) => res && res.data ? res.data : null)
}

/**
 * 批量上传多文件（多文件 AI 文档重点提取）— 第 1 步：创建任务拿 batchId
 * @param {Object} options { files, fields } - 见 batchCreate
 * @returns {Promise<{ batchId, fileCount }>}
 */
export const batchUpload = (options) => {
  return batchCreate('/api/ai-office/document-summary/batch-upload', options.files, options.fields)
}

/**
 * 批量上传多图片/PDF（多文件 OCR 智能识别）— 第 1 步：创建任务拿 batchId
 * @param {Object} options { files, fields } - 见 batchCreate
 * @returns {Promise<{ batchId, fileCount }>}
 */
export const ocrBatchUpload = (options) => {
  return batchCreate('/api/ai-office/ocr-recognize/batch-upload', options.files, options.fields)
}

/**
 * 批量上传多文件（AI 文件解读）— 第 1 步：创建任务拿 batchId
 * @param {Object} options { files, fields } - fields 传 { prompt }
 * @returns {Promise<{ batchId, fileCount }>}
 */
export const aiFileReaderBatchUpload = (options) => {
  return batchCreate('/api/ai-office/ai-file-reader/batch-upload', options.files, options.fields)
}
