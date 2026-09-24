<template>
  <view class="page">
    <!-- Hero 区（设计稿：渐变 + 立方体 + 搜索框） -->
    <section class="hero">
      <view class="hero__decoration">
        <svg width="120" height="120" viewBox="0 0 120 120" fill="none">
          <defs>
            <linearGradient id="cubeGrad" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stop-color="#A5B4FC" />
              <stop offset="100%" stop-color="#6366F1" />
            </linearGradient>
          </defs>
          <path d="M60 10 L100 30 L60 50 L20 30 Z" fill="url(#cubeGrad)" />
          <path d="M20 30 L20 70 L60 90 L60 50 Z" fill="#818CF8" opacity="0.7" />
          <path d="M100 30 L100 70 L60 90 L60 50 Z" fill="#C7D2FE" opacity="0.9" />
          <g transform="translate(85, 15)">
            <path d="M0 0 L8 4 L0 8 Z" fill="#FCD34D" />
            <path d="M0 0 L-4 6 L4 10 L8 4" fill="#FDE68A" opacity="0.8" />
          </g>
          <g transform="translate(15, 80)">
            <circle r="2" fill="#F472B6" />
          </g>
          <g transform="translate(100, 90)">
            <circle r="2.5" fill="#60A5FA" />
          </g>
        </svg>
      </view>
      <text class="hero__title">AI Tools Box</text>
      <text class="hero__subtitle">你的 AI 工具箱</text>
      <view class="hero__search" @click="goToSearch">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 001.48-5.34c-.47-2.78-2.79-5-5.59-5.34a6.505 6.505 0 00-7.27 7.27c.34 2.8 2.56 5.12 5.34 5.59a6.5 6.5 0 005.34-1.48l.27.28v.79l4.25 4.25c.41.41 1.08.41 1.49 0 .41-.41.41-1.08 0-1.49L15.5 14zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" />
        </svg>
        <text>搜索你需要的 AI 工具</text>
      </view>
    </section>

    <!-- 快捷分类（横滑，7 类） -->
    <section class="section">
      <view class="section__header">
        <text class="section__title">快捷分类</text>
        <text class="section__more" @click="goToCategory">全部分类 ›</text>
      </view>
      <scroll-view scroll-x class="category-list" :show-scrollbar="false">
        <view
          v-for="cat in quickCategories"
          :key="cat.code"
          class="category-item"
          :class="{ 'category-item--active': cat.code === activeCategory }"
          @click="activeCategory = cat.code"
        >
          <view class="category-icon" :style="{ background: cat.bg }">
            <!-- 内联 SVG（避免额外静态资源） -->
            <svg v-if="cat.code === 'all'" viewBox="0 0 24 24" fill="#3B82F6"><path d="M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z" /></svg>
            <svg v-else-if="cat.code === 'doc'" viewBox="0 0 24 24" fill="#EF4444"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm-1 7V3.5L18.5 9H13z" /></svg>
            <svg v-else-if="cat.code === 'image'" viewBox="0 0 24 24" fill="#6366F1"><path d="M21 19V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2zM8.5 13.5l2.5 3 3.5-4.5 4.5 6H5l3.5-4.5z" /></svg>
            <svg v-else-if="cat.code === 'audio'" viewBox="0 0 24 24" fill="#F59E0B"><path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.91-3c-.49 0-.9.36-.98.85C16.52 14.2 14.47 16 12 16s-4.52-1.8-4.93-4.15c-.08-.49-.49-.85-.98-.85-.61 0-1.09.54-1 1.14.49 3 2.89 5.35 5.91 5.78V20c0 .55.45 1 1 1s1-.45 1-1v-2.08c3.02-.43 5.42-2.78 5.91-5.78.1-.6-.39-1.14-1-1.14z" /></svg>
            <svg v-else-if="cat.code === 'video'" viewBox="0 0 24 24" fill="#EC4899"><path d="M8 5v14l11-7z" /></svg>
            <svg v-else-if="cat.code === 'dev'" viewBox="0 0 24 24" fill="#10B981"><path d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z" /></svg>
            <svg v-else-if="cat.code === 'office'" viewBox="0 0 24 24" fill="#8B5CF6"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-2 11h-4v4h-2v-4H7v-2h4V8h2v4h4v2z" /></svg>
          </view>
          <text>{{ cat.name }}</text>
        </view>
      </scroll-view>
    </section>

    <!-- 热门工具（2 列卡） -->
    <section class="section">
      <view class="section__header">
        <text class="section__title">热门工具</text>
        <text class="section__more" @click="goToCategory">查看更多 ›</text>
      </view>
      <view class="tool-grid">
        <view
          v-for="tool in hotTools"
          :key="tool.id"
          class="tool-card"
          @click="goToTool(tool.id)"
        >
          <view class="tool-icon" :class="['tool-icon--' + tool.iconType, 'tool-icon--lg']">
            <svg v-if="tool.iconType === 'doc'" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm-1 7V3.5L18.5 9H13z" /></svg>
            <svg v-else-if="tool.iconType === 'image'" viewBox="0 0 24 24"><path d="M21 19V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2zM8.5 13.5l2.5 3 3.5-4.5 4.5 6H5l3.5-4.5z" /></svg>
            <svg v-else-if="tool.iconType === 'ocr'" viewBox="0 0 24 24"><path d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4z" /></svg>
            <svg v-else-if="tool.iconType === 'dev'" viewBox="0 0 24 24"><path d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z" /></svg>
          </view>
          <text class="tool-card__name">{{ tool.name }}</text>
          <text class="tool-card__desc">{{ tool.desc }}</text>
          <view class="tool-card__meta">
            <text class="tag">{{ tool.tag }}</text>
            <view
              class="icon-btn icon-btn--fav"
              :class="{ 'is-active': tool.favored }"
              @click.stop="toggleFav(tool.id)"
            >
              <svg viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.74l-7.19-.61L12 2 9.19 9.13 2 9.74l5.46 4.73L5.82 21z" /></svg>
            </view>
          </view>
        </view>
      </view>
    </section>

    <!-- 最近使用 -->
    <section class="section" v-if="recentTools.length">
      <view class="section__header">
        <text class="section__title">最近使用</text>
        <text class="section__more" @click="goToHistory">更多 ›</text>
      </view>
      <view class="recent-list">
        <view
          v-for="tool in recentTools"
          :key="tool.id"
          class="recent-item"
          @click="goToTool(tool.id)"
        >
          <view class="tool-icon" :class="['tool-icon--' + tool.iconType]">
            <svg v-if="tool.iconType === 'image'" viewBox="0 0 24 24"><path d="M21 19V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2zM8.5 13.5l2.5 3 3.5-4.5 4.5 6H5l3.5-4.5z" /></svg>
          </view>
          <view class="recent-item__body">
            <text class="recent-item__name">{{ tool.name }}</text>
            <text class="recent-item__time">{{ tool.time }}</text>
          </view>
        </view>
      </view>
    </section>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { TOOLS, REALIZED_TOOLS } from '@/config/tools'

// tools.js icon 字段 → 首页可渲染的 iconType（共用映射）
const ICON_TYPE_MAP = {
  summary: 'doc', weekly: 'text', meeting: 'text', ocr: 'ocr',
  'bg-color': 'image', 'bg-image': 'image', compress: 'image', qr: 'dev',
  todo: 'dev', tomato: 'audio', password: 'dev'
}
const TAG_MAP = {
  'AI办公助手': '办公', '图片创意工具': '图片', '效率小工具': '工具'
}
const quickCategories = [
  { code: 'all',    name: '全部', bg: 'var(--cat-all)' },
  { code: 'doc',    name: '文档', bg: 'var(--cat-doc)' },
  { code: 'image',  name: '图片', bg: 'var(--cat-image)' },
  { code: 'audio',  name: '音频', bg: 'var(--cat-audio)' },
  { code: 'video',  name: '视频', bg: 'var(--cat-video)' },
  { code: 'dev',    name: '开发', bg: 'var(--cat-dev)' },
  { code: 'office', name: '办公', bg: 'var(--cat-office)' }
]
const activeCategory = ref('all')

const hotTools = Object.keys(TOOLS).slice(0, 4).map(id => {
  const t = TOOLS[id] || {}
  const iconType = ICON_TYPE_MAP[t.icon] || 'doc'
  return {
    id,
    name: t.name || id,
    desc: t.desc || '',
    tag: TAG_MAP[t.category] || t.category || '工具',
    iconType
  }
})
const recentTools = Object.keys(TOOLS).slice(0, 1).map(id => {
  const t = TOOLS[id] || {}
  return {
    id,
    name: t.name || id,
    time: '刚刚',
    iconType: ICON_TYPE_MAP[t.icon] || 'doc'
  }
})

function goToSearch() {
  uni.navigateTo({ url: '/pages/search' })
}
function goToCategory() {
  uni.navigateTo({ url: '/pages/category' })
}
function goToHistory() {
  uni.navigateTo({ url: '/pages/history' })
}
function goToTool(id) {
  uni.navigateTo({ url: `/pages/tool-common?id=${id}` })
}
function toggleFav(id) {
  const t = hotTools.find(x => x.id === id)
  if (t) t.favored = !t.favored
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page, #F9FAFB);
  padding-bottom: 40rpx;
}

/* ===== Hero ===== */
.hero {
  position: relative;
  background: linear-gradient(135deg, #EFF6FF 0%, #F5F3FF 100%);
  padding: 32rpx 32rpx 40rpx;
  overflow: hidden;
}
.hero__decoration {
  position: absolute;
  right: 16rpx;
  top: 16rpx;
  opacity: 0.95;
}
.hero__title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
  margin-bottom: 4rpx;
}
.hero__subtitle {
  display: block;
  font-size: 28rpx;
  color: var(--text-secondary, #4B5563);
  margin-bottom: 20rpx;
}
.hero__search {
  width: 100%;
  height: 80rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  gap: 16rpx;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 28rpx;

  &:active {
    opacity: 0.95;
  }
  svg {
    width: 32rpx;
    height: 32rpx;
    fill: var(--text-tertiary, #9CA3AF);
  }
}

/* ===== 区块通用 ===== */
.section {
  padding: 20rpx 32rpx 0;
}
.section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.section__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
.section__more {
  font-size: 26rpx;
  color: var(--brand-primary, #3B82F6);
}

/* ===== 快捷分类 ===== */
.category-list {
  white-space: nowrap;
  padding: 4rpx 0;
}
.category-item {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  margin-right: 32rpx;
  width: 80rpx;
  vertical-align: top;

  text {
    font-size: 22rpx;
    color: var(--text-primary, #111827);
  }
  &.category-item--active text {
    color: var(--brand-primary, #3B82F6);
    font-weight: 500;
  }
}
.category-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  svg {
    width: 40rpx;
    height: 40rpx;
  }
}

/* ===== 工具卡 ===== */
.tool-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
}
.tool-card {
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);

  &:active {
    opacity: 0.95;
  }
}
.tool-card__name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin-top: 12rpx;
  margin-bottom: 4rpx;
}
.tool-card__desc {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  margin-bottom: 12rpx;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tool-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tag {
  display: inline-flex;
  align-items: center;
  height: 32rpx;
  padding: 0 12rpx;
  background: var(--bg-page, #F9FAFB);
  color: var(--text-secondary, #4B5563);
  font-size: 20rpx;
  border-radius: 8rpx;
}
.icon-btn {
  width: 48rpx;
  height: 48rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary, #9CA3AF);

  &:active {
    opacity: 0.7;
  }
  svg {
    width: 32rpx;
    height: 32rpx;
    fill: currentColor;
  }
}
.icon-btn--fav.is-active {
  color: var(--brand-primary, #3B82F6);
}

/* ===== 工具图标方块 ===== */
.tool-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  svg {
    width: 44rpx;
    height: 44rpx;
    fill: #FFFFFF;
  }
  &.tool-icon--doc { background: linear-gradient(135deg, #FCA5A5 0%, #EF4444 100%); }
  &.tool-icon--image { background: linear-gradient(135deg, #C7D2FE 0%, #6366F1 100%); }
  &.tool-icon--dev { background: linear-gradient(135deg, #6EE7B7 0%, #10B981 100%); }
  &.tool-icon--audio { background: linear-gradient(135deg, #FCD34D 0%, #F59E0B 100%); }
  &.tool-icon--video { background: linear-gradient(135deg, #F9A8D4 0%, #EC4899 100%); }
  &.tool-icon--ocr { background: linear-gradient(135deg, #93C5FD 0%, #3B82F6 100%); }
  &.tool-icon--text { background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%); }
  &.tool-icon--code { background: linear-gradient(135deg, #5EEAD4 0%, #14B8A6 100%); }

  &.tool-icon--lg {
    width: 96rpx;
    height: 96rpx;
    svg {
      width: 52rpx;
      height: 52rpx;
    }
  }
}

/* ===== 最近使用 ===== */
.recent-list {
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  overflow: hidden;
}
.recent-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;

  &:active {
    opacity: 0.7;
  }
}
.recent-item__body {
  flex: 1;
}
.recent-item__name {
  display: block;
  font-size: 30rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 4rpx;
}
.recent-item__time {
  display: block;
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
}
</style>