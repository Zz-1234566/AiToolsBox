import { uploadFile, uploadWithFormData, request } from './request'
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
    // 提取可上传的文件条目。
    // 三种来源：
    //   - H5 原生 File 对象：{ file } → 直接用（下方 H5 分支走 FormData）
    //   - App/小程序 临时路径字符串：{ filePath } → 直接用
    //   - App/小程序 选文件产生的对象：{ fileName, path, size } → 保留原对象，
    //     其 path 字段即本地路径（App runtime 的 files 数组需要 uri）
    const fileItems = files.map((f) => {
      if (f && f.file && typeof f.file === 'object') return f.file        // H5 原生 File
      if (typeof f === 'string') return f                                // 直接就是路径
      if (f && f.filePath) return f.filePath                            // { filePath } 形态
      return f                                                        // { fileName, path, size } 形态
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
    xhr.open('POST', BASE_URL() + url, true)
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
    // 平台差异（官方《uni.uploadFile》原文）：
    //   「App支持多文件上传，微信小程序只支持单文件上传，传多个文件需要反复调用本API」
    //   files 参数的平台差异说明也只列了 App 与 H5。
    //
    // ① App：走 files 数组，一次请求把全部文件作为**多个同名 files 字段**提交。
    //    runtime 实现（uni.runtime.esm.js）：
    //      files.forEach(f => uploader.addFile(getRealPath(f.uri), { key: f.name || 'file' }))
    //    → key 即 multipart 字段名，必须统一设为 'files'，
    //      才能被后端 @RequestParam("files") List<MultipartFile> 收成列表。
    //      （已实测：3 个同名 files 字段 → 后端 fileCount=3）
    // ② 小程序：uploadFile 不支持 files 数组，且小程序端暂未开通 → 见下方分支。

    // #ifdef APP-PLUS
    const token2 = uni.getStorageSync('token')
    const header2 = token2 ? { 'Authorization': 'Bearer ' + token2 } : {}
    const uploadFiles = fileItems.map((f) => {
      const uri = (f && (f.uri || f.path || f.filePath)) || ''
      return { uri: uri, name: 'files' }
    }).filter((f) => f.uri)
    if (!uploadFiles.length) {
      reject(new Error('没有可上传的文件'))
      return
    }
    uni.uploadFile({
      url: BASE_URL() + url,
      files: uploadFiles,
      formData: { ...fields },
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
    return
    // #endif

    // #ifndef APP-PLUS
    // ─────────────────────────────────────────────────────────────
    // 小程序端：暂未开通，代码保留占位。
    //
    // 【平台限制】微信小程序 wx.uploadFile 只有 filePath（单文件），
    //   无 files 数组 → 一次请求只能传 1 个文件。
    //   （官方《uni.uploadFile》：「微信小程序只支持单文件上传，
    //     传多个文件需要反复调用本API」）
    //
    // 【为何不实现「循环调用 uploadFile」】
    //   后端一次 HTTP 请求 = 建一个 batchId = 立即跑批
    //   （见 AiOfficeToolController#batchDocumentUpload）。
    //   循环 N 次 → 会建 N 个任务、跑 N 遍 AI、产生 N 个 batchId，
    //   前端需要同时轮询 N 个任务并合并结果（多游标 + 聚合器），
    //   且并发占用线程池 N 倍（core=8，N 大时可能被 AbortPolicy 拒绝）、
    //   结果顺序不保证。要真正支持需后端加「分片追加」端点
    //   （建任务不跑 → 逐个追加 → 触发执行），属后端状态机改造。
    //
    // 【当前策略】小程序端统一提示「暂未开通」，不做半吊子实现，
    //   避免用户以为文件已上传、后端却只收到部分。
    //   待小程序端真正启用时，按上述「分片追加」方案统一实现，
    //   App / H5 端不受影响（已用 files 数组 / FormData 一次传全）。
    // ─────────────────────────────────────────────────────────────
    uni.showModal({
      title: '小程序端暂未开通',
      content: '当前小程序端尚未开通文件上传能力，请使用 App 端或 H5 端。',
      showCancel: false,
      confirmText: '知道了'
    })
    reject(new Error('小程序端暂未开通文件上传'))
    // #endif
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

  const res = await fetch(BASE_URL() + '/api/ai-office/meeting-minutes/transcribe', {
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
