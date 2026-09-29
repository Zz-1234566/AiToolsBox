/**
 * HTML 清洗（环境自适应）
 *
 *   H5       ：使用 DOMPurify 按白名单清洗，返回可安全交给 v-html 的 HTML 字符串
 *   非 H5    ：App / 小程序没有 DOM，无法做强清洗，按约定返回空串 ''，
 *              由调用方（MarkdownView）降级为 mp-html 渲染
 *
 * 白名单说明：markdown-it 已关闭 html 解析（html:false），正常情况下渲染产物只包含
 * 结构标签；白名单仍按"最小可用"原则收敛，防止后续插件（如 katex）或外部 HTML
 * 片段引入脚本 / 事件属性。
 */

// #ifdef H5
import DOMPurify from 'dompurify'
// #endif

/** 允许保留的标签：markdown 标准结构 + 任务列表 input + 表格滚动包裹 div */
export const SANITIZE_ALLOWED_TAGS = [
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'p', 'br', 'hr', 'div', 'span',
  'strong', 'b', 'em', 'i', 'del', 's', 'sup', 'sub',
  'ul', 'ol', 'li',
  'blockquote', 'pre', 'code',
  'a', 'img',
  'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
  'input', 'label'
]

/**
 * 允许保留的属性
 * class —— highlight.js 的 hljs/language-* 与任务列表的 task-list-item 依赖它
 * 其余为表格 / 图片 / 链接 / checkbox 的结构性属性
 */
export const SANITIZE_ALLOWED_ATTR = [
  'href', 'src', 'alt', 'title', 'class',
  'target', 'rel',
  'align', 'colspan', 'rowspan', 'start',
  'type', 'checked', 'disabled',
  'width', 'height', 'loading'
]

const SANITIZE_CONFIG = {
  ALLOWED_TAGS: SANITIZE_ALLOWED_TAGS,
  ALLOWED_ATTR: SANITIZE_ALLOWED_ATTR,
  ALLOW_DATA_ATTR: false,
  FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form']
}

// #ifdef H5
/** DOMPurify 实例懒加载：避免非浏览器环境在模块加载期就访问 window */
let purifier = null
const getPurifier = () => {
  // typeof window 判空是双保险：即使条件编译未被处理（如纯 Node 环境），也不会直接抛错
  if (typeof window === 'undefined') return null
  if (!purifier) purifier = DOMPurify(window)
  return purifier
}
// #endif

/**
 * 清洗 HTML 字符串
 * @param {String} html markdown-it 渲染出的 HTML
 * @returns {String} H5 环境返回清洗后的 HTML；非 H5 环境恒返回 ''
 */
export const sanitizeHtml = (html) => {
  if (!html) return ''
  let safe = ''
  // #ifdef H5
  try {
    const p = getPurifier()
    // 非 H5 环境（或 window 不可用）没有 DOM，按约定不返回 HTML
    safe = p ? p.sanitize(String(html), SANITIZE_CONFIG) : ''
  } catch (e) {
    // 清洗异常按"不可信"处理：返回空串，由调用方降级为纯文本展示
    console.error('[markdown] sanitize failed:', e)
    safe = ''
  }
  // #endif
  return safe
}
