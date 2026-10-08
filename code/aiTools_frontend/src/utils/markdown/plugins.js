/**
 * markdown-it 插件注册表（管线唯一可扩展点）
 *
 * 约定：每个插件是一个对象 { name, apply(md), enabled? }
 *   - name    插件唯一标识，用于按名启用 / 禁用
 *   - apply   接收 markdown-it 实例，在内部完成 use / options / renderer 配置
 *   - enabled 显式置为 false 表示"预留注册位"：已登记但不参与默认渲染
 *
 * 扩展方式：新增一个插件对象并加入 pluginRegistry；
 *   若要默认生效，再把 name 加进 DEFAULT_PLUGIN_NAMES 即可，
 *   解析管线（utils/markdown/index.js）无需任何改动。
 */
import hljs from 'highlight.js/lib/common'
import taskLists from 'markdown-it-task-lists'

/**
 * 代码高亮：交给 highlight.js（只内置 common 常用语言，控制包体积）
 * 渲染结果形如 <pre class="hljs"><code class="language-xxx">...</code></pre>，
 * 浅色主题样式由 MarkdownView 引入的 highlight.js/styles/github.css 提供。
 */
export const highlightPlugin = {
  name: 'highlight',
  apply (md) {
    md.options.highlight = (code, lang) => {
      // 语言已知：正常高亮；ignoreIllegals 避免个别语法错误直接抛异常
      if (lang && hljs.getLanguage(lang)) {
        try {
          const value = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
          return `<pre class="hljs"><code class="language-${md.utils.escapeHtml(lang)}">${value}</code></pre>`
        } catch (e) {
          // 高亮失败不打断渲染，落到下方转义输出
        }
      }
      // 语言未知 / 高亮异常：转义后原样输出，保证结构不破坏、不引入 XSS
      return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
    }
  }
}

/**
 * 任务列表：- [ ] / - [x]
 * enabled: true —— 生成的 checkbox 不禁用（保留交互能力）
 * label: true   —— 用 <label> 包裹，便于点击整行切换、便于统一皮肤
 */
export const taskListsPlugin = {
  name: 'task-lists',
  apply (md) {
    md.use(taskLists, { enabled: true, label: true })
  }
}

/**
 * 表格横向滚动包裹：把 markdown-it 生成的 <table> 套一层可滚动 div
 * 表格列多时在窄屏（移动端）里可左右滑动，而不是撑破容器。
 */
export const tableWrapPlugin = {
  name: 'table-wrap',
  apply (md) {
    const defaultOpen = md.renderer.rules.table_open ||
      ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
    const defaultClose = md.renderer.rules.table_close ||
      ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
    md.renderer.rules.table_open = (tokens, idx, options, env, self) =>
      '<div class="md-table-wrap">' + defaultOpen(tokens, idx, options, env, self)
    md.renderer.rules.table_close = (tokens, idx, options, env, self) =>
      defaultClose(tokens, idx, options, env, self) + '</div>'
  }
}

/**
 * 预留注册位：数学公式（katex）
 * 启用步骤：npm i markdown-it-katex（或 @vscode/markdown-it-katex），
 * 取消下方注释并在应用侧引入 katex.min.css，其余代码无需改动。
 */
export const katexPlugin = {
  name: 'katex',
  enabled: false,
  apply (md) {
    // import katex from 'markdown-it-katex'
    // md.use(katex, { throwOnError: false })
  }
}

/**
 * 预留注册位：标题锚点（anchor）
 * 启用步骤：npm i markdown-it-anchor，取消下方注释。
 */
export const anchorPlugin = {
  name: 'anchor',
  enabled: false,
  apply (md) {
    // import anchor from 'markdown-it-anchor'
    // md.use(anchor, { permalink: false })
  }
}

/**
 * 预留注册位：脚注（footnote）
 * 启用步骤：npm i markdown-it-footnote，取消下方注释。
 */
export const footnotePlugin = {
  name: 'footnote',
  enabled: false,
  apply (md) {
    // import footnote from 'markdown-it-footnote'
    // md.use(footnote)
  }
}

/** 插件注册表：按 name 索引 */
export const pluginRegistry = {
  highlight: highlightPlugin,
  'task-lists': taskListsPlugin,
  'table-wrap': tableWrapPlugin,
  katex: katexPlugin,
  anchor: anchorPlugin,
  footnote: footnotePlugin
}

/** 默认启用的插件（顺序即应用顺序） */
export const DEFAULT_PLUGIN_NAMES = ['highlight', 'task-lists', 'table-wrap']

/**
 * 按 name 依次应用插件
 * @param {Object} md markdown-it 实例
 * @param {Array<string>} names 插件名列表，默认 DEFAULT_PLUGIN_NAMES
 * @returns {Array<string>} 实际生效的插件名（未注册 / 预留未启用的会被跳过）
 */
export const applyPlugins = (md, names = DEFAULT_PLUGIN_NAMES) => {
  const applied = []
  for (const name of names) {
    const plugin = pluginRegistry[name]
    if (!plugin) {
      console.warn('[markdown] 未注册的插件: ' + name)
      continue
    }
    if (plugin.enabled === false) {
      // 预留注册位：依赖未安装时不参与渲染
      continue
    }
    plugin.apply(md)
    applied.push(name)
  }
  return applied
}
