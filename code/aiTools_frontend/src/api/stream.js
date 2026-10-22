import { BASE_URL } from '../config/env'
import { handleAuthError } from '../utils/auth-error-handler'
import { getSseEmitter, setSseCallbacks } from '../utils/sseBridge'

// 规约：B2 多文件批量 SSE 标记格式 `--- [xxx] ---`（见 AGENTS.md 第 7 节）
const SSE_MARKER_PATTERN = /^---\s*\[(.+?)\]\s*---$/

/**
 * SSE 事件解析器工厂（streamRequest 使用，消除重复解析逻辑）
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
 *   onError  - 收到后端错误帧 `--- [ERROR] 文案 ---` 时回调 (err)
 * @returns {{ feed: (fullText: String, done?: Boolean) => void }}
 *   feed 增量喂入 xhr.responseText（内部按 lastIndex 只解析新增部分，不重复消费）
 */
const createSseEventParser = ({ onChunk, onMarker, onError } = {}) => {
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
      // 后端约定的错误帧：--- [ERROR:文案] ---
      // 用于 SSE 中途失败时把「可读原因」带给前端（否则只表现为连接中断）
      if (match[1].startsWith('ERROR:')) {
        const msg = match[1].slice('ERROR:'.length).trim() || '处理失败，请稍后重试'
        if (onError) onError(new Error(msg))
        return
      }
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

/**
 * 从错误响应体里提取后端返回的真实文案。
 *
 * 后端统一返回 { code, message, data }（common/Result），
 * 5xx 时 message 是可展示的中文文案（如「系统内部错误」）；
 * 非 JSON 响应体（如 nginx 502/504 的 HTML 错误页）解析失败 → 退回通用兜底文案。
 *
 * 为什么要透传真实文案：workflow-run 依赖 status 区分「能否降级重试」，
 * 而用户看到的应该是服务端到底报了什么，而不是笼统的「实时进度不可用」。
 *
 * @param {String} body  错误响应体原文（xhr.responseText）
 * @param {Number} status HTTP 状态码
 * @returns {String} 可直接展示的文案
 */
const extractErrorMessage = (body, status) => {
  const fallback = '请求失败（' + status + '）'
  if (!body) return fallback
  try {
    const data = JSON.parse(body)
    if (data && typeof data.message === 'string' && data.message) return data.message
  } catch (e) {
    // 非 JSON 响应体（如网关 HTML 错误页），用兜底文案
  }
  return fallback
}

/**
 * 原始流传输（H5 走 XHR，App 走视图层 renderjs）。
 *
 * 只负责「把 HTTP 响应体按增量喂给调用方」，**不解析任何协议**：
 *   - onData(text) 每次收到数据时回调，text 是**累计全文**（与 xhr.responseText 语义一致）
 *   - onDone()      流正常结束
 *   - onError(err)  出错
 *
 * 为什么提供这个原语：
 *   1) streamRequest 需要「累计全文 + 结束标记」，好复用 createSseEventParser；
 *   2) workflow-run 的帧协议是 JSON（run_start / node_start / chunk…），
 *      不是 SSE 事件格式，但同样消费「累计全文 + 结束标记」——
 *      所以两者共用这一个传输原语，各自解析。
 *
 * @param {Object} options { url, data, onData, onDone, onError }
 * @returns {Function} 取消函数（中断请求）
 */
export const streamRaw = (options) => {
  // #ifdef APP-PLUS
  return streamRawApp(options)
  // #endif
  // #ifndef APP-PLUS
  return streamRawH5(options)
  // #endif
}

/** H5 原始流：原生 XHR */
const streamRawH5 = (options) => {
  const token = uni.getStorageSync('token')
  const xhr = new XMLHttpRequest()
  xhr.open('POST', BASE_URL() + options.url, true)
  xhr.setRequestHeader('Content-Type', 'application/json')
  if (token) {
    xhr.setRequestHeader('Authorization', 'Bearer ' + token)
  }
  xhr.onprogress = () => {
    if (options.onData) options.onData(xhr.responseText || '')
  }
  xhr.onload = () => {
    if (xhr.status >= 400) {
      // 401：token 过期，清除登录态并跳登录（P2-B11：统一到 utils/auth-error-handler.js）
      if (xhr.status === 401) {
        handleAuthError('登录已过期，请重新登录')
        if (options.onError) options.onError({ message: '未登录', status: 401 })
        return
      }
      // 透传服务端真实文案（后端 Result.message），便于调用方按 status 决定是否重试
      const message = extractErrorMessage(xhr.responseText, xhr.status)
      if (options.onError) options.onError({ message, status: xhr.status })
      return
    }
    if (options.onData) options.onData(xhr.responseText || '')
    if (options.onDone) options.onDone()
  }
  xhr.onerror = () => {
    // 网络层错误（连不上 / 中断），status 无意义，记 0
    if (options.onError) options.onError({ message: '网络异常', status: 0 })
  }
  xhr.send(JSON.stringify(options.data || {}))
  return () => { try { xhr.abort() } catch (e) { /* ignore */ } }
}

/** App 原始流：由视图层 renderjs 发起 XHR */
const streamRawApp = (options) => {
  const emitter = getSseEmitter()
  if (!emitter) {
    if (options.onError) options.onError(new Error('流式组件未就绪，请返回上一页重试'))
    return () => {}
  }
  const token = uni.getStorageSync('token')
  const reqId = 'sse-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)

  setSseCallbacks(reqId, {
    onData: (text) => {
      if (options.onData) options.onData(text)
    },
    onDone: () => {
      if (options.onDone) options.onDone()
    },
    onError: (err) => {
      // err 统一形状：{ message, status, body }（视图层回传）或 Error（H5 侧兜底）
      const status = (err && err.status) || 0
      const body = (err && err.body) || ''
      // 有响应体时优先取后端 Result.message 的真实文案（与 H5 分支同一 helper，口径一致）
      const message = body
        ? extractErrorMessage(body, status)
        : ((err && err.message) || String(err || '请求失败'))
      // 401：token 过期，清除登录态并跳登录（与 H5 分支口径一致）
      if (status === 401) {
        handleAuthError('登录已过期，请重新登录')
        if (options.onError) options.onError({ message: '未登录', status: 401 })
        return
      }
      // ⚠️ 必须透传 status：workflow-run.vue 依赖它区分「HTTP 错误」与「连不上」，
      //    status 丢失会误走 fallbackToSync，把 AI 再跑一遍（重复计费）。
      if (options.onError) options.onError({ message: message, status: status })
    }
  })

  emitter.start({
    reqId,
    url: BASE_URL() + options.url,
    token: token || '',
    body: JSON.stringify(options.data || {})
  })

  return () => {
    clearSseCallbacks(reqId)
    emitter.abort()
  }
}

/**
 * SSE 流式请求（H5 走 XHR，App 走视图层 renderjs）。
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
export const streamRequest = (options) => {
  // 共用 SSE 事件解析器（增量解析累计全文，按事件拼回换行）
  const parser = createSseEventParser({ onChunk: options.onChunk, onMarker: options.onMarker, onError: options.onError })
  // 记录最后一次收到的累计全文：结束时用它冲掉不完整的尾部事件
  let lastText = ''

  return streamRaw({
    url: options.url,
    data: options.data,
    onData: (text) => {
      lastText = text
      parser.feed(text)
    },
    onDone: () => {
      // 用最后一次的累计全文 + done=true，与原 H5 实现 parser.feed(xhr.responseText, true) 语义一致
      parser.feed(lastText, true)
      if (options.onDone) options.onDone()
    },
    onError: (err) => {
      if (options.onError) options.onError(err)
    }
  })
}
