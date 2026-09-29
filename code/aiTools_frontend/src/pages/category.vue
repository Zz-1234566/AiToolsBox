<template>
  <view class="page page--no-tabbar">
    <!-- 顶栏：返回 + 标题 + 搜索按钮 -->
    <view class="topbar topbar--border">
      <view class="topbar__back" hover-class="topbar__back--hover" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      <text class="topbar__title">AI 工具</text>
      <view class="topbar__action" @click="goSearch">
        <svg viewBox="0 0 24 24" fill="none">
          <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="1.5" />
          <path d="M21 21L16.65 16.65" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
      </view>
    </view>

    <!-- 横向分类 Tab -->
    <scroll-view scroll-x class="category-tabs" :show-scrollbar="false">
      <view
        v-for="tab in tabs"
        :key="tab.code"
        class="category-tab"
        :class="{ 'category-tab--active': activeTab === tab.code }"
        @click="activeTab = tab.code"
      >
        {{ tab.name }}
      </view>
    </scroll-view>

    <!-- 工具列表 -->
    <scroll-view scroll-y class="tool-list-scroll">
      <view v-if="filteredTools.length === 0" class="empty">
        <text>该分类暂无工具</text>
      </view>
      <view
        v-for="tool in filteredTools"
        :key="tool.id"
        class="tool-list-item"
        @click="goToTool(tool.id)"
      >
        <view class="tool-icon" :class="['tool-icon--' + tool.iconType, 'tool-icon--md']">
          <text class="tool-icon__emoji">{{ tool.emoji }}</text>
        </view>
        <view class="tool-list-item__body">
          <view class="tool-list-item__name">{{ tool.name }}</view>
          <view class="tool-list-item__desc">{{ tool.desc }}</view>
          <view class="tool-list-item__stats">
            <view class="stats-item">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5C21.27 7.61 17 4.5 12 4.5zm0 12.5c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z" />
              </svg>
              <text>{{ tool.viewCount }}</text>
            </view>
            <view class="stats-item stats-fav" :class="{ 'stats-fav--on': tool.favored }" @click.stop="toggleFav(tool.id)">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
              </svg>
              <text>{{ tool.favored ? '已收藏' : '收藏' }}</text>
            </view>
          </view>
        </view>
      </view>
      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { TOOLS, REALIZED_TOOLS } from '@/config/tools'
import { favoriteListApi, favoriteAddApi, favoriteRemoveApi } from '@/api/favorite'

const tabs = [
  { code: 'all', name: '全部' },
  { code: 'document', name: '文档' },
  { code: 'image', name: '图片' },
  { code: 'audio', name: '音频' },
  { code: 'video', name: '视频' },
  { code: 'dev', name: '开发' },
  { code: 'office', name: '办公' }
]
const activeTab = ref('all')

// icon 字段 → iconType + emoji 的映射（mockup 里的视觉风格）
const ICON_MAP = {
  summary:   { type: 'doc',   emoji: '📄' },
  weekly:    { type: 'text',  emoji: '📝' },
  meeting:   { type: 'text',  emoji: '🎯' },
  ocr:       { type: 'ocr',   emoji: '🔍' },
  'bg-color':{ type: 'image', emoji: '🖼️' },
  'bg-image':{ type: 'image', emoji: '🎨' },
  compress:  { type: 'image', emoji: '📦' },
  qr:        { type: 'dev',   emoji: '🔳' },
  todo:      { type: 'dev',   emoji: '✅' },
  tomato:    { type: 'audio', emoji: '🍅' },
  password:  { type: 'dev',   emoji: '🔑' }
}

// 从 TOOLS 派生列表（展示全部 13 个工具，包括未实现的）
const allTools = Object.keys(TOOLS).map(id => {
  const t = TOOLS[id]
  const icon = ICON_MAP[t.icon] || { type: 'doc', emoji: '📄' }
  return {
    id,
    name: t.name,
    desc: t.desc,
    iconType: icon.type,
    emoji: icon.emoji,
    fileType: t.fileType || '',
    inputTypes: t.inputTypes || [],
    category: t.category || ''
  }
})

// Tab 过滤
const filteredTools = computed(() => {
  switch (activeTab.value) {
    case 'document':
      return allTools.filter(t => t.fileType === 'document' || t.inputTypes.includes('file'))
    case 'image':
      return allTools.filter(t => t.fileType === 'image' || t.inputTypes.includes('image'))
    case 'audio':
    case 'video':
    case 'dev':
      return []
    case 'office':
      return allTools.filter(t => t.category === 'AI办公助手' || t.category === '效率小工具')
    case 'all':
    default:
      return allTools
  }
})

const goToTool = (id) => {
  uni.navigateTo({ url: `/pages/tool-common?id=${id}` })
}

/** 收藏：点击同步后端（乐观更新） */
function toggleFav(id) {
  const t = allTools.find(x => x.id === id)
  if (!t) return
  const next = !t.favored
  t.favored = next
  const call = next ? favoriteAddApi('tool', id) : favoriteRemoveApi('tool', id)
  call.catch(() => { t.favored = !next })
}

/** 回填已收藏状态 */
async function loadFavorites() {
  try {
    const res = await favoriteListApi('tool')
    const set = new Set((res.data || []).map(f => f.targetId))
    allTools.forEach(t => { t.favored = set.has(t.id) })
  } catch (e) { /* 未登录 / 失败：保持未收藏 */ }
}

onShow(() => {
  loadFavorites()
})
const goBack = () => {
  // #ifdef H5
  if (window.history.length > 1) {
    window.history.back()
  } else {
    uni.showToast({ title: '已是最上层页面', icon: 'none' })
  }
  // #endif
  // #ifndef H5
  uni.navigateBack({ delta: 1 })
  // #endif
}

const goSearch = () => {
  uni.navigateTo({ url: '/pages/search' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page, #F9FAFB);
}

/* ===== Topbar ===== */
.topbar {
  display: flex;
  align-items: center;
  position: relative;
  height: 88rpx;
  padding: 0 32rpx;
  background: var(--bg-card, #FFFFFF);
}
.topbar--border {
  border-bottom: 1rpx solid var(--border-light, #F3F4F6);
}
.topbar__back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  color: var(--text-primary, #1F2937);
  cursor: pointer;
}
.topbar__back svg {
  width: 40rpx;
  height: 40rpx;
}
.topbar__back--hover {
  opacity: 0.6;
}

.topbar__title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
.topbar__action {
  position: absolute;
  right: 32rpx;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #4B5563);
}
.topbar__action svg {
  width: 36rpx; height: 36rpx;
}

/* ===== Category Tabs ===== */
.category-tabs {
  white-space: nowrap;
  background: var(--bg-card, #FFFFFF);
  padding: 16rpx 24rpx;
  border-bottom: 1rpx solid var(--border-light, #F3F4F6);
}
.category-tab {
  display: inline-block;
  padding: 12rpx 28rpx;
  margin-right: 16rpx;
  font-size: 28rpx;
  color: var(--text-secondary, #4B5563);
  background: var(--bg-disabled, #F3F4F6);
  border-radius: 32rpx;
  cursor: pointer;
}
.category-tab--active {
  color: #fff;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
}

/* ===== Tool List ===== */
.tool-list-scroll {
  height: calc(100vh - 88rpx - 80rpx - 120rpx);
  padding: 16rpx 24rpx;
}
.tool-list-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 16rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  cursor: pointer;
}
.tool-list-item__body {
  flex: 1;
  min-width: 0;
}
.tool-list-item__name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin-bottom: 8rpx;
}
.tool-list-item__desc {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  line-height: 1.4;
  margin-bottom: 12rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}
.tool-list-item__stats {
  display: flex;
  gap: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 22rpx;
}
.stats-item {
  display: flex;
  align-items: center;
  gap: 6rpx;
}
/* 收藏按钮：默认灰，已收藏为主题色 */
.stats-fav {
  padding: 4rpx 16rpx;
  border-radius: 9999rpx;
  background: #F3F4F6;
  color: #9CA3AF;
  transition: all 0.15s;
}
.stats-fav--on {
  background: #FEF3C7;
  color: #F59E0B;
  font-weight: 500;
}

/* ===== Tool Icon ===== */
.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
  color: #fff;
  flex-shrink: 0;
}
.tool-icon--md {
  width: 96rpx;
  height: 96rpx;
}
.tool-icon--doc { background: var(--gradient-doc, linear-gradient(135deg, #FCA5A5 0%, #EF4444 100%)); }
.tool-icon--image { background: var(--gradient-image, linear-gradient(135deg, #C7D2FE 0%, #6366F1 100%)); }
.tool-icon--ocr { background: var(--gradient-ocr, linear-gradient(135deg, #93C5FD 0%, #3B82F6 100%)); }
.tool-icon--dev { background: var(--gradient-dev, linear-gradient(135deg, #6EE7B7 0%, #10B981 100%)); }
.tool-icon--audio { background: var(--gradient-audio, linear-gradient(135deg, #FCD34D 0%, #F59E0B 100%)); }
.tool-icon--video { background: var(--gradient-video, linear-gradient(135deg, #F9A8D4 0%, #EC4899 100%)); }
.tool-icon--text { background: var(--gradient-text, linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%)); }
.tool-icon--code { background: var(--gradient-code, linear-gradient(135deg, #5EEAD4 0%, #14B8A6 100%)); }
.tool-icon__emoji {
  font-size: 44rpx;
}

/* ===== Empty ===== */
.empty {
  padding: 80rpx 0;
  text-align: center;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 26rpx;
}
.safe-area-bottom {
  height: 60rpx;
}
</style>