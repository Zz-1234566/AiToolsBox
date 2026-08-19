<template>
  <view class="page-container animate-fade-in">
    <page-header title="智汇工具箱" :showBack="false"></page-header>
    
    <scroll-view scroll-y class="page-content">
      <!-- 顶部标语 -->
      <view class="hero-section animate-fade-in-up">
        <text class="hero-title">让 AI 帮你做小事</text>
        <text class="hero-subtitle">文档提取、周报生成、图片处理，一个 App 全搞定</text>
      </view>
      
      <!-- 搜索入口 -->
      <view class="search-card" @click="goToSearch">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="1.5"/>
          <path d="M21 21L16.65 16.65" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <text class="search-text">搜索工具...</text>
      </view>
      
      <!-- 常用工具 -->
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
            <view class="quick-icon">
              <tool-icon :name="tool.icon" size="48rpx"></tool-icon>
            </view>
            <text class="quick-name">{{ tool.name }}</text>
          </view>
        </view>
      </view>
      
      <!-- 分类工具 -->
      <view class="section" v-for="category in categories" :key="category.code">
        <view class="section-header">
          <text class="section-title">{{ category.name }}</text>
        </view>
        <view class="tool-grid">
          <tool-card
            v-for="(tool, toolIndex) in category.tools"
            :key="tool.toolId"
            class="animate-stagger"
            :style="{ animationDelay: toolIndex * 0.06 + 's' }"
            :icon="tool.icon"
            :name="tool.name"
            :desc="tool.desc"
            :toolId="tool.toolId"
            :isCustom="tool.isCustom"
            @click="goToTool"
          ></tool-card>
        </view>
      </view>
      
      <!-- 自定义工具入口 -->
      <view class="custom-banner" @click="goToCustom">
        <view class="custom-content">
          <tool-icon name="custom" size="40rpx"></tool-icon>
          <view class="custom-text">
            <text class="custom-title">自定义工具</text>
            <text class="custom-desc">设计你自己的 AI 提示词</text>
          </view>
        </view>
        <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
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
import { CATEGORIES, TOOLS, REALIZED_TOOLS } from '@/config/tools'

// 首页推荐：优先取已实现工具，不足时用分类首工具补齐
const quickTools = (REALIZED_TOOLS.length >= 4
  ? REALIZED_TOOLS.slice(0, 4)
  : [...REALIZED_TOOLS, ...CATEGORIES.flatMap(c => c.tools)].slice(0, 4)
).map(id => ({ toolId: id, icon: TOOLS[id].icon, name: TOOLS[id].name, desc: TOOLS[id].desc || '' }))

// 分类工具：由顶层配置派生
const categories = CATEGORIES.map(cat => ({
  code: cat.code,
  name: cat.code,
  tools: cat.tools.map(id => ({
    toolId: id,
    icon: TOOLS[id].icon,
    name: TOOLS[id].name,
    desc: TOOLS[id].desc || '',
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
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  flex-direction: column;
}

.page-content {
  flex: 1;
  padding: 0 $spacing-md;
}

.hero-section {
  padding: $spacing-xl 0;
  
  .hero-title {
    display: block;
    font-size: $font-size-xxl;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: $spacing-sm;
  }
  
  .hero-subtitle {
    display: block;
    font-size: $font-size-md;
    color: $text-secondary;
    line-height: 1.5;
  }
}

.search-card {
  background-color: $bg-white;
  border-radius: $radius-pill;
  height: 96rpx;
  display: flex;
  align-items: center;
  padding: 0 $spacing-lg;
  margin-bottom: $spacing-xl;
  box-shadow: $shadow-card;
  
  &:active {
    background-color: $bg-gray;
  }
  
  .search-icon {
    width: 40rpx;
    height: 40rpx;
    color: $text-tertiary;
    margin-right: $spacing-sm;
  }
  
  .search-text {
    font-size: $font-size-md;
    color: $text-tertiary;
  }
}

.section {
  margin-bottom: $spacing-xl;
  
  .section-header {
    margin-bottom: $spacing-md;
    
    .section-title {
      font-size: $font-size-lg;
      font-weight: 600;
      color: $text-primary;
    }
  }
}

.quick-tools {
  display: flex;
  justify-content: space-between;
  gap: $spacing-sm;
  
  .quick-tool-item {
    flex: 1;
    background-color: $bg-white;
    border-radius: $radius-lg;
    padding: $spacing-md $spacing-sm;
    display: flex;
    flex-direction: column;
    align-items: center;
    box-shadow: $shadow-card;
    
    &:active {
      background-color: $bg-gray;
    }
    
    .quick-icon {
      width: 88rpx;
      height: 88rpx;
      border-radius: $radius-pill;
      background-color: $bg-gray;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: $spacing-sm;
    }
    
    .quick-name {
      font-size: $font-size-sm;
      color: $text-primary;
      text-align: center;
    }
  }
}

.tool-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;
}

.custom-banner {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-xl;
  box-shadow: $shadow-card;
  
  &:active {
    background-color: $bg-gray;
  }
  
  .custom-content {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    
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
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }
  }
  
  .arrow-icon {
    width: 40rpx;
    height: 40rpx;
    color: $text-tertiary;
  }
}
</style>
