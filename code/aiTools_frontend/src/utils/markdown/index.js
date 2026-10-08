/**
 * markdown 渲染管线统一出口
 *
 * 设计要点：
 *  - 单例 markdown-it：全项目共用一个解析器实例，配置只做一次，避免反复构造开销
 *  - renderMarkdown(source)：一步到位把 markdown 源文本渲染为 HTML 字符串
 *  - createMarkdownRenderer(options)：需要隔离配置（不同插件组合 / 测试）时自建实例
 *  - 解析一律走 markdown-it，禁止再用正则给 markdown "补空行 / 规范化"
 */
import MarkdownIt from 'markdown-it'
import { applyPlugins, DEFAULT_PLUGIN_NAMES, pluginRegistry } from './plugins.js'

/**
 * 默认解析配置
 *  html: false  —— 不解析原始 HTML（安全，且保证渲染完全由 markdown-it 决定）
 *  linkify      —— 裸链接 / 邮箱自动转 <a>
 *  breaks       —— 单个换行即 <br>（贴合 AI 流式输出的口语化换行习惯）
 *  typographer  —— 智能标点（引号、破折号等）
 */
export const DEFAULT_MARKDOWN_OPTIONS = {
  html: false,
  linkify: true,
  breaks: true,
  typographer: true
}

/**
 * 创建独立的 markdown-it 渲染器
 * @param {Object} options
 *   plugins 插件名数组，默认 DEFAULT_PLUGIN_NAMES；传 [] 表示不启用任何插件
 *   其余字段作为 markdown-it 原生配置，覆盖 DEFAULT_MARKDOWN_OPTIONS
 * @returns {Object} markdown-it 实例
 */
export const createMarkdownRenderer = (options = {}) => {
  const { plugins = DEFAULT_PLUGIN_NAMES, ...mdOptions } = options
  const md = new MarkdownIt({ ...DEFAULT_MARKDOWN_OPTIONS, ...mdOptions })
  applyPlugins(md, plugins)
  return md
}

/** 单例实例（懒加载，首次调用时创建） */
let singletonRenderer = null

/** 获取全局单例渲染器 */
export const getMarkdownRenderer = () => {
  if (!singletonRenderer) {
    singletonRenderer = createMarkdownRenderer()
  }
  return singletonRenderer
}

/**
 * 主入口：markdown 源文本 → HTML 字符串
 * 失败时返回空串（由调用方降级为纯文本展示），绝不抛错打断页面渲染
 * @param {String} source markdown 源文本
 * @returns {String} HTML 字符串（未经清洗，注入前需过 sanitizeHtml）
 */
export const renderMarkdown = (source) => {
  if (!source) return ''
  try {
    return getMarkdownRenderer().render(String(source))
  } catch (e) {
    console.error('[markdown] render failed:', e)
    return ''
  }
}

// 统一出口：插件能力与清洗能力一并对外暴露
export { applyPlugins, DEFAULT_PLUGIN_NAMES, pluginRegistry }
export { sanitizeHtml } from './sanitize.js'
