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

/**
 * 批量上传多录音（多文件录音转写）— 第 1 步：创建任务拿 batchId
 * @param {Object} options { files, fields } — 录音文件数组；fields 可传 { engine }
 * @returns {Promise<{ batchId, fileCount }>}
 */
export const audioBatchUpload = (options) => {
  return batchCreate('/api/ai-office/meeting-minutes/batch-transcribe', options.files, options.fields)
}

/**
 * 录音转文本（上传音频文件 → 后端 ASR → 返回识别文字）
 * 兼容 H5 + App：
 *   H5 端：filePath 是 dataURL 字符串（data:audio/...;base64,...），用 fetch + FormData
 *   App 端：filePath 是临时文件路径，用 uni.uploadFile
 * @param {String|Blob|File} filePath
 * @param {Object} [opts]
 * @param {String} [opts.fileName] - H5 端传 Blob 时需要的文件名（从 dataURL 解析或外部传入）
 * @param {String} [opts.engine]   - 转写引擎 minimax | tencent，不传则用后端默认值
 */
export const transcribeMeeting = (filePath, opts = {}) => {
  // #ifdef H5
  // H5 端：filePath 可能是 dataURL 字符串或 File/Blob 对象
  return transcribeMeetingH5(filePath, opts)
  // #endif
  // #ifndef H5
  return uploadFile('/api/ai-office/meeting-minutes/transcribe', filePath, 'file',
    opts.engine ? { engine: opts.engine } : {})
  // #endif
}

// #ifdef H5
// H5 端专用：dataURL / Blob / File → multipart/form-data → fetch
async function transcribeMeetingH5(filePath, opts = {}) {
  const token = uni.getStorageSync('token')
  const headers = {}
  if (token) headers.Authorization = 'Bearer ' + token

  let blob, fileName
  if (typeof filePath === 'string' && filePath.startsWith('data:')) {
    // dataURL → Blob
    const [meta, b64] = filePath.split(',')
    const mimeMatch = meta.match(/data:([^;]+)/)
    const mime = mimeMatch ? mimeMatch[1] : 'audio/mpeg'
    const bin = atob(b64)
    const arr = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i)
    blob = new Blob([arr], { type: mime })
    fileName = opts.fileName || ('recording.' + (mime.split('/')[1] || 'mp3'))
  } else if (filePath instanceof Blob) {
    blob = filePath
    fileName = (filePath.name) || opts.fileName || 'recording.audio'
  } else {
    throw new Error('H5 端 transcribeMeeting 需要 dataURL 或 Blob')
  }

  const fd = new FormData()
  fd.append('file', blob, fileName)
  // 转写引擎：minimax | tencent。不传则后端用 asr.engine 配置值（默认 minimax）
  if (opts.engine) {
    fd.append('engine', opts.engine)
  }

  const res = await fetch((typeof BASE_URL !== 'undefined' ? BASE_URL : '') + '/api/ai-office/meeting-minutes/transcribe', {
    method: 'POST',
    headers,
    body: fd
  })
  const json = await res.json()
  if (!res.ok) {
    const e = new Error(json.msg || '上传失败')
    e.data = json
    throw e
  }
  return json
}
// #endif

/**
 * 会议纪要 AI 路由决策：判断本次会议内容适合 SSE 流式还是 JSON 结构化输出
 * 后端先调 AI 决策 + 关键词兜底，返回 'sse' 或 'json'
 * @param {Object} data { content, promptFormat, promptGenerate, promptId }
 * @returns {Promise<String>} 'sse' | 'json'
 */
export const meetingMinutesDecideRoute = (data) => {
  return request({
    url: '/api/ai-office/meeting-minutes/decide-route',
    method: 'POST',
    data
  }).then((res) => (res && res.data) || 'sse')
}

/**
 * 会议纪要 JSON 同步响应：调 json-single handler，等 AI 完全返回后一次性拿到结构化 JSON
 * @param {Object} data { content, promptFormat, promptGenerate, promptId }
 * @returns {Promise<{ code: number, data: string }>}
 */
export const meetingMinutesJson = (data) => {
  return request({
    url: '/api/ai-office/meeting-minutes/json',
    method: 'POST',
    data
  })
}
