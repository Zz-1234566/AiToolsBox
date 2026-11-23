<template>
  <view class="hist-page">
    <!-- 头部：标题 + 操作 -->
    <view class="hist-head">
      <view class="hist-back" @click="goBack">
        <svg class="hist-back__icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M15 19L8 12L15 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      <text class="hist-head__title">历史记录</text>
      <view class="hist-head__icon" @click="onClearAll">
        <svg class="hist-trash" viewBox="0 0 24 24">
          <path d="M4 7h16M10 4h4M6 7l1 13h10l1-13" fill="none" stroke="#5C5C66" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M10 11v6M14 11v6" fill="none" stroke="#5C5C66" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
      </view>
    </view>

    <!-- 分类筛选 -->
    <scroll-view scroll-x class="hist-tabs">
      <view v-for="t in tabs" :key="t.value" class="hist-tab"
            :class="{ 'hist-tab--active': activeTab === t.value }"
            @click="activeTab = t.value">
        {{ t.label }}
      </view>
    </scroll-view>

    <!-- 列表（按天分组） -->
    <view v-if="loading" class="redesign-empty"><text class="redesign-empty__text">加载中…</text></view>
    <view v-else-if="groupedList.length === 0" class="redesign-empty">
      <view class="redesign-empty__icon">
        <svg viewBox="0 0 24 24" style="width:76rpx;height:76rpx;fill:none;stroke:var(--text-tertiary);stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round"><path d="M4 5h16v14H4z M4 13h4l1.5 2h5L16 13h4"/></svg>
      </view>
      <text class="redesign-empty__text">还没有历史记录</text>
      <text class="redesign-empty__sub">做一次转换后，结果会自动存在这里，换设备也能找到。</text>
      <view class="redesign-empty__cta" @click="goTools"><text>去用工具</text></view>
    </view>

    <block v-else>
      <view v-for="group in groupedList" :key="group.label">
        <text class="hist-group-label">{{ group.label }}</text>
        <view v-for="item in group.items" :key="item.id" class="hist-card" @click="onItemClick(item)">
          <view class="tool-avatar" :class="'tool-avatar--' + iconType(item)">
            <svg class="ta-svg" viewBox="0 0 24 24"><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z" fill="none" stroke="#fff" stroke-width="1.8" stroke-linejoin="round"/><path d="M14 3v5h5" fill="none" stroke="#fff" stroke-width="1.8" stroke-linejoin="round"/></svg>
          </view>
          <view class="hist-card__body">
            <view class="hist-card__row">
              <text class="hist-card__title">{{ getToolName(item) }}</text>
              <view class="hist-badge" :class="item.status === 1 ? 'hist-badge--ok' : 'hist-badge--fail'">
                <text>{{ item.status === 1 ? '✓ 成功' : '✗ 失败' }}</text>
              </view>
              <view class="hist-card__del" @click.stop="onDelete(item)">
                <svg class="hist-del__icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M3 6H21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <path d="M19 6V20C19 20.5523 18.5523 21 18 21H6C5.44772 21 5 20.5523 5 20V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <path d="M10 11V17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <path d="M14 11V17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                </svg>
              </view>
            </view>
            <text class="hist-card__line">输入：{{ inputLine(item) }}</text>
            <!-- 产物按类型分情况：文本截断展示；图片/音视频/文件显示图标 + 文件名，
                 避免把 COS 签名 URL 当正文铺出来。判定统一走 utils/fileAction。 -->
            <text class="hist-card__line">
              结果：{{ item.status === 1 ? outputLine(item) : (item.errorMsg || '处理失败') }}
            </text>
            <view class="hist-card__foot">
              <text class="hist-card__time">{{ formatTime(item.createTime) }}</text>
              <text v-if="item.duration" class="hist-card__dur">耗时 {{ fmtDuration(item.duration) }}</text>
            </view>
          </view>
        </view>
      </view>
    </block>

    <!-- 底部加载状态 -->
    <view v-if="!loading && historyList.length > 0" class="hist-foot">
      <text v-if="loadingMore" class="hist-foot__text">加载中…</text>
      <text v-else-if="!hasMore" class="hist-foot__text">没有更多了</text>
    </view>

    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { historyListApi, historyDeleteApi, historyClearAllApi } from '@/api/history'
import { requireLogin } from '@/utils/auth'
import { safeBack } from '@/utils/pageTransition'
import { isFileUrl, extensionOf, fileKeyOf, fileNameFromUrl, isAudioExt } from '@/utils/fileDisplayName'
import { outputKindOf, outputBrief, outputIcon } from '@/utils/fileAction'

const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(true)
const historyList = ref([])
const activeTab = ref('all')
/** 每页条数 */
const PAGE_SIZE = 10

const tabs = [
  { label: '全部', value: 'all' },
  { label: 'AI办公', value: 'AI办公助手' }
]

const getToolName = (item) => item.toolName || item.aiCode || '未知工具'

/** duration 单位毫秒 → 友好时长（历史记录卡片展示） */
const fmtDuration = (ms) => {
  const n = Math.max(0, Math.round(Number(ms) || 0))
  return n >= 1000 ? (n / 1000).toFixed(1) + 's' : n + 'ms'
}

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 16)
}

const brief = (s) => {
  if (!s) return '—'
  const t = String(s).replace(/\s+/g, ' ').trim()
  return t.length > 28 ? t.slice(0, 28) + '…' : t
}

/**
 * 「输入」一行：文件类输入（inputContent 就是整条 COS 签名 URL）显示文件名，
 * 音频额外带 🎵 提示；纯文本输入沿用 brief 截断，与历史详情页展示口径一致。
 * 整卡仍跳详情页，列表页不做下载。
 */
const inputLine = (item) => {
  const raw = item && item.inputContent
  if (!raw || !isFileUrl(raw)) return brief(raw)
  const meta = (item.files || []).find((f) => f && f.fileUrl && fileKeyOf(f.fileUrl) === fileKeyOf(raw))
  const name = (meta && meta.fileName) || fileNameFromUrl(raw) || '查看文件'
  const prefix = isAudioExt(extensionOf(name)) ? '🎵 ' : ''
  return prefix + (name.length > 24 ? name.slice(0, 24) + '…' : name)
}

/**
 * 「结果」一行：按产物类型分情况展示。
 * - 文本（AI 摘要 / 纪要 / 提取文本）：截断显示，与历史详情页口径一致；
 * - 图片 / 音视频 / 文件：显示「图标 + 文件名」，不铺 COS 签名 URL。
 * 整卡仍然跳详情页，列表页不做下载（见下方注释）。
 */
const outputLine = (item) => {
  const out = item && item.outputContent
  const kind = outputKindOf(out)
  if (kind === 'text') return brief(out)
  return `${outputIcon(out)} ${outputBrief(out, 24)}`
}

/** 工具图标类型（按 aiCode 粗分，与设计稿渐变对应） */
const iconType = (item) => {
  const c = item.aiCode || ''
  if (c.includes('ocr') || c.includes('recognize') || c.includes('invoice') || c.includes('receipt')) return 'ocr'
  if (c.includes('image') || c.includes('photo') || c.includes('portrait') || c.includes('bg')) return 'image'
  if (c.includes('file-reader') || c.includes('doc')) return 'doc'
  if (c.includes('audio') || c.includes('transcribe') || c.includes('meeting')) return 'text'
  return 'text'
}
const emoji = (item) => {
  const m = { ocr: '🖨', image: '🖼️', doc: '📄', text: '📝', code: '✨' }
  return m[iconType(item)] || '📝'
}

/** 按天分组：今天 / 昨天 / 更早 */
const groupedList = computed(() => {
  // 工具已收敛为单一分类（AI办公助手），tab 只剩「全部」，无需按类过滤
  const list = historyList.value

  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfYesterday = startOfToday - 86400000
  const groups = [
    { label: '今天', items: [] },
    { label: '昨天', items: [] },
    { label: '更早', items: [] }
  ]
  list.forEach(item => {
    const t = item.createTime ? new Date(String(item.createTime).replace('T', ' ').replace(/-/g, '/')).getTime() : 0
    if (t >= startOfToday) groups[0].items.push(item)
    else if (t >= startOfYesterday) groups[1].items.push(item)
    else groups[2].items.push(item)
  })
  return groups.filter(g => g.items.length > 0)
})

const fetchHistory = async (append = false) => {
  if (append) {
    if (loadingMore.value || !hasMore.value) return
    loadingMore.value = true
  } else {
    loading.value = true
  }
  try {
    const offset = append ? historyList.value.length : 0
    const res = await historyListApi(PAGE_SIZE, offset)
    const list = (res && res.data) || []
    if (append) {
      // 去重兜底：分页期间若有新增/删除，offset 可能错位导致重复
      const existIds = new Set(historyList.value.map((h) => String(h.id)))
      historyList.value = historyList.value.concat(list.filter((h) => !existIds.has(String(h.id))))
    } else {
      historyList.value = list
    }
    // 返回不足一页 → 没有更多了
    hasMore.value = list.length >= PAGE_SIZE
  } catch (err) {
    // request.js 已统一提示错误，这里清空列表避免残留旧数据
    if (!append) historyList.value = []
    hasMore.value = false
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

onShow(() => {
  if (!requireLogin()) return
  hasMore.value = true
  fetchHistory()
})

onPullDownRefresh(async () => {
  hasMore.value = true
  await fetchHistory()
  uni.stopPullDownRefresh()
})

/** 滚动到底部：自动加载下一页 */
onReachBottom(() => {
  fetchHistory(true)
})

// H5 兜底：uni 的 onReachBottom 依赖页面滚动监听注册，实测在 H5 下未生效，
// 改用原生 scroll 事件判定触底（App/小程序仍走上面的 onReachBottom）
// #ifdef H5
let h5ScrollHandler = null
onMounted(() => {
  h5ScrollHandler = () => {
    const doc = document.documentElement
    const distance = 80
    if (window.scrollY + window.innerHeight + distance >= doc.scrollHeight) {
      fetchHistory(true)
    }
  }
  window.addEventListener('scroll', h5ScrollHandler, { passive: true })
})
onUnmounted(() => {
  if (h5ScrollHandler) window.removeEventListener('scroll', h5ScrollHandler)
})
// #endif

const goTools = () => uni.switchTab({ url: '/pages/category' })

const onItemClick = (item) => {
  if (!item || !item.id) return
  uni.navigateTo({ url: `/pages/history-detail?id=${item.id}&aiCode=${encodeURIComponent(item.aiCode || '')}` })
}

/** 返回：栈内有上一页则返回，否则回「我的」页（本页入口来源） */
const goBack = () => safeBack('/pages/my')

const onDelete = (item) => {
  uni.showModal({
    title: '删除记录',
    content: '确定要删除这条历史记录吗？',
    confirmColor: 'var(--text-primary)',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await historyDeleteApi(item.id)
        historyList.value = historyList.value.filter((h) => h.id !== item.id)
        uni.showToast({ title: '删除成功', icon: 'none' })
      } catch (err) {
        // request.js 已统一提示错误
      }
    }
  })
}

const onClearAll = () => {
  uni.showModal({
    title: '清空历史记录',
    content: '确定要清空全部历史记录吗？清空后不可恢复。',
    confirmText: '清空',
    confirmColor: 'var(--color-danger)',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await historyClearAllApi()
        historyList.value = []
        uni.showToast({ title: '已清空', icon: 'none' })
      } catch (err) {
        // request.js 已统一提示错误
      }
    }
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.hist-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: 40rpx;
}

.hist-head {
  /* App 端自绘导航栏需避开状态栏/刘海（uni 注入 --status-bar-height，H5 端为 0）
     ⚠️ 必须用四值简写：`padding: 32rpx 32rpx 16rpx` 简写会重置 padding-top，
        把状态栏避让覆盖掉。 */
  padding: calc(var(--status-bar-height, 0px) + 32rpx) 32rpx 16rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.hist-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: -12rpx;
  border-radius: 50%;
}
.hist-back__icon { width: 44rpx; height: 44rpx; color: var(--text-primary); }
.hist-head__title {
  flex: 1;
  font-size: 48rpx;
  font-weight: 700;
  color: var(--text-primary);
}
.hist-trash { width: 40rpx; height: 40rpx; }
.hist-head__icon { font-size: 40rpx; padding: 8rpx; }

.hist-tabs {
  white-space: nowrap;
  padding: 0 32rpx 24rpx;
}
.hist-tab {
  display: inline-block;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 32rpx;
  margin-right: 16rpx;
  border-radius: 9999rpx;
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  font-size: 26rpx;
  color: var(--text-secondary);
}
.hist-tab--active {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
  color: #fff;
  font-weight: 500;
}

.hist-group-label {
  display: block;
  font-size: 26rpx;
  color: var(--text-tertiary);
  padding: 24rpx 32rpx 8rpx;
}

.hist-card {
  display: flex;
  gap: 24rpx;
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  margin: 0 32rpx 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.hist-card__body { flex: 1; min-width: 0; }
.hist-card__row { display: flex; align-items: center; gap: 16rpx; }
.hist-card__title {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-primary);
}
.hist-badge {
  display: flex;
  align-items: center;
  height: 44rpx;
  padding: 0 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
}
.hist-badge--ok { background: var(--color-success-bg); color: var(--color-success); }
.hist-badge--fail { background: var(--color-danger-bg); color: var(--color-danger); }
.hist-card__del {
  /* 触控热区 ≥ 44px（88rpx）：删除是破坏性操作，
     热区太小会误触到相邻内容。图标本身仍保持 34rpx 不变，只扩大可点范围。 */
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  /* 视觉上不因热区变大而显得突兀：负 margin 抵消布局占位 */
  margin: -18rpx 0;
}
.hist-del__icon {
  width: 34rpx;
  height: 34rpx;
  color: var(--text-tertiary);
}

/* 底部加载提示 */
.hist-foot {
  padding: 16rpx 0 32rpx;
  text-align: center;
}
.hist-foot__text {
  font-size: 24rpx;
  color: var(--text-tertiary);
}

.hist-card__line {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary);
  margin-top: 8rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.hist-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}
.hist-card__time {
  font-size: 22rpx;
  color: var(--text-tertiary);
}
.hist-card__dur {
  font-size: 22rpx;
  color: var(--brand-primary);
}

.safe-bottom { height: 40rpx; }

/* ===== v6 统一：24px 左对齐标题 ===== */
.hist-head {
  background: var(--bg-page) !important;
  border-bottom: none !important;
  justify-content: flex-start !important;
  padding-left: 32rpx !important;
  padding-right: 32rpx !important;
}
.hist-head__title {
  position: static !important;
  left: auto !important;
  right: auto !important;
  transform: none !important;
  flex: 1 !important;
  min-width: 0 !important;
  text-align: left !important;
  font-size: 48rpx !important;
  font-weight: 700 !important;
  letter-spacing: -1rpx !important;
  color: var(--text-primary) !important;
  margin: 0 !important;
}
</style>
