import { BASE_URL } from '../config/env'
/**
 * SSE 流式请求（XHR 实现，H5 + App vue 页面通用）
 * @param {Object} options
 *   url - 接口路径
 *   data - POST body 对象
 *   onChunk - 每收到一段内容回调 (text)
 *   onDone - 流结束回调
 *   onError - 错误回调 (err)
 */
export const streamRequest = (options) => {
  const token = uni.getStorageSync('token')

  // 增量解析状态：buffer 缓存未完整行，lastIndex 记录已消费位置
  let buffer = ''
  let lastIndex = 0

  // 从 lastIndex 起解析新增内容，按 \n 逐行处理，最后一行可能不完整需缓存
  const parseNewChunks = (fullText, done) => {
    buffer += fullText.slice(lastIndex)
    lastIndex = fullText.length

    const lines = buffer.split('\n')
    buffer = lines.pop() // 最后一行可能不完整，缓存

    for (const line of lines) {
      const trimmed = line.trim()
      if (trimmed.startsWith('data:')) {
        const data = trimmed.substring(5).trim()
        if (data === '[DONE]') continue
        if (data) {
          if (options.onChunk) options.onChunk(data)
        }
      }
    }

    // 流结束时处理缓存中最后一段没有换行的 data:
    if (done && buffer.trim()) {
      const trimmed = buffer.trim()
      if (trimmed.startsWith('data:')) {
        const data = trimmed.substring(5).trim()
        if (data && data !== '[DONE]') {
          if (options.onChunk) options.onChunk(data)
        }
      }
      buffer = ''
    }
  }

  const xhr = new XMLHttpRequest()
  xhr.open('POST', BASE_URL + options.url, true)
  xhr.setRequestHeader('Content-Type', 'application/json')
  if (token) {
    xhr.setRequestHeader('Authorization', 'Bearer ' + token)
  }

  xhr.onprogress = () => {
    // 每收到一段数据，解析 data: 行（增量，不重复处理）
    parseNewChunks(xhr.responseText)
  }

  xhr.onload = () => {
    // 非 2xx 按错误处理（响应体是错误 JSON，不是 SSE 流）
    if (xhr.status >= 400) {
      // 401：token 过期，清除登录态并跳登录
      if (xhr.status === 401) {
        uni.removeStorageSync('token')
        uni.removeStorageSync('userInfo')
        uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
        if (options.onError) options.onError(new Error('未登录'))
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/login' })
        }, 600)
        return
      }
      if (options.onError) options.onError(new Error('请求失败（' + xhr.status + '）'))
      return
    }
    // 流结束，处理最后一段未换行的数据
    parseNewChunks(xhr.responseText, true)
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

  // 与 streamRequest 相同的增量解析逻辑
  let buffer = ''
  let lastIndex = 0
  const parseNewChunks = (fullText, done) => {
    buffer += fullText.slice(lastIndex)
    lastIndex = fullText.length

    const lines = buffer.split('\n')
    buffer = lines.pop()

    for (const line of lines) {
      const trimmed = line.trim()
      if (trimmed.startsWith('data:')) {
        const data = trimmed.substring(5).trim()
        if (data === '[DONE]') continue
        if (data) {
          if (options.onChunk) options.onChunk(data)
        }
      }
    }

    if (done && buffer.trim()) {
      const trimmed = buffer.trim()
      if (trimmed.startsWith('data:')) {
        const data = trimmed.substring(5).trim()
        if (data && data !== '[DONE]') {
          if (options.onChunk) options.onChunk(data)
        }
      }
      buffer = ''
    }
  }

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

  return resolveFileBlob(options.file)
    .then(({ blob, name }) => {
      const formData = new FormData()
      formData.append('file', blob, name)
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
        // 上传完成后的响应流：每收到一段数据，增量解析 data: 行
        parseNewChunks(xhr.responseText)
      }

      xhr.onload = () => {
        if (xhr.status >= 400) {
          if (xhr.status === 401) {
            uni.removeStorageSync('token')
            uni.removeStorageSync('userInfo')
            uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
            if (options.onError) options.onError(new Error('未登录'))
            setTimeout(() => {
              uni.reLaunch({ url: '/pages/login' })
            }, 600)
            return
          }
          if (options.onError) options.onError(new Error('请求失败（' + xhr.status + '）'))
          return
        }
        parseNewChunks(xhr.responseText, true)
        if (options.onDone) options.onDone()
      }

      xhr.onerror = (err) => {
        if (options.onError) options.onError(err)
      }

      xhr.send(formData)
    })
    .catch((err) => {
      if (options.onError) options.onError(err)
    })
}
