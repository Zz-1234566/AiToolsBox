/**
 * 全局复制能力（跨端统一出口）
 *
 * 为什么统一到一处：
 *   之前全项目 7 处复制各写各的 —— 有的用 uni.setClipboardData，有的带 H5 兜底，
 *   有的静默 return（空内容点了没反应），还有一处在模板里内联正则做 markdown 剥离。
 *   行为不一致、toast 文案不统一、且剥离逻辑无法复用。
 *
 * 两个入口，对应两类真实需求：
 *   copyRaw(text, label)   —— 复制原始文本，保留 Markdown 语法（# / ** / |）。
 *                            用于粘到 Notion / 语雀 / GitHub 这类支持 MD 的地方。
 *   copyPlain(text, label) —— 复制纯文本，剥掉 Markdown 语法。
 *                            用于粘到微信 / 钉钉 / 短信框这类不支持 MD 的地方。
 *
 * 剥离逻辑不在这里重写，复用 utils/markdown/plaintext.js 的 markdownToPlainText。
 *
 * 跨端：只用 uni.setClipboardData（H5 / App / 小程序全端支持），不碰任何 H5 专属 API；
 *      navigator.clipboard 兜底仅在 setClipboardData 不存在时启用（老浏览器）。
 */
import { markdownToPlainText } from './markdown/plaintext'

/** 空内容统一提示文案 */
const EMPTY_TEXT = '暂无可复制内容'

/**
 * 底层复制：统一 toast / 空值拦截 / 失败兜底
 * @param {String} text 待复制内容
 * @param {String} [label] toast 文案，如「会议纪要」→ 提示「会议纪要已复制」
 * @param {String} [fallbackToast] 自定义空内容提示
 * @returns {Boolean} 是否真正发起了复制
 */
const copy = (text, label, fallbackToast) => {
  const data = text == null ? '' : String(text)
  if (!data) {
    uni.showToast({ title: fallbackToast || EMPTY_TEXT, icon: 'none' })
    return false
  }
  const okTitle = label ? `${label}已复制` : '已复制'
  if (typeof uni !== 'undefined' && uni.setClipboardData) {
    uni.setClipboardData({
      data,
      success: () => uni.showToast({ title: okTitle, icon: 'none' }),
      fail: () => uni.showToast({ title: '复制失败，请重试', icon: 'none' })
    })
    return true
  }
  // 老环境兜底：Clipboard API 仅在 uni API 缺失时使用（正常编译不会走到）
  if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(data).then(
      () => uni.showToast({ title: okTitle, icon: 'none' }),
      () => uni.showToast({ title: '复制失败，请重试', icon: 'none' })
    )
    return true
  }
  uni.showToast({ title: '当前环境不支持复制', icon: 'none' })
  return false
}

/**
 * 复制原始文本（保留 Markdown 语法）
 * @param {String} text 原文
 * @param {String} [label] toast 前缀，如「Markdown」→ 「Markdown已复制」
 * @param {String} [emptyText] 空内容时的自定义提示
 * @returns {Boolean} 是否发起复制
 */
export const copyRaw = (text, label, emptyText) => copy(text, label, emptyText)

/**
 * 复制纯文本（剥离 Markdown 语法）
 * @param {String} text markdown 原文
 * @param {String} [label] toast 前缀，如「会议纪要」→ 「会议纪要已复制」
 * @param {String} [emptyText] 空内容时的自定义提示
 * @returns {Boolean} 是否发起复制
 */
export const copyPlain = (text, label, emptyText) => {
  const src = text == null ? '' : String(text)
  // 先判原文空不空：剥离后可能只剩空白，那种情况同样按「无内容」提示
  if (!src.trim()) {
    uni.showToast({ title: emptyText || EMPTY_TEXT, icon: 'none' })
    return false
  }
  return copy(markdownToPlainText(src), label, emptyText)
}