/**
 * 工作流视图公共逻辑（workflow.vue 与 workflow-run.vue 共用）。
 * 只做展示层计算，不含接口调用；改这里两边同时生效。
 */

import { fileDisplayName as sharedFileDisplayName } from './fileDisplayName'

/* ==================== 节点图标（填充式，与既有节点卡同一套风格） ==================== */

/** 后端 icon 标识 → 24x24 SVG path（与 ToolIcon 保持同一套线性图标风格） */
export const NODE_ICON_PATHS = {
  'file-text': 'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm0 7V3.5L18.5 9H14z',
  'mic': 'M12 14a3 3 0 003-3V5a3 3 0 00-6 0v6a3 3 0 003 3zm5-3a5 5 0 01-10 0H5a7 7 0 006 6.92V21h2v-3.08A7 7 0 0019 11h-2z',
  'meeting': 'M12 22a10 10 0 100-20 10 10 0 000 20zm0-16a8 8 0 110 16 8 8 0 010-16zm-1 3v6l5 3 1-1.7-4-2.4V9h-2z',
  'summary': 'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm0 7V3.5L18.5 9H14zM8 13h8v2H8v-2zm0 4h5v2H8v-2z',
  'report': 'M19 3h-4V1h-2v2H8V1H6v2H5a2 2 0 00-2 2v16a2 2 0 002 2h14a2 2 0 002-2V5a2 2 0 00-2-2zm0 16H5V8h14v11z',
  'weekly': 'M7 2v2H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2V6a2 2 0 00-2-2h-2V2h-2v2H9V2H7zm12 18H5V10h14v10z',
  'bg-color': 'M3 3h18v18H3V3zm2 14l5-5 4 4 3-3 3 4H5z',
  'image': 'M3 3h18v18H3V3zm2 14l5-5 4 4 3-3 3 4H5z'
}

/** 节点图标标识 → SVG path */
export const nodeIconPath = (icon) => {
  const key = String(icon || '')
  return NODE_ICON_PATHS[key] || NODE_ICON_PATHS['summary']
}

/* ==================== 线性图标集（设计稿 ICON 同款 stroke 风格） ==================== */

/**
 * 线性图标：值为 path d 数组（一条 d 里可含多段子路径）。
 * fill=true 表示实心图标（播放/停止），由 WfIcon.vue 切换 fill/stroke。
 */
export const STROKE_ICONS = {
  back: ['M15 18l-6-6 6-6'],
  check: ['M20 6.5L9.5 17 4 11.5'],
  close: ['M18 6L6 18M6 6l12 12'],
  alert: ['M12 3a9 9 0 100 18 9 9 0 100-18', 'M12 7.4v5.4', 'M12 15.5v.9'],
  clock: ['M12 3a9 9 0 100 18 9 9 0 100-18', 'M12 7.2v5.2l3.2 2'],
  play: { fill: true, d: ['M8.4 5.4l10.4 6.6-10.4 6.6z'] },
  stop: { fill: true, d: ['M8 7h8a1 1 0 011 1v8a1 1 0 01-1 1H8a1 1 0 01-1-1V8a1 1 0 011-1z'] },
  refresh: ['M20.4 12a8.4 8.4 0 10-2.6 6', 'M20.4 5.6v5.4H15'],
  download: ['M12 3.6v11.6', 'M7.6 10.8L12 15.2l4.4-4.4', 'M4.6 20.2h14.8'],
  copy: ['M8.4 8.4h11.2v11.2H8.4z', 'M15.6 5.6a2.4 2.4 0 00-2.4-2.4H6.8a2.4 2.4 0 00-2.4 2.4v6.4a2.4 2.4 0 002.4 2.4'],
  folder: ['M3.6 6.6a2 2 0 012-2h3.1a2 2 0 011.5.7l1 1.1h7.2a2 2 0 012 2v8.9a2 2 0 01-2 2H5.6a2 2 0 01-2-2z'],
  upload: ['M6.8 18.4A4.4 4.4 0 016.4 9.7a6 6 0 0111.4 1.4 3.6 3.6 0 01-.4 7.3', 'M12 21.4v-7.8', 'M8.9 16.5L12 13.4l3.1 3.1'],
  file: ['M6 4.6h12a2.6 2.6 0 012.6 2.6v9.6a2.6 2.6 0 01-2.6 2.6H6a2.6 2.6 0 01-2.6-2.6V7.2A2.6 2.6 0 016 4.6z', 'M8.6 9.4h6.8', 'M8.6 13h6.8', 'M8.6 16.6h4'],
  mic: ['M12 3a3 3 0 013 3v5a3 3 0 01-6 0V6a3 3 0 013-3z', 'M5 11a7 7 0 0014 0', 'M12 18v3'],
  summary: ['M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z', 'M14 2v6h6', 'M8 13h8', 'M8 17h5'],
  report: ['M19 3h-4V1h-2v2H8V1H6v2H5a2 2 0 00-2 2v16a2 2 0 002 2h14a2 2 0 002-2V5a2 2 0 00-2-2z', 'M7 8.6h11v10.8H7z', 'M9.4 12.4h6'],
  calendar: ['M7 2v2H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2V6a2 2 0 00-2-2h-2V2h-2v2H9V2H7z', 'M5 10h14v10H5z'],
  palette: ['M12 3a9 9 0 000 18h1.5a1.5 1.5 0 001.5-1.5 1.5 1.5 0 011.5-1.5H18a3 3 0 003-3A9 9 0 0012 3z', 'M7.5 12.5a1 1 0 100-2 1 1 0 000 2z', 'M10.2 8.4a1 1 0 100-2 1 1 0 000 2z', 'M14.4 8.4a1 1 0 100-2 1 1 0 000 2z'],
  image: ['M6 4.6h12a2.6 2.6 0 012.6 2.6v9.6a2.6 2.6 0 01-2.6 2.6H6a2.6 2.6 0 01-2.6-2.6V7.2A2.6 2.6 0 016 4.6z', 'M10.65 10a1.75 1.75 0 11-3.5 0 1.75 1.75 0 013.5 0z', 'M20.6 15.4l-4.2-4.2L8 19.4'],
  sparkle: ['M11.4 3.4l1.7 4.4 4.4 1.7-4.4 1.7-1.7 4.4-1.7-4.4L5.3 9.5l4.4-1.7z', 'M17.6 15.2l.75 1.95 1.95.75-1.95.75-.75 1.95-.75-1.95-1.95-.75 1.95-.75z'],
  video: ['M4.4 6h9a2.6 2.6 0 012.6 2.6v6.8a2.6 2.6 0 01-2.6 2.6h-9a2.6 2.6 0 01-2.6-2.6V8.6A2.6 2.6 0 014.4 6z', 'M15.4 10.6l5.8-3.3v9.4l-5.8-3.3z']
}

/** 后端 icon 标识 → 线性图标名 */
export const nodeIconName = (icon) => ({
  'file-text': 'file',
  'mic': 'mic',
  'meeting': 'clock',
  'summary': 'summary',
  'report': 'report',
  'weekly': 'calendar',
  'bg-color': 'palette',
  'image': 'image',
  'video': 'video',
  'upload': 'upload'
}[String(icon || '')] || 'sparkle')

/** 节点图标渐变底（设计稿 node-ic 的 --g1/--g2），按线性图标名取 */
const NODE_ICON_GRADIENTS = {
  file: ['#93C5FD', '#3B82F6'],
  mic: ['#FCD34D', '#F59E0B'],
  clock: ['#A78BFA', '#8B5CF6'],
  summary: ['#C7D2FE', '#6366F1'],
  report: ['#F9A8D4', '#EC4899'],
  calendar: ['#5EEAD4', '#14B8A6'],
  palette: ['#FCD34D', '#F59E0B'],
  image: ['#C7D2FE', '#6366F1'],
  video: ['#5EEAD4', '#14B8A6'],
  upload: ['#93C5FD', '#3B82F6'],
  sparkle: ['#A78BFA', '#8B5CF6']
}

/** 节点图标渐变：后端 icon 标识 → CSS linear-gradient */
export const nodeIconGradient = (icon) => {
  const g = NODE_ICON_GRADIENTS[nodeIconName(icon)] || NODE_ICON_GRADIENTS.sparkle
  return `linear-gradient(135deg, ${g[0]} 0%, ${g[1]} 100%)`
}

/* ==================== 节点真名解析 ==================== */

/**
 * 节点真名解析（同步结果 / SSE / 输入快照三处共用）。
 * nodeResults 是 Map，键就是 nodeId（n1/n2），直接把键当标题会显示成占位名，
 * 所以按优先级取真名，取不到才回退 nodeId（绝不编造中文名）：
 *   1. 后端 WorkflowNodeResult.nodeName（后端补字段后自动生效，无需改前端）
 *   2. 工作流详情里的节点：ctx.nodes[nodeId].toolName → .name
 *   3. 工具列表按 nodeRef（toolCode）反查 toolName
 *   4. 兜底 nodeId
 * @param {String} nodeId - nodeResults 的键
 * @param {Object} [nr] - nodeResults 的值（可能为 undefined）
 * @param {String} [nodeRef] - SSE 帧直接带的工具编码（有则优先于 nodeId 反查）
 * @param {Object} [ctx] - { nodes: 工作流详情节点列表, tools: 工具列表 }
 * @returns {String} 可读节点名
 */
export const nodeNameOf = (nodeId, nr, nodeRef, ctx = {}) => {
  if (nr && nr.nodeName) return nr.nodeName
  const nodes = ctx.nodes || []
  const hit = nodes.find(n => n.nodeId === nodeId)
  if (hit) {
    if (hit.toolName) return hit.toolName
    if (hit.name) return hit.name
  }
  const code = nodeRef || (hit && hit.nodeRef)
  if (code) {
    const t = (ctx.tools || []).find(x => x.toolCode === code)
    if (t && t.toolName) return t.toolName
  }
  return nodeId
}

/* ==================== 文件 ==================== */

/** 文件显示名：兼容 {name,url} 与纯 url 字符串（实现已收敛到 utils/fileDisplayName.js 供历史页共用） */
export const fileDisplayName = sharedFileDisplayName

/** 点击文件：跳下载（H5 新窗口打开签名 URL，App 下载后打开文档） */
export const openFile = (f) => {
  const url = typeof f === 'string' ? f : (f && (f.url || f.fileUrl))
  if (!url) {
    uni.showToast({ title: '文件链接不可用', icon: 'none' })
    return
  }
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200) uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
    },
    fail: () => uni.showToast({ title: '下载失败', icon: 'none' })
  })
  // #endif
}

/* ==================== 产物 ==================== */

/** 产物类型中文：1文本 2图片 3视频 4音频 5文件（与后端 ToolOutputVO.outputType 一致） */
export const outputTypeLabel = (t) => ({
  1: '文本', 2: '图片', 3: '视频', 4: '音频', 5: '文件'
}[t] || '文件')

/** 图片产物放大预览（uni.previewImage 跨端通用） */
export const previewOutput = (o) => {
  if (!o || !o.fileUrl) return
  uni.previewImage({ urls: [o.fileUrl], current: o.fileUrl })
}

/* ==================== 输入快照 ==================== */

/**
 * inputFileNames 是 JSON 字符串，形如 {"n1":["原始名.docx"]}。
 * 解析成 { nodeId: [name] } 供 parseInputSnapshot 按下标取；非法/为空一律返回 null（触发回落）。
 */
export const parseNameMap = (raw) => {
  if (!raw) return null
  let obj = raw
  if (typeof raw === 'string') {
    try { obj = JSON.parse(raw) } catch (e) { return null }
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null
  return obj
}

/**
 * inputSnapshot 是 JSON 字符串，形如 {"n1":["路径1","路径2"]}；解析后只保留文件名供展示。
 * 转成数组 [{ nodeId, names }]，模板 v-for 用 nodeId 作 key 更稳（对象遍历 key 不保证稳定）。
 *
 * @param {Object} [nameMap] 后端 inputFileNames 解析结果（{nodeId:[原始名]}）。
 *        与 raw 同构按下标对应：有原始名就用原始名，没有才回落到从 URL 反解的 uuid 名。
 *        不传/为空 → 完全走旧的 URL 反解逻辑（老数据兼容）。
 */
export const parseInputSnapshot = (raw, nameMap) => {
  if (!raw) return []
  let obj = raw
  if (typeof raw === 'string') {
    try { obj = JSON.parse(raw) } catch (e) { return [] }
  }
  if (!obj || typeof obj !== 'object') return []
  const names0 = nameMap || null
  const rows = []
  Object.keys(obj).forEach((nodeId) => {
    const v = obj[nodeId]
    // 兼容字符串与数组两种形态
    const arr = Array.isArray(v) ? v : (v == null ? [] : [v])
    const origList = (names0 && Array.isArray(names0[nodeId])) ? names0[nodeId] : null
    const names = arr
      .map((x, i) => {
        // 原始名优先（不下标即乱序）；该项为空/非数组时回落到 URL 反解
        const orig = origList ? origList[i] : null
        if (orig) return orig
        return (typeof x === 'string' ? fileDisplayName(x) : fileDisplayName(x && (x.name || x.fileName || x.url)))
      })
      .filter(Boolean)
    if (names.length) rows.push({ nodeId, names })
  })
  return rows
}

/* ==================== 耗时格式化 ==================== */

/**
 * 耗时是否「真实可得」。
 *
 * 为什么必须区分：nodeResults[nid].costMs 可能为 null / undefined（字段缺失，
 * 例如老数据或上游失败没跑到的节点），也可能是 0（真的瞬间完成）。
 * 两者都被当成 0 去格式化的话，前者会显示成「0.0秒」——用户会以为节点没执行，
 * 而实际上是这个数据根本不存在。
 *
 * @param {*} ms 原始值（可能是 number / 字符串数字 / null / undefined）
 * @returns {Number|null} 可解析成有限数字则返回该数字，否则返回 null（数据缺失）
 */
export const parseDuration = (ms) => {
  if (ms === null || ms === undefined || ms === '') return null
  const n = typeof ms === 'number' ? ms : Number(ms)
  return Number.isFinite(n) && n >= 0 ? n : null
}

/**
 * 毫秒 → [{ v: '45', u: 'ms' }] / [{ v: '1.4', u: '秒' }] / [{ v: '1', u: '分' }, { v: '12', u: '秒' }]
 *
 * 量级自适应（设计稿 HUD 与节点耗时共用这一份）：
 *   - < 1000ms  → 显示毫秒。纯文本解析这类节点常在 10~80ms，
 *     压成「0.0秒」会被误读成「没执行」，毫秒精度才有信息量。
 *   - < 60s     → 秒 + 1 位小数
 *   - >= 60s    → 分 + 秒（对齐设计稿 fmtT 的 X分YY秒）
 *
 * @param {*} ms 毫秒值；null/undefined/非法 → 返回 null（调用方决定怎么兜底）
 */
export const durationParts = (ms) => {
  const n = parseDuration(ms)
  if (n === null) return null
  if (n === 0) return [{ v: '0', u: 'ms' }]
  if (n < 1000) return [{ v: String(Math.round(n)), u: 'ms' }]
  const totalSec = n / 1000
  const m = Math.floor(totalSec / 60)
  if (m > 0) {
    return [{ v: String(m), u: '分' }, { v: String(Math.round(totalSec - m * 60)).padStart(2, '0'), u: '秒' }]
  }
  return [{ v: totalSec.toFixed(1), u: '秒' }]
}

/** 毫秒 → '45ms' / '1.4秒' / '1分12秒'；数据缺失返回 fallback（默认 '—'，不伪造 0） */
export const durationText = (ms, fallback = '—') => {
  const parts = durationParts(ms)
  return parts ? parts.map(p => p.v + p.u).join('') : fallback
}
