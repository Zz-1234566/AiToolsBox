<template>
  <view class="batch-cards">
    <view
      v-for="item in items"
      :key="(item.index || 0) + '-' + item.fileName"
      class="card"
      :class="{ 'card-failed': item.status === 'failed' }"
    >
      <!-- 头部：序号 + 文件名 + 状态徽标 + 复制全部按钮 -->
      <view class="card-header">
        <view class="card-title">
          <text class="card-index">[{{ item.index || '?' }}/{{ total }}]</text>
          <text class="card-filename">{{ item.fileName }}</text>
        </view>
        <view class="card-right">
          <view class="card-status" :class="item.status === 'failed' ? 'status-failed' : 'status-ok'">
            <text>{{ item.status === 'failed' ? '失败' : '完成' }}</text>
            <text v-if="item.costMs" class="card-cost"> {{ item.costMs }}ms</text>
          </view>
          <view v-if="item.status !== 'failed' && hasContent(item)" class="action-btn" @click="onCopyAll(item)">
            <svg class="action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="9" y="9" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.5"/>
              <path d="M5 15H4C3.46957 15 2.96086 14.7893 2.58579 14.4142C2.21071 14.0391 2 13.5304 2 13V4C2 3.46957 2.21071 2.96086 2.58579 2.58579C2.96086 2.21071 3.46957 2 4 2H13C13.5304 2 14.0391 2.21071 14.4142 2.58579C14.7893 2.96086 15 3.46957 15 4V5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
        </view>
      </view>

      <!-- 失败：显示错误 -->
      <view v-if="item.status === 'failed'" class="card-error">
        <text>{{ item.errorMsg || '处理失败' }}</text>
      </view>

      <!-- 成功：按字段拆解展示 -->
      <view v-else class="card-body">
        <view v-if="parsedSections(item).length" class="sections">
          <view
            v-for="(sec, sIdx) in parsedSections(item)"
            :key="sIdx"
            class="section"
          >
            <view class="section-head">
              <text v-if="sec.title" class="section-title">{{ sec.title }}</text>
              <view class="section-copy" @click="onCopySection(sec)">
                <text>复制本段</text>
              </view>
            </view>
            <!-- 字段对列表 -->
            <view v-if="sec.fields && sec.fields.length" class="field-list">
              <view v-for="(f, fIdx) in sec.fields" :key="fIdx" class="field-row">
                <text class="field-label">{{ f.label }}</text>
                <text class="field-value">{{ f.value }}</text>
              </view>
            </view>
            <!-- 没有字段对：原样展示纯文本 -->
            <view v-else class="section-text">
              <text>{{ sec.rawText }}</text>
            </view>
          </view>
        </view>
        <!-- 拆不出结构时降级：原样输出整段 -->
        <view v-else class="card-output">
          <text>{{ item.output || '(无输出)' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // items: [{ index, fileName, status, costMs, errorMsg, output }]
  items: {
    type: Array,
    default: () => []
  },
  total: {
    type: Number,
    default: 0
  }
})

// 是否有可复制内容
const hasContent = (item) => !!(item && item.output && item.output.trim())

// 把单个 item.output 解析成 sections
// 每个 section：{ title, fields: [{label, value}], rawText }
// 拆不出结构时返回 []
const parsedSectionsCache = new WeakMap()
const parsedSections = (item) => {
  if (!hasContent(item)) return []
  if (parsedSectionsCache.has(item)) return parsedSectionsCache.get(item)
  const result = parseSections(item.output)
  parsedSectionsCache.set(item, result)
  return result
}

/**
 * 解析 AI 输出文本成 sections 列表
 * 通用规则（不硬编码字段名，兼容多行"字段名：值"格式）：
 *   1. 不再按 --- 切大段、按 ### 取子标题（AI 输出已结构化）
 *   2. 通用字段对正则：匹配"字段名：值"
 *      - 兼容 **字段** 强调（前缀后缀 `*` 可选）
 *      - 兼容中英文冒号"："和":"
 *      - 字段缺失时输出"字段名："（冒号后留空）
 *   3. 匹配不到字段对的行：原样保留为 rawText
 *   4. 整个 AI 输出作为单一 section 返回
 */
const parseSections = (text) => {
  if (!text) return []
  // 标准化换行
  const normalized = String(text).replace(/\r\n/g, '\n').trim()
  if (!normalized) return []

  // 通用字段对正则：
  //   ^\s*         行首允许空格
  //   \*?\*?       可选 0-2 个 `*`（吃 **强调**）
  //   ([^*\n：:：]+?)  字段名（非贪婪，不含 `*`/换行/冒号）
  //   \*?\*?       可选尾部 `*`
  //   \s*[：:]\s*   中英文冒号 + 空格
  //   (.*?)\s*$     值到行末（去尾空格）
  const FIELD_RE = /^\s*\*?\*?([^*\n：:：]+?)\*?\*?\s*[：:]\s*(.*?)\s*$/

  const lines = normalized.split('\n')
  const fields = []
  const textOnlyLines = []
  for (const line of lines) {
    if (!line.trim()) continue
    const fm = FIELD_RE.exec(line)
    if (fm) {
      fields.push({ label: fm[1].trim(), value: fm[2].trim() })
    } else {
      textOnlyLines.push(line)
    }
  }

  const rawText = textOnlyLines.join('\n').trim()
  return [{
    title: '',
    fields,
    rawText
  }].filter(s => s.fields.length || s.rawText)
}

// ========== 复制逻辑 ==========
const doCopy = (text) => {
  if (!text) return
  // 优先走 uni（HBuilderX / 小程序 / APP 全端兼容）
  if (typeof uni !== 'undefined' && uni.setClipboardData) {
    uni.setClipboardData({
      data: text,
      success: () => uni.showToast({ title: '已复制', icon: 'none' }),
      fail: () => fallbackCopy(text)
    })
  } else {
    fallbackCopy(text)
  }
}

const fallbackCopy = (text) => {
  // H5 端兜底：Clipboard API
  if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(
      () => uni.showToast({ title: '已复制', icon: 'none' }),
      () => uni.showToast({ title: '复制失败', icon: 'none' })
    )
  } else {
    uni.showToast({ title: '复制失败', icon: 'none' })
  }
}

// 复制整张卡片 output
const onCopyAll = (item) => {
  doCopy(item.output || '')
}

// 复制单个 section：把字段对重新拼成 markdown 格式（保持原文风）
const onCopySection = (sec) => {
  const lines = []
  if (sec.title) lines.push(`### ${sec.title}`)
  if (sec.fields && sec.fields.length) {
    for (const f of sec.fields) {
      lines.push(`- **${f.label}**：${f.value}`)
    }
  }
  if (sec.rawText) {
    if (lines.length) lines.push('')
    lines.push(sec.rawText)
  }
  doCopy(lines.join('\n'))
}
</script>

<style lang="scss" scoped>
.batch-cards {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.card {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  box-shadow: $shadow-card;
  border-left: 6rpx solid $text-primary;
}

.card-failed {
  border-left-color: $color_danger;
  background-color: $color_danger_bg;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
  gap: $spacing-sm;
}

.card-title {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
  overflow: hidden;
}

.card-index {
  font-size: $font-size-sm;
  color: $text-tertiary;
  flex-shrink: 0;
}

.card-filename {
  font-size: $font-size-md;
  font-weight: 600;
  color: $text-primary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-shrink: 0;
}

.card-status {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border-radius: $radius-pill;
  font-size: $font-size-sm;
}

.status-ok {
  background-color: $color_success_bg;
  color: $color_success;
}

.status-failed {
  background-color: $color_danger_soft;
  color: $color_danger;
}

.card-cost {
  font-size: $font-size-xs;
  color: $text-tertiary;
  margin-left: 4rpx;
}

.action-btn {
  width: 56rpx;
  height: 56rpx;
  border-radius: $radius-pill;
  background-color: $bg-gray;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  &:active {
    background-color: $border-color;
  }

  .action-icon {
    width: 32rpx;
    height: 32rpx;
    color: $text-secondary;
  }
}

.card-error {
  background-color: $color_danger_bg;
  border-radius: $radius-md;
  padding: $spacing-md;
  font-size: $font-size-md;
  color: $color_danger;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.sections {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.section {
  background-color: $bg-gray;
  border-radius: $radius-md;
  padding: $spacing-md;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
  gap: 12rpx;
}

.section-title {
  font-size: $font-size-md;
  font-weight: 600;
  color: $text-primary;
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.section-copy {
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  border-radius: $radius-sm;
  background-color: $bg-white;
  border: 1rpx solid $border-color;
  font-size: $font-size-xs;
  color: $text-secondary;

  &:active {
    background-color: $border-color;
  }
}

.field-list {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.field-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  font-size: $font-size-md;
  line-height: 1.6;
}

.field-label {
  color: $text-tertiary;
  flex-shrink: 0;
  min-width: 140rpx;
}

.field-value {
  color: $text-primary;
  flex: 1;
  word-break: break-word;
}

.section-text {
  font-size: $font-size-md;
  color: $text-primary;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.card-output {
  background-color: $bg-gray;
  border-radius: $radius-md;
  padding: $spacing-md;
  font-size: $font-size-md;
  color: $text-primary;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 800rpx;
  overflow-y: auto;
}
</style>
