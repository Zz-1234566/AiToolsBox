/**
 * Base64 编解码（跨端安全实现）。
 *
 * 【为什么需要自己实现】
 * uni-app 官方文档明确：App 端与小程序端的 js 运行在 jscore 下，**没有浏览器专有对象**，
 * 包括 atob / btoa。实测 `node_modules/@dcloudio/uni-app-plus/dist/` 全目录
 * atob / btoa 命中 0 次 —— App 逻辑层确实没有这两个函数。
 *
 * 直接用 atob 的后果：parseJwt 抛异常 → catch 返回 null → isTokenValid 恒为 false
 * → requireLogin 永远判定未登录 → **用户被反复踢回登录页**，且没有任何报错提示。
 *
 * 【实现要点】
 * 1. 优先用浏览器的 atob（存在于 H5 与 App 视图层，且性能最好）；
 * 2. 没有 atob 时走纯 JS 实现（不依赖任何平台 API，全端可用）；
 * 3. 解码后要处理 UTF-8 —— 直接用 String.fromCharCode 拼字节会得到乱码，
 *    需再按 UTF-8 还原。
 */

/** 纯 JS base64 解码（返回字节数组） */
const decodeToBytes = (b64) => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  // 先去掉 padding 与空白
  const clean = String(b64).replace(/[^A-Za-z0-9+/]/g, '')
  const len = clean.length
  const bytes = []
  for (let i = 0; i < len; i += 4) {
    const c0 = chars.indexOf(clean.charAt(i))
    const c1 = chars.indexOf(clean.charAt(i + 1))
    const c2 = chars.indexOf(clean.charAt(i + 2))
    const c3 = chars.indexOf(clean.charAt(i + 3))
    // 最后一组可能不足 4 字符：此时 c2/c3 为 -1，
    // 必须**按剩余字符数**决定解出几个字节，否则会多解出一个 0 字节
    // （该 bug 会让解码结果尾部多一个 \0，导致 JSON.parse 直接失败）。
    const remain = len - i
    bytes.push((c0 << 2) | (c1 >> 4))
    if (remain > 2 && c2 >= 0) bytes.push(((c1 & 0x0f) << 4) | (c2 >> 2))
    if (remain > 3 && c3 >= 0) bytes.push(((c2 & 0x03) << 6) | c3)
  }
  return bytes
}

/** 字节数组 → UTF-8 字符串 */
const bytesToUtf8 = (bytes) => {
  let out = ''
  for (let i = 0; i < bytes.length; i++) {
    const b = bytes[i]
    if (b < 0x80) {
      out += String.fromCharCode(b)
    } else if (b < 0xe0) {
      out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[++i] & 0x3f))
    } else if (b < 0xf0) {
      out += String.fromCharCode(((b & 0x0f) << 12) | ((bytes[++i] & 0x3f) << 6) | (bytes[++i] & 0x3f))
    } else {
      // 4 字节（代理对）
      const cp = ((b & 0x07) << 18) | ((bytes[++i] & 0x3f) << 12) | ((bytes[++i] & 0x3f) << 6) | (bytes[++i] & 0x3f)
      const off = cp - 0x10000
      out += String.fromCharCode(0xd800 + (off >> 10), 0xdc00 + (off & 0x3ff))
    }
  }
  return out
}

/**
 * base64（标准或 URL-safe）解码为字符串。
 * @param {String} b64 base64 字符串（自动处理 -_ 与 padding 缺失）
 * @returns {String} 解码后的 UTF-8 字符串；失败返回 ''
 */
export const base64Decode = (b64) => {
  const s = String(b64 || '')
  if (!s) return ''
  // base64url → 标准 base64，并补齐 padding
  const std = s.replace(/-/g, '+').replace(/_/g, '/')
  const padded = std + '='.repeat((4 - (std.length % 4)) % 4)
  // 优先用原生 atob（H5 / App 视图层）
  if (typeof atob === 'function') {
    try {
      const raw = atob(padded)
      // 原生 atob 返回的是「二进制字符串」（每个字符一个字节），需按 UTF-8 还原
      let bin = ''
      for (let i = 0; i < raw.length; i++) bin += raw.charCodeAt(i).toString(2).padStart(8, '0')
      const bytes = []
      for (let i = 0; i < bin.length; i += 8) bytes.push(parseInt(bin.substr(i, 8), 2))
      // ⚠️ atob 对「末尾不足 3 字节的分组」会补出多余的 0 字节，
      //    与纯 JS 实现保持一致：按有效位裁掉尾部多余的 0
      const validLen = Math.floor((std.replace(/=+$/, '').length * 3) / 4)
      return bytesToUtf8(bytes.slice(0, validLen))
    } catch (e) {
      // 落到下面的纯 JS 实现
    }
  }
  try {
    return bytesToUtf8(decodeToBytes(std))
  } catch (e) {
    return ''
  }
}

/**
 * 字符串 → base64（UTF-8 安全）。
 * @param {String} str
 * @returns {String} base64 字符串
 */
export const base64Encode = (str) => {
  const s = String(str == null ? '' : str)
  // 优先用原生 btoa
  if (typeof btoa === 'function') {
    try {
      const utf8 = unescape(encodeURIComponent(s))
      return btoa(utf8)
    } catch (e) {
      // 落到下面的纯 JS 实现
    }
  }
  const bytes = []
  for (let i = 0; i < s.length; i++) {
    let code = s.charCodeAt(i)
    if (code < 0x80) {
      bytes.push(code)
    } else if (code < 0x800) {
      bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f))
    } else if (code >= 0xd800 && code <= 0xdbff && i + 1 < s.length) {
      const next = s.charCodeAt(i + 1)
      code = 0x10000 + ((code - 0xd800) << 10) + (next - 0xdc00)
      i++
      bytes.push(0xf0 | (code >> 18), 0x80 | ((code >> 12) & 0x3f), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f))
    } else {
      bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f))
    }
  }
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i]
    const b1 = bytes[i + 1]
    const b2 = bytes[i + 2]
    out += chars.charAt(b0 >> 2)
    out += chars.charAt(((b0 & 0x03) << 4) | ((b1 || 0) >> 4))
    out += b1 === undefined ? '=' : chars.charAt(((b1 & 0x0f) << 2) | ((b2 || 0) >> 6))
    out += b2 === undefined ? '=' : chars.charAt(b2 & 0x3f)
  }
  return out
}
