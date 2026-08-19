<template>
  <view class="page-container animate-fade-in">
    <page-header title="搜索" :showBack="true"></page-header>
    
    <view class="page-content">
      <!-- 搜索框 -->
      <view class="search-box">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="11" cy="11" r="8" stroke="currentColor" stroke-width="1.5"/>
          <path d="M21 21L16.65 16.65" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <input 
          class="search-input" 
          v-model="keyword" 
          placeholder="输入工具名称..."
          placeholder-class="input-placeholder"
          @input="onSearch"
        />
        <text v-if="keyword" class="clear-btn" @click="clearKeyword">清除</text>
      </view>
      
      <!-- 搜索结果 -->
      <scroll-view scroll-y class="result-list">
        <view v-if="filteredTools.length === 0 && keyword" class="empty-state">
          <tool-icon name="default" size="80rpx"></tool-icon>
          <text class="empty-text">未找到相关工具</text>
        </view>
        
        <view v-if="filteredTools.length === 0 && !keyword" class="hot-section">
          <text class="hot-title">热门工具</text>
          <view class="hot-list">
            <view 
              v-for="tool in hotTools" 
              :key="tool.toolId"
              class="hot-item"
              @click="goToTool(tool)"
            >
              <view class="hot-icon">
                <tool-icon :name="tool.icon" size="40rpx"></tool-icon>
              </view>
              <view class="hot-info">
                <text class="hot-name">{{ tool.name }}</text>
                <text class="hot-desc">{{ tool.desc }}</text>
              </view>
            </view>
          </view>
        </view>
        
        <view v-if="filteredTools.length > 0" class="search-results">
          <view 
            v-for="tool in filteredTools" 
            :key="tool.toolId"
            class="result-item"
            @click="goToTool(tool)"
          >
            <view class="result-icon">
              <tool-icon :name="tool.icon" size="44rpx"></tool-icon>
            </view>
            <view class="result-info">
              <text class="result-name">{{ tool.name }}</text>
              <text class="result-desc">{{ tool.desc }}</text>
            </view>
            <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
        </view>
        
        <view class="safe-area-bottom"></view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import ToolIcon from '@/components/ToolIcon.vue'
import { TOOLS, REALIZED_TOOLS } from '@/config/tools'

const keyword = ref('')

// 全部工具：由顶层配置派生
const allTools = Object.entries(TOOLS).map(([id, t]) => ({
  toolId: id, icon: t.icon, name: t.name, desc: t.desc || '', isCustom: false
}))

// 热门工具：优先取已实现工具，不足时取前 4 个
const hotTools = (REALIZED_TOOLS.length >= 4 ? REALIZED_TOOLS : Object.keys(TOOLS).slice(0, 4))
  .map(id => allTools.find(t => t.toolId === id)).filter(Boolean)

const filteredTools = computed(() => {
  if (!keyword.value) return []
  return allTools.filter(tool => 
    tool.name.includes(keyword.value) || 
    tool.desc.includes(keyword.value)
  )
})

const onSearch = () => {
  // 实际可调用搜索接口
}

const clearKeyword = () => {
  keyword.value = ''
}

const goToTool = (tool) => {
  if (tool.isCustom) {
    uni.navigateTo({ url: `/pages/tool-custom?id=${tool.toolId}` })
  } else {
    uni.navigateTo({ url: `/pages/tool-common?id=${tool.toolId}` })
  }
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
  display: flex;
  flex-direction: column;
  padding: $spacing-md;
}

.search-box {
  background-color: $bg-white;
  border-radius: $radius-pill;
  height: 96rpx;
  display: flex;
  align-items: center;
  padding: 0 $spacing-lg;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-card;
  
  .search-icon {
    width: 40rpx;
    height: 40rpx;
    color: $text-tertiary;
    margin-right: $spacing-sm;
  }
  
  .search-input {
    flex: 1;
    height: 96rpx;
    font-size: $font-size-md;
    color: $text-primary;
  }
  
  .input-placeholder {
    color: $text-tertiary;
  }
  
  .clear-btn {
    font-size: $font-size-sm;
    color: $text-secondary;
    padding: $spacing-xs;
  }
}

.result-list {
  flex: 1;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl * 2 0;
  color: $text-tertiary;
  
  .empty-text {
    margin-top: $spacing-md;
    font-size: $font-size-md;
    color: $text-tertiary;
  }
}

.hot-section {
  .hot-title {
    font-size: $font-size-lg;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-md;
    display: block;
  }
  
  .hot-list {
    background-color: $bg-white;
    border-radius: $radius-lg;
    padding: $spacing-md;
    box-shadow: $shadow-card;
  }
  
  .hot-item {
    display: flex;
    align-items: center;
    padding: $spacing-md 0;
    border-bottom: 1rpx solid $divider-color;
    
    &:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }
    
    &:active {
      opacity: 0.7;
    }
    
    .hot-icon {
      width: 80rpx;
      height: 80rpx;
      border-radius: $radius-md;
      background-color: $bg-gray;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: $spacing-md;
    }
    
    .hot-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      
      .hot-name {
        font-size: $font-size-md;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 4rpx;
      }
      
      .hot-desc {
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }
  }
}

.search-results {
  .result-item {
    background-color: $bg-white;
    border-radius: $radius-lg;
    padding: $spacing-md;
    display: flex;
    align-items: center;
    margin-bottom: $spacing-md;
    box-shadow: $shadow-card;
    
    &:active {
      background-color: $bg-gray;
    }
    
    .result-icon {
      width: 80rpx;
      height: 80rpx;
      border-radius: $radius-md;
      background-color: $bg-gray;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: $spacing-md;
    }
    
    .result-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      
      .result-name {
        font-size: $font-size-md;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 4rpx;
      }
      
      .result-desc {
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }
    
    .arrow-icon {
      width: 40rpx;
      height: 40rpx;
      color: $text-tertiary;
    }
  }
}
</style>
