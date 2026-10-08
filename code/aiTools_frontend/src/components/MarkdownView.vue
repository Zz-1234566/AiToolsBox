<template>
  <view class="markdown-view" :class="themeClass">
    <!-- 渲染模式切换靠 :key 强制整块重挂载：
         纯文本 ↔ 富文本的根节点同名（都是 view），不带 key 时 Vue 会原地 patch 复用节点，
         导致浏览器不重新解析 innerHTML、样式错乱；显式给不同 key 可保证销毁重建。 -->
    <!-- 推字阶段（streaming=true）：只渲染转义后的纯文本，不做任何 markdown 解析 -->
    <view
      v-if="streaming"
      :key="'plain'"
      class="markdown-view__plain"
      v-html="plainHtml"
    ></view>
    <template v-else>
      <!-- #ifdef H5 -->
      <!-- H5：v-html 注入 DOMPurify 白名单清洗后的 HTML -->
      <view :key="'rich'" class="markdown-body" v-html="h5Html"></view>
      <!-- #endif -->
      <!-- #ifndef H5 -->
      <!-- 非 H5（App / 小程序）：无 v-html，用 mp-html 渲染同一份 HTML 字符串 -->
      <mp-html
        :key="'rich'"
        :content="richHtml"
        :tag-style="tagStyle"
        :scroll-table="true"
        :selectable="true"
      />
      <!-- #endif -->
    </template>
  </view>
</template>

<script setup>
/**
 * MarkdownView —— markdown 富文本渲染组件（H5 / App 双端统一入口）
 *
 * props：
 *   source    markdown 源文本（必填）
 *   streaming 是否处于流式推字阶段；true 时只渲染转义纯文本，结束后渲染富文本
 *   theme     主题名，默认 'light'，传 'dark' 走深色主题
 *
 * 渲染链路：markdown-it 解析（utils/markdown）→ DOMPurify 清洗（H5）→ v-html / mp-html
 * 渲染模式切换由组件内部处理（:key 重挂载），外部无需再传 renderKey。
 */
import { computed } from 'vue'
import { renderMarkdown, sanitizeHtml } from '@/utils/markdown'

// 统一 markdown 主题（全局样式）+ highlight.js 浅色主题（按需只引一个，控制体积）
import '@/styles/markdown.css'
import 'highlight.js/styles/github.css'

// #ifndef H5
import MpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html.vue'
// #endif

const props = defineProps({
  source: {
    type: String,
    required: true
  },
  streaming: {
    type: Boolean,
    default: false
  },
  theme: {
    type: String,
    default: 'light'
  }
})

/**
 * 纯文本转义（推字阶段与降级兜底共用）
 * 转义顺序不可颠倒：先 & 再 < >，否则会把已生成的实体二次转义
 */
const escapePlainText = (txt) => {
  if (!txt) return ''
  return String(txt)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}

/**
 * 非 H5 端 mp-html 的标签内联样式
 * 原因：App / 小程序的 rich-text 节点不支持外层 CSS 选择器，标签样式必须内联注入
 */
const tagStyle = {
  h1: 'font-size:22px;font-weight:700;margin:20px 0 12px;line-height:1.4;',
  h2: 'font-size:18px;font-weight:700;margin:20px 0 12px;line-height:1.4;',
  h3: 'font-size:16px;font-weight:700;margin:20px 0 12px;line-height:1.4;',
  h4: 'font-size:15px;font-weight:700;margin:16px 0 10px;line-height:1.4;',
  h5: 'font-size:14px;font-weight:700;margin:16px 0 10px;line-height:1.4;',
  h6: 'font-size:13px;font-weight:700;margin:16px 0 10px;line-height:1.4;',
  p: 'margin:8px 0;line-height:1.75;',
  ul: 'margin:8px 0;padding-left:24px;',
  ol: 'margin:8px 0;padding-left:24px;',
  li: 'margin:4px 0;line-height:1.7;',
  strong: 'font-weight:700;',
  em: 'font-style:italic;',
  del: 'text-decoration:line-through;',
  code: 'background:#F3F4F6;padding:1px 4px;border-radius:3px;font-family:monospace;font-size:12px;color:#BE185D;',
  pre: 'background:#F6F8FA;border:1px solid #E5E7EB;border-radius:8px;padding:12px;overflow-x:auto;font-size:12.5px;',
  blockquote: 'border-left:3px solid #3B82F6;padding:6px 12px;margin:12px 0;background:rgba(59,130,246,0.04);',
  a: 'color:#3B82F6;text-decoration:none;',
  hr: 'border:none;border-top:1px solid #E5E7EB;margin:16px 0;',
  img: 'max-width:100%;height:auto;border-radius:6px;',
  table: 'border-collapse:collapse;width:100%;font-size:13px;',
  th: 'border:1px solid #E5E7EB;padding:8px 12px;background:#F3F4F6;font-weight:700;text-align:left;',
  td: 'border:1px solid #E5E7EB;padding:8px 12px;text-align:left;vertical-align:top;'
}

// markdown 源文本 → HTML（走 utils/markdown 单例渲染器，含 highlight / 任务列表 / 表格包裹）
const renderedHtml = computed(() => renderMarkdown(props.source || ''))
// H5 侧按白名单清洗（非 H5 恒返回空串）
const safeHtml = computed(() => sanitizeHtml(renderedHtml.value))
// H5 注入内容：清洗结果为空（异常 / 文档为空）时降级为转义纯文本，避免白屏
const h5Html = computed(() => safeHtml.value || escapePlainText(props.source))
// 非 H5：清洗按设计不返回 HTML，回退为解析后的 HTML 字符串交给 mp-html
const richHtml = computed(() => safeHtml.value || renderedHtml.value)
// 推字阶段：只做转义，不解析 markdown
const plainHtml = computed(() => escapePlainText(props.source))
// 主题类名（供 styles/markdown.css 中的深色主题覆盖）
const themeClass = computed(() => (props.theme === 'dark' ? 'markdown-view--dark' : 'markdown-view--light'))
</script>

<style scoped>
.markdown-view {
  width: 100%;
}
/* 推字阶段：保留换行与空白，避免纯文本横向溢出 */
.markdown-view__plain {
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-primary, #111827);
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
}
</style>
