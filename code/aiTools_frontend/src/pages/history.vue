<template>
  <view class="hist-page">
    <!-- 头部：标题 + 操作 -->
    <view class="hist-head">
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
              <text class="hist-card__more" @click.stop="onDelete(item)">···</text>
            </view>
            <text class="hist-card__line">输入：{{ brief(item.inputContent) }}</text>
            <text class="hist-card__line">结果：{{ item.status === 1 ? brief(item.outputContent) : (item.errorMsg || '处理失败') }}</text>
            <text class="hist-card__time">{{ formatTime(item.createTime) }}</text>
          </view>
        </view>
      </view>
    </block>

    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { historyListApi, historyDeleteApi, historyClearAllApi } from '@/api/history'
import { requireLogin } from '@/utils/auth'

const loading = ref(false)
const historyList = ref([])
const activeTab = ref('all')

const tabs = [
  { label: '全部', value: 'all' },
  { label: 'AI办公', value: 'AI办公助手' },
  { label: '图片', value: '图片创意工具' },
  { label: '效率', value: '效率小工具' }
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
  const list = activeTab.value === 'all'
    ? historyList.value
    : historyList.value.filter(h => {
        const c = h.aiCode || ''
        if (activeTab.value === 'AI办公助手') {
          return !c.includes('image') && !c.includes('photo') && !c.includes('portrait') && !c.includes('bg')
            && !['todo-list', 'pomodoro', 'password-gen', 'qr-code-gen'].includes(c)
        }
        if (activeTab.value === '图片创意工具') {
          return c.includes('image') || c.includes('photo') || c.includes('portrait') || c.includes('bg') || c === 'qr-code-gen'
        }
        if (activeTab.value === '效率小工具') {
          return ['todo-list', 'pomodoro', 'password-gen'].includes(c)
        }
        return true
      })

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

const fetchHistory = async () => {
  loading.value = true
  try {
    const res = await historyListApi()
    historyList.value = res.data || []
  } catch (err) {
    // request.js 已统一提示错误，这里清空列表避免残留旧数据
    historyList.value = []
  } finally {
    loading.value = false
  }
}

onShow(() => {
  if (!requireLogin()) return
  fetchHistory()
})

onPullDownRefresh(async () => {
  await fetchHistory()
  uni.stopPullDownRefresh()
})

const onItemClick = () => {
  uni.showToast({ title: '详情功能开发中', icon: 'none' })
}

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
  padding: 32rpx 32rpx 16rpx;
}
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
.hist-card__more { color: #9CA3AF; font-size: 36rpx; padding-left: 8rpx; line-height: 1; }

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
