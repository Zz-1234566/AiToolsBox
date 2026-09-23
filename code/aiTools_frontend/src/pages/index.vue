<template>
  <view class="page-container animate-fade-in">
    <page-header title="智汇工具箱" :showBack="false"></page-header>

    <scroll-view scroll-y class="page-content">
      <!-- Hero 区（按设计稿：蓝紫渐变 + 立方体装饰 + 搜索框） -->
      <view class="hero-section animate-fade-in-up">
        <view class="hero-deco">
          <svg viewBox="0 0 120 120" fill="none" width="120" height="120">
            <defs>
              <linearGradient id="cubeGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#A5B4FC"/>
                <stop offset="100%" stop-color="#6366F1"/>
              </linearGradient>
            </defs>
            <path d="M60 10 L100 30 L60 50 L20 30 Z" fill="url(#cubeGrad)"/>
            <path d="M20 30 L20 70 L60 90 L60 50 Z" fill="#818CF8" opacity="0.7"/>
            <path d="M100 30 L100 70 L60 90 L60 50 Z" fill="#C7D2FE" opacity="0.9"/>
          </svg>
        </view>
        <text class="hero-title">AI Tools Box</text>
        <text class="hero-subtitle">你的 AI 工具箱</text>
        <view class="hero-search" @click="goToSearch">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none">
            <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="1.5"/>
            <path d="M21 21L16.65 16.65" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          <text class="search-text">搜索你需要的 AI 工具</text>
        </view>
      </view>

      <!-- 常用工具（4 个圆图标横排，渐变方块） -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">常用工具</text>
        </view>
        <view class="quick-tools">
          <view
            v-for="tool in quickTools"
            :key="tool.toolId"
            class="quick-tool-item press-scale"
            @click="goToTool(tool)"
          >
            <tool-icon :name="tool.icon" :gradient="tool.gradient" size="72rpx"></tool-icon>
            <text class="quick-name">{{ tool.name }}</text>
          </view>
        </view>
      </view>

      <!-- 分类工具（按设计稿：区块标题 + 2 列卡片网格） -->
      <view class="section" v-for="category in categories" :key="category.code">
        <view class="section-header">
          <text class="section-title">{{ category.code }}</text>
          <text class="section-more" @click="goToCategory(category)">查看更多 ›</text>
        </view>
        <view class="tool-grid">
          <tool-card
            v-for="(tool, toolIndex) in category.tools"
            :key="tool.toolId"
            class="animate-stagger"
            :style="{ animationDelay: toolIndex * 0.06 + 's' }"
            :icon="tool.icon"
            :name="tool.name"
            :desc="tool.desc || ''"
            :toolId="tool.toolId"
            :isCustom="tool.isCustom"
            :gradient="tool.gradient"
            :categoryTag="tool.tag"
            @click="goToTool"
          ></tool-card>
        </view>
      </view>

      <!-- 自定义工具入口 -->
      <view class="custom-banner press-scale" @click="goToCustom">
        <view class="custom-content">
          <tool-icon name="custom" gradient="none" size="64rpx"></tool-icon>
          <view class="custom-text">
            <text class="custom-title">自定义工具</text>
            <text class="custom-desc">设计你自己的 AI 提示词</text>
          </view>
        </view>
        <svg class="arrow-icon" viewBox="0 0 24 24" fill="none">
          <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import PageHeader from '@/components/PageHeader.vue'
import ToolIcon from '@/components/ToolIcon.vue'
import ToolCard from '@/components/ToolCard.vue'
import { CATEGORIES, TOOLS, REALIZED_TOOLS, getToolGradient, getToolCategoryTag } from '@/config/tools'

// 首页推荐：优先取已实现工具，不足时用分类首工具补齐
const quickTools = (REALIZED_TOOLS.length >= 4
  ? REALIZED_TOOLS.slice(0, 4)
  : [...REALIZED_TOOLS, ...CATEGORIES.flatMap(c => c.tools)]
).slice(0, 4).map(id => ({
  toolId: id,
  icon: TOOLS[id].icon,
  name: TOOLS[id].name,
  desc: TOOLS[id].desc || '',
  gradient: getToolGradient(id),
  isCustom: false
}))

// 分类工具：由顶层配置派生
const categories = CATEGORIES.map(cat => ({
  code: cat.code,
  tools: cat.tools.map(id => ({
    toolId: id,
    icon: TOOLS[id].icon,
    name: TOOLS[id].name,
    desc: TOOLS[id].desc || '',
    gradient: getToolGradient(id),
    tag: getToolCategoryTag(id),
    isCustom: false
  }))
}))

const goToSearch = () => {
  uni.navigateTo({ url: '/pages/search' })
}

const goToTool = (tool) => {
  const data = typeof tool === 'object' && tool.toolId ? tool : {}
  if (data.isCustom) {
    uni.navigateTo({ url: `/pages/tool-custom?id=${data.toolId}` })
  } else {
    uni.navigateTo({ url: `/pages/tool-common?id=${data.toolId}` })
  }
}

const goToCustom = () => {
  uni.navigateTo({ url: '/pages/tool-custom' })
}

// 占位：分类页未建，先回到搜索页
const goToCategory = (category) => {
  uni.navigateTo({ url: `/pages/search?keyword=${encodeURIComponent(category.code)}` })
}
</script>

<style lang="scss" scoped>
@use '@/uni.scss' as *;
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  flex-direction: column;
}

.page-content {
  flex: 1;
  padding: 0 $spacing-4;
}

/* ============================================================
   Hero 区（蓝紫渐变）
   ============================================================ */
.hero-section {
  position: relative;
  background: linear-gradient(135deg, #EFF6FF 0%, #F5F3FF 100%);
  margin: $spacing-2 (-$spacing-4) $spacing-4;
  padding: $spacing-6 $spacing-4 $spacing-6;
  border-radius: $radius-lg;
  overflow: hidden;

  .hero-deco {
    position: absolute;
    right: 16rpx;
    top: 16rpx;
    opacity: 0.9;
  }

  .hero-title {
    display: block;
    font-size: $font-size-xxl;
    font-weight: 700;
    color: $brand-primary;
    margin-bottom: $spacing-1;
  }

  .hero-subtitle {
    display: block;
    font-size: $font-size-md;
    color: $text-secondary;
    margin-bottom: $spacing-4;
  }
}

.hero-search {
  background-color: $bg-white;
  border-radius: $radius-lg;
  height: 80rpx;
  display: flex;
  align-items: center;
  padding: 0 $spacing-3;
  box-shadow: $shadow-card;
  transition: opacity 0.1s ease-in-out;

  &:active {
    opacity: 0.85;
  }

  .search-icon {
    width: 36rpx;
    height: 36rpx;
    color: $text-tertiary;
    margin-right: $spacing-2;
  }

  .search-text {
    font-size: $font-size-md;
    color: $text-tertiary;
  }
}

/* ============================================================
   区块通用
   ============================================================ */
.section {
  margin-bottom: $spacing-5;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-3;

  .section-title {
    font-size: $font-size-lg;
    font-weight: 600;
    color: $text-primary;
  }

  .section-more {
    font-size: $font-size-xs;
    color: $brand-primary;
  }
}

/* ============================================================
   常用工具横排
   ============================================================ */
.quick-tools {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-2;
}

.quick-tool-item {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-3 $spacing-1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-2;
  box-shadow: $shadow-card;

  &:active {
    opacity: 0.85;
  }

  .quick-name {
    font-size: $font-size-xs;
    color: $text-primary;
    text-align: center;
    line-height: 1.3;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
}

/* ============================================================
   工具卡片网格
   ============================================================ */
.tool-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-3;
}

/* ============================================================
   自定义工具 banner
   ============================================================ */
.custom-banner {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-5;
  box-shadow: $shadow-card;
  transition: opacity 0.1s ease-in-out;

  &:active {
    opacity: 0.85;
  }

  .custom-content {
    display: flex;
    align-items: center;
    gap: $spacing-3;

    .custom-text {
      display: flex;
      flex-direction: column;

      .custom-title {
        font-size: $font-size-md;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 4rpx;
      }

      .custom-desc {
        font-size: $font-size-xs;
        color: $text-tertiary;
      }
    }
  }

  .arrow-icon {
    width: 36rpx;
    height: 36rpx;
    color: $text-tertiary;
  }
}
</style>