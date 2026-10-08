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
        <text>🗑</text>
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
      <view class="redesign-empty__icon"><text class="redesign-empty__emoji">📭</text></view>
      <text class="redesign-empty__text">还没有历史记录</text>
    </view>

    <block v-else>
      <view v-for="group in groupedList" :key="group.label">
        <text class="hist-group-label">{{ group.label }}</text>
        <view v-for="item in group.items" :key="item.id" class="hist-card" @click="onItemClick(item)">
          <view class="tool-avatar" :class="'tool-avatar--' + iconType(item)">
            <text class="ta-emoji">{{ emoji(item) }}</text>
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
            <text class="hist-card__line">输入：{{ brief(item.inputContent) }}</text>
            <text class="hist-card__line">结果：{{ item.status === 1 ? brief(item.outputContent) : (item.errorMsg || '处理失败') }}</text>
            <text class="hist-card__time">{{ formatTime(item.createTime) }}</text>
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

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 16)
}

const brief = (s) => {
  if (!s) return '—'
  const t = String(s).replace(/\s+/g, ' ').trim()
  return t.length > 28 ? t.slice(0, 28) + '…' : t
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
    confirmColor: '#211E1E',
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
    confirmColor: '#C0392B',
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
  background: #F9FAFB;
  padding-bottom: 40rpx;
}

.hist-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 32rpx 32rpx 16rpx;
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
.hist-back__icon { width: 44rpx; height: 44rpx; color: #111827; }
.hist-head__title {
  flex: 1;
  font-size: 48rpx;
  font-weight: 700;
  color: #111827;
}
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
  background: #fff;
  border: 2rpx solid #F3F4F6;
  font-size: 26rpx;
  color: #4B5563;
}
.hist-tab--active {
  background: #3B82F6;
  border-color: #3B82F6;
  color: #fff;
  font-weight: 500;
}

.hist-group-label {
  display: block;
  font-size: 26rpx;
  color: #9CA3AF;
  padding: 24rpx 32rpx 8rpx;
}

.hist-card {
  display: flex;
  gap: 24rpx;
  background: #fff;
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
  color: #111827;
}
.hist-badge {
  display: flex;
  align-items: center;
  height: 44rpx;
  padding: 0 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
}
.hist-badge--ok { background: #D1FAE5; color: #10B981; }
.hist-badge--fail { background: #FEE2E2; color: #EF4444; }
.hist-card__del {
  width: 52rpx;
  height: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}
.hist-del__icon {
  width: 34rpx;
  height: 34rpx;
  color: #9CA3AF;
}

/* 底部加载提示 */
.hist-foot {
  padding: 16rpx 0 32rpx;
  text-align: center;
}
.hist-foot__text {
  font-size: 24rpx;
  color: #9CA3AF;
}

.hist-card__line {
  display: block;
  font-size: 24rpx;
  color: #4B5563;
  margin-top: 8rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.hist-card__time {
  display: block;
  font-size: 22rpx;
  color: #D1D5DB;
  margin-top: 12rpx;
}

.safe-bottom { height: 40rpx; }
</style>
