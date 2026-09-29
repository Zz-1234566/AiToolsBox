import { BASE_URL } from '../config/env'
import { handleAuthError } from '../utils/auth-error-handler'
/**
 * SSE 流式请求（XHR 实现，H5 + App vue 页面通用）
 * @param {Object} options
 *   url - 接口路径
 *   data - POST body 对象
 *   onChunk - 每收到一段普通文本内容回调 (text)
 *   onMarker - 收到 B2 多文件批量 SSE 标记 `--- [任务已创建 batchId=xxx] ---` 时回调 (text)；
 *              文本中括号[]里的内容是规约约定的语义标记（如任务开始/完成/失败）。
 *              不传 onMarker 时，标记仍会通过 onChunk 透传，调用方按需识别。
 *   onDone - 流结束回调
 *   onError - 错误回调 (err)
 */
// 规约：B2 多文件批量 SSE 标记格式 `--- [xxx] ---`（见 AGENTS.md 第 7 节）
const SSE_MARKER_PATTERN = /^---\s*\[(.+?)\]\s*---$/

/**
 * SSE 事件解析器工厂（streamRequest / streamUpload 共用，消除重复解析逻辑）
 *
 * 为什么需要按「事件」而不是按「行」解析：
 *   后端 Spring 的 SseEmitter 会把 chunk 里的换行转义成跨行 data: 帧
 *   （SseEventBuilderImpl.data(Object,MediaType) 内执行 StringUtils.replace("\n", "\ndata:")）。
 *   即 sendChunk("## 会议概要\n") 实际发出的字节是：
 *       data:## 会议概要\n  data:\n  \n
 *   旧实现逐行独立取值并丢弃空 data: 行，导致所有换行丢失 → markdown 无法渲染
 *   （整篇被吞进一个 <h1>），而读历史因绕过 SSE 解析器所以正常。
 *
 * 现按 SSE 规范解析：事件以空行分隔，事件内多个 data: 行用 \n 拼回。
 *
 * @param {Object} options
 *   onChunk  - 普通内容回调 (text)
 *   onMarker - 批量任务标记 `--- [xxx] ---` 回调 (text)
 * @returns {{ feed: (fullText: String, done?: Boolean) => void }}
 *   feed 增量喂入 xhr.responseText（内部按 lastIndex 只解析新增部分，不重复消费）
 */
const createSseEventParser = ({ onChunk, onMarker } = {}) => {
  let buffer = ''      // 已接收但尚未凑齐一个完整事件（未遇到空行）的文本
  let lastIndex = 0    // xhr.responseText 已消费位置

  // 解析单个完整事件块：事件内多行 data: 用 \n 拼回（SSE 规范）
  const emitEvent = (eventText) => {
    const dataLines = []
    for (const rawLine of eventText.split(/\r?\n/)) {
      if (rawLine.startsWith('data:')) {
        // 只去掉 "data:" 后的一个前导空格；其余空格属内容，不可 trim
        dataLines.push(rawLine.slice(5).replace(/^ /, ''))
      }
    }
    if (dataLines.length === 0) return
    const data = dataLines.join('\n')
    if (!data || data === '[DONE]') return
    // 优先识别 B2 多文件批量标记
    const match = data.match(SSE_MARKER_PATTERN)
    if (match) {
      if (onMarker) onMarker(match[1])
      // 不再透传到 onChunk，避免重复渲染
    } else if (onChunk) {
      onChunk(data)
    }
  }

  return {
    feed(fullText, done) {
      buffer += fullText.slice(lastIndex)
      lastIndex = fullText.length

      // SSE 事件以空行（\n\n，兼容 \r\n\r\n）分隔
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() // 最后一段可能不完整，留待下次
      for (const ev of events) emitEvent(ev)

      // 流结束：残留 buffer 直接解析（最后一段可能没有空行结尾）
      if (done) {
        if (buffer) emitEvent(buffer)
        buffer = ''
      }
    }
  }
}

export const streamRequest = (options) => {
  const token = uni.getStorageSync('token')

  // 共用 SSE 事件解析器（增量解析 xhr.responseText，按事件拼回换行）
  const parser = createSseEventParser({ onChunk: options.onChunk, onMarker: options.onMarker })

  const xhr = new XMLHttpRequest()
  xhr.open('POST', BASE_URL + options.url, true)
  xhr.setRequestHeader('Content-Type', 'application/json')
  if (token) {
    xhr.setRequestHeader('Authorization', 'Bearer ' + token)
  }

  xhr.onprogress = () => {
    // 每收到一段数据，增量解析 SSE 事件（不重复处理）
    parser.feed(xhr.responseText)
  }

  xhr.onload = () => {
    // 非 2xx 按错误处理（响应体是错误 JSON，不是 SSE 流）
    if (xhr.status >= 400) {
      // 401：token 过期，清除登录态并跳登录
      if (xhr.status === 401) {
        // P2-B11: 统一到 utils/auth-error-handler.js
        handleAuthError('登录已过期，请重新登录')
        if (options.onError) options.onError(new Error('未登录'))
        return
      }
      if (options.onError) options.onError(new Error('请求失败（' + xhr.status + '）'))
      return
    }
    // 流结束，处理最后一段未以空行结尾的数据
    parser.feed(xhr.responseText, true)
    if (options.onDone) options.onDone()
  }

  xhr.onerror = (err) => {
    if (options.onError) options.onError(err)
  }

  xhr.send(JSON.stringify(options.data || {}))
}

/**
 * SSE 流式上传（multipart/form-data + XHR 增量读响应流，H5 端兼容）
 * 用于文档重点提取等需要"上传文件 + 流式接收"的接口。
 * 区别 streamRequest：本函数用 FormData 携带文件（非 JSON body），
 * 上传完成后通过 onprogress 增量读取服务端 SSE 响应，不影响持久的 JSON 流式调用。
 * @param {Object} options
 *   url - 接口路径
 *   file - 文件：File/Blob 对象（H5 端推荐，保留真实文件名），或 H5 临时路径（blob:/http:/data:）
 *   fields - 附加表单字段 { key: value }（如 promptFormat / promptGenerate / promptId）
 *   onChunk - 每收到一段内容回调 (text)
 *   onDone - 流结束回调
 *   onError - 错误回调 (err)
 */
export const streamUpload = (options) => {
  const token = uni.getStorageSync('token')

  // 共用 SSE 事件解析器（与 streamRequest 同一套，避免解析逻辑重复漂移）
  const parser = createSseEventParser({ onChunk: options.onChunk, onMarker: options.onMarker })

  // 把文件统一转成可 append 进 FormData 的 Blob（H5 端）
  // - File/Blob 对象：直接使用，保留真实文件名
  // - 字符串（H5 chooseImage 的 blob:/http:/data: 临时路径）：fetch 成 Blob
  const resolveFileBlob = (file) => {
    if (typeof Blob !== 'undefined' && file instanceof Blob) {
      return Promise.resolve({ blob: file, name: file.name || 'document' })
    }
    if (typeof file === 'string') {
      return fetch(file)
        .then((res) => res.blob())
        .then((blob) => ({ blob, name: blob.name || 'document' }))
    }
    return Promise.reject(new Error('无效的文件对象'))
  }

  // 兼容单文件：options.file 直接用；多文件：options.files 数组
  const fileList = options.files
    ? options.files
    : (options.file != null ? [options.file] : [])

  Promise.all(fileList.map(resolveFileBlob))
    .then((resolved) => {
      const formData = new FormData()
      // 多文件：field name 用 "files"（后端 @RequestParam("files") 接收）；单文件用 "file"
      const fieldName = options.files ? 'files' : 'file'
      resolved.forEach(({ blob, name }) => {
        formData.append(fieldName, blob, name)
      })
      const fields = options.fields || {}
      for (const key in fields) {
        const val = fields[key]
        if (val != null && val !== '') {
          formData.append(key, String(val))
        }
      }

      const xhr = new XMLHttpRequest()
      xhr.open('POST', BASE_URL + options.url, true)
      // 不手动设置 Content-Type，让浏览器用 multipart boundary 自动生成
      if (token) {
        xhr.setRequestHeader('Authorization', 'Bearer ' + token)
      }

      xhr.onprogress = () => {
        // 上传完成后的响应流：每收到一段数据，增量解析 SSE 事件
        parser.feed(xhr.responseText)
      }

      xhr.onload = () => {
        if (xhr.status >= 400) {
          if (xhr.status === 401) {
            // P2-B11: 统一到 utils/auth-error-handler.js
            handleAuthError('登录已过期，请重新登录')
            if (options.onError) options.onError(new Error('未登录'))
            return
          }
          // 解析后端 message，弹窗给用户看
          let msg = '请求失败（' + xhr.status + '）'
          try {
            const body = JSON.parse(xhr.responseText || '{}')
            if (body && (body.message || body.msg)) msg = body.message || body.msg
            // Result 结构 { code, message, data }，取 message
          } catch (e) { /* 解析失败就用默认 */ }
          if (options.onError) options.onError(new Error(msg))
          return
        }
        parser.feed(xhr.responseText, true)
        if (options.onDone) options.onDone()
      }

      xhr.onerror = (err) => {
        if (options.onError) options.onError(err)
      }

      // xhr 完整发送后再 attach load 监听（保持原有行为）
      xhr.send(formData)
    })
    .catch((err) => {
      if (options.onError) options.onError(err)
    })
}
