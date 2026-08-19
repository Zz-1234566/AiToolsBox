<template>
  <view class="page-container">
    <page-header title="我的收藏" showBack></page-header>

    <scroll-view scroll-y class="page-content">
      <!-- 收藏列表 -->
      <view v-if="favoriteList.length > 0" class="favorites-list">
        <view
          v-for="(item, index) in favoriteList"
          :key="item.toolId"
          class="favorite-card"
          @click="goToTool(item)"
          @longpress="handleLongPress(item, index)"
        >
          <view class="favorite-main">
            <view class="favorite-icon">
              <tool-icon :name="item.icon" size="48rpx"></tool-icon>
            </view>
            <view class="favorite-info">
              <text class="favorite-name">{{ item.name }}</text>
              <text class="favorite-desc">{{ item.desc }}</text>
              <text class="favorite-time">{{ item.favoriteTime }}</text>
            </view>
          </view>
          <view class="favorite-action" @click.stop="removeFavorite(item, index)">
            <svg class="delete-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 6L6 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M6 6L18 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else class="empty-state">
        <view class="empty-icon">
          <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M5 5C5 3.34315 6.34315 2 8 2H16C17.6569 2 19 3.34315 19 5V21L12 17.5L5 21V5Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </view>
        <text class="empty-text">还没有收藏的工具</text>
        <text class="empty-tip">去发现更多实用工具吧</text>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '@/components/PageHeader.vue'
import ToolIcon from '@/components/ToolIcon.vue'
import { requireLogin } from '@/utils/auth'
import { TOOLS } from '@/config/tools'

// 收藏列表：toolId 与顶层配置一致，name/desc/icon 由配置派生
const favIds = ['weekly-report', 'doc-keypoint-extract', 'id-photo-bg-change']
const favoriteList = ref(favIds.map((id, i) => ({
  toolId: id,
  icon: TOOLS[id].icon,
  name: TOOLS[id].name,
  desc: TOOLS[id].desc || '',
  favoriteTime: `2026-08-0${i + 1} 收藏`,
  isCustom: false
})))

const goToTool = (item) => {
  if (item.isCustom) {
    uni.navigateTo({ url: `/pages/tool-custom?id=${item.toolId}` })
  } else {
    uni.navigateTo({ url: `/pages/tool-common?id=${item.toolId}` })
  }
}

const removeFavorite = (item, index) => {
  uni.showModal({
    title: '取消收藏',
    content: `确定不再收藏「${item.name}」吗？`,
    confirmColor: '#211E1E',
    success: (res) => {
      if (res.confirm) {
        favoriteList.value.splice(index, 1)
        uni.showToast({ title: '已取消收藏', icon: 'none' })
      }
    }
  })
}

const handleLongPress = (item, index) => {
  removeFavorite(item, index)
}

onShow(() => {
  if (!requireLogin()) return
})
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

.favorites-list {
  padding-top: $spacing-md;
}

.favorite-card {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-card;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.98);
    background-color: $bg-gray;
  }

  .favorite-main {
    flex: 1;
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .favorite-icon {
    width: 88rpx;
    height: 88rpx;
    border-radius: $radius-md;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: $spacing-md;
    flex-shrink: 0;
  }

  .favorite-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .favorite-name {
    font-size: $font-size-md;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 6rpx;
  }

  .favorite-desc {
    font-size: $font-size-sm;
    color: $text-secondary;
    margin-bottom: 6rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .favorite-time {
    font-size: $font-size-xs;
    color: $text-tertiary;
  }

  .favorite-action {
    width: 64rpx;
    height: 64rpx;
    border-radius: $radius-pill;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-left: $spacing-md;
    flex-shrink: 0;

    &:active {
      background-color: $divider-color;
    }

    .delete-icon {
      width: 40rpx;
      height: 40rpx;
      color: $text-secondary;
    }
  }
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl * 2 0;

  .empty-icon {
    width: 128rpx;
    height: 128rpx;
    border-radius: $radius-pill;
    background-color: $bg-white;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: $spacing-lg;
    box-shadow: $shadow-card;

    .icon {
      width: 64rpx;
      height: 64rpx;
      color: $text-tertiary;
    }
  }

  .empty-text {
    font-size: $font-size-lg;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-sm;
  }

  .empty-tip {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}
</style>
