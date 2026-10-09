/**
 * 文件显示名解析（历史列表 / 历史详情 / 工作流视图共用）。
 * 只做展示层计算，不含接口调用；改这里三处同时生效。
 *
 * 背景：COS 私有文件的 fileUrl 是临时签名 URL，
 * q-sign-time 每次查询都会重新签发，完整 URL 不可用于比对，
 * 只有 object key（file/{userId}/{uuid}.{ext}）才是稳定标识。
 */

/** 整串是否就是一个文件 URL（排除正文里内嵌链接的文本） */
export const isFileUrl = (s) => /^https?:\/\/\S+$/i.test(String(s || '').trim())

/** 扩展名（小写，无点）；无扩展名返回 '' */
export const extensionOf = (name) => {
  const i = String(name || '').lastIndexOf('.')
  return i > 0 ? String(name).slice(i + 1).toLowerCase() : ''
}

/**
 * COS object key：剥掉协议域名与签名 query。
 * https://host/file/1/x.mp3?sign=... → file/1/x.mp3
 * 输入本来就是纯 key 时原样返回；空值返回 ''（两个空值不建议直接比较）。
 */
export const fileKeyOf = (u) => {
  const s = String(u || '').split('?')[0].split('#')[0]
  return s.replace(/^https?:\/\/[^/]+\//i, '')
}

/**
 * 从 URL 末段提取文件名：
 * 签名 URL 形如 .../file/1/{uuid}.mp3?sign=xxx&q-ak=xxx，
 * 必须先 strip query 再取末段，否则会把签名参数当成文件名。
 * 工作流产物命名为 {短id}_{原文件名}.ext，这里沿用既有回溯规则。
 */
export const fileNameFromUrl = (url) => {
  if (!url) return ''
  let seg = String(url).split('?')[0].split('#')[0].split('/').filter(Boolean).pop() || ''
  if (!seg) return ''
  try {
    seg = decodeURIComponent(seg)
  } catch (e) { /* 非转义序列则用原值 */ }
  const cut = seg.indexOf('_')   // 形如 8eb03bf1_文件名.pdf
  return cut > 0 && cut < 40 ? seg.slice(cut + 1) : seg
}

/** 音频扩展名（用于列表页图标、详情页文件类型判断） */
const AUDIO_EXT = ['mp3', 'wav', 'm4a', 'aac', 'amr', 'silk', 'ogg', 'aiff', 'wma']

/** 可直接播放的扩展名（openDocument 不支持音频，这类走试听而非打开） */
const PLAYABLE_EXT = ['mp3', 'wav', 'm4a', 'aac', 'amr', 'silk', 'ogg']

/** 扩展名是否音频 */
export const isAudioExt = (ext) => AUDIO_EXT.indexOf(String(ext || '').toLowerCase()) >= 0

/** 扩展名是否可直接试听 */
export const isPlayableExt = (ext) => PLAYABLE_EXT.indexOf(String(ext || '').toLowerCase()) >= 0

/** 文件显示名：兼容 {name,fileName,url} 对象与纯 url 字符串 */
export const fileDisplayName = (f) => {
  if (!f) return '文件'
  if (typeof f === 'string') return fileNameFromUrl(f) || '文件'
  return f.name || f.fileName || fileNameFromUrl(f.url || f.fileUrl) || '文件'
}