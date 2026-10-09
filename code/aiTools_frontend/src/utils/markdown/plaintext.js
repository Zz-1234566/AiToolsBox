/**
 * Markdown → 纯文本（复制到微信 / 钉钉 / 短信框这类不支持 Markdown 的地方用）。
 *
 * 为什么走 markdown-it 的 token 流而不是正则：
 *   - 正则剥语法在「表格 / 嵌套列表 / 代码块里的 * 和 # 」上必错；
 *   - markdown-it 已完成语法解析，块级结构（标题层级、列表嵌套、表格单元格、代码块边界）
 *     是结构化 token，按块渲染纯文本能保住格式语义；
 *   - 不依赖任何运行时 DOM（无 document / 无 v-html），App 端同样可用。
 *
 * 复用现有渲染管线（utils/markdown/index.js 的 getMarkdownRenderer 单例），不引入新依赖。
 */
import { getMarkdownRenderer } from './index.js'

/** inline token → 可见文字（剥掉所有语法标记；图片只留 alt，链接只留锚文本） */
const inlineText = (tokens) => {
  let out = ''
  for (const t of tokens || []) {
    switch (t.type) {
      case 'text':
      case 'code_inline':
        out += t.content
        break
      case 'softbreak':
      case 'hardbreak':
        out += '\n'
        break
      case 'image':
        out += t.content || ''
        break
      case 'html_inline':
        // html:false 配置下不产生；真出现也跳过，避免把标签带进纯文本
        break
      default:
        // link_open / strong_open / em_open 等零文本标记 → 递归取 children
        if (t.children) out += inlineText(t.children)
        break
    }
  }
  return out
}

/** 列表项 → 单行文本（缩进 + 实际标记，保留有序列表的 1. / 2. 序号） */
const formatItem = (node) => {
  const text = node.text.join('').replace(/[ \t]+/g, ' ').trim()
  if (!text) return ''
  return '  '.repeat(Math.max(0, node.level - 1)) + node.mark + ' ' + text
}

/** 块节点 → 文本行 */
const renderBlock = (node) => {
  const text = node.text.join('').replace(/[ \t]+/g, ' ').trim()
  switch (node.kind) {
    case 'heading':
      // '# 标题' → '标题'（纯文本里 # 是噪声）
      return text
    case 'listitem':
      return formatItem(node)
    default:
      return text
  }
}
const tableToText = (slice) => {
  const rows = []
  for (let i = 0; i < slice.length; i++) {
    if (slice[i].type !== 'tr_open') continue
    const cells = []
    for (let j = i + 1; j < slice.length && slice[j].type !== 'tr_close'; j++) {
      if (slice[j].type === 'inline') cells.push(inlineText(slice[j].children).trim())
    }
    if (cells.length) rows.push(cells)
  }
  if (!rows.length) return ''
  const lines = rows.map(cells => cells.join(' ｜ '))
  if (rows.length > 1) lines.splice(1, 0, rows[0].map(() => '───────').join('─┼─'))
  return lines.join('\n')
}

/**
 * Markdown → 纯文本
 * @param {String} src markdown 源文本
 * @returns {String} 可直接粘贴的纯文本（空输入返回空串）
 */
export const markdownToPlainText = (src) => {
  if (!src) return ''
  let tokens
  try {
    tokens = getMarkdownRenderer().parse(String(src), {})
  } catch (e) {
    console.error('[markdown] plain text failed:', e)
    return String(src)
  }

  const lines = []
  /** 块栈：root 恒在栈底，listitem 记录嵌套层级 */
  const stack = [{ kind: 'root', text: [] }]
  const top = () => stack[stack.length - 1]
  const add = (s) => { if (s) top().text.push(s) }

  /** 把一个块渲染成最终文本行（非空才写） */
  const emit = (node) => {
    const line = renderBlock(node)
    if (line) lines.push(line)
  }

  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i]
    switch (t.type) {
      /* ---- 标题 ---- */
      case 'heading_open':
        stack.push({ kind: 'heading', level: Number(t.tag.slice(1)), text: [] })
        break
      case 'heading_close':
        emit(stack.pop())
        break

      /* ---- 段落 ---- */
      case 'paragraph_open':
        stack.push({ kind: 'paragraph', text: [] })
        break
      case 'paragraph_close': {
        const node = stack.pop()
        // 列表项内部的段落不单独成行，合并回 listitem
        if (top().kind === 'listitem') top().text.push(node.text.join(''))
        else emit(node)
        break
      }

      /* ---- 列表 ---- */
      case 'bullet_list_open':
      case 'ordered_list_open':
        // items 缓冲本列表已渲染的各行：列表整体关闭时再统一落盘，
        // 否则嵌套子列表会比父项先输出，顺序会乱。
        stack.push({ kind: 'list', items: [], ordered: t.type === 'ordered_list_open', n: 0 })
        break
      case 'bullet_list_close':
      case 'ordered_list_close': {
        const list = stack.pop()
        const rendered = list.items.filter(Boolean)
        if (!rendered.length) break
        if (top().kind === 'listitem') {
          // 嵌套列表：并回父列表项
          top().text.push('\n' + rendered.join('\n'))
        } else {
          rendered.forEach(line => lines.push(line))
        }
        break
      }
      case 'list_item_open': {
        // markdown-it 的 mark 在 inline token 上，list_item_open 取不到；
        // 有序列表的序号自己数（从 1 开始，与源文案的编号一致）。
        const listNode = [...stack].reverse().find(x => x.kind === 'list')
        let mark = '·'
        if (listNode && listNode.ordered) {
          listNode.n = (listNode.n || 0) + 1
          mark = listNode.n + '.'
        }
        // level 只数「祖先 list」减 1：嵌套列表实际位于父 listitem 内部，
        // 直接用栈里 list 的总数会把本层也算进去，缩进多一级。
        const listDepth = stack.filter(x => x.kind === 'list').length
        stack.push({
          kind: 'listitem',
          level: Math.max(1, listDepth - 1),
          mark,
          text: []
        })
        break
      }
      case 'list_item_close': {
        const node = stack.pop()
        const line = formatItem(node)
        if (top().kind === 'list') top().items.push(line)
        break
      }

      /* ---- 引用：内部块正常渲染，纯文本里不额外加标记 ---- */
      case 'blockquote_open':
        stack.push({ kind: 'quote', text: [] })
        break
      case 'blockquote_close':
        stack.pop()
        break

      /* ---- 文本 ---- */
      case 'inline':
        add(inlineText(t.children))
        break

      /* ---- 代码块：保留原始代码，不加反引号 ---- */
      case 'fence':
      case 'code_block': {
        const code = (t.content || '').replace(/\n$/, '')
        if (code) lines.push(code)
        break
      }

      /* ---- 分隔线 ---- */
      case 'hr':
        lines.push('────────')
        break

      /* ---- 表格：整段扫描到配对的 table_close 再一次性渲染 ---- */
      case 'table_open': {
        let depth = 1
        let j = i + 1
        while (j < tokens.length && depth > 0) {
          if (tokens[j].type === 'table_open') depth++
          else if (tokens[j].type === 'table_close') depth--
          j++
        }
        emit({ kind: 'table', text: [tableToText(tokens.slice(i + 1, j - 1))] })
        i = j - 1
        break
      }
      case 'table_close':
        break   // 已由 table_open 整段消费

      /* ---- 原始 HTML / 其他零文本 token：跳过 ---- */
      default:
        break
    }
  }

  return lines.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}