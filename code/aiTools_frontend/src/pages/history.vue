<template>
  <view class="page-container">
    <page-header title="历史记录" showBack></page-header>

    <view class="page-content">
      <!-- 空状态 -->
      <view v-if="!loading && historyList.length === 0" class="empty-state">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 8V12L15 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <path d="M3 12C3 16.9706 7.02944 21 12 21C16.9706 21 21 16.9706 21 12C21 7.02944 16.9706 3 12 3C7.02944 3 3 7.02944 3 12Z" stroke="currentColor" stroke-width="1.5"/>
        </svg>
        <text class="empty-text">暂无历史记录</text>
      </view>

      <!-- 历史列表 -->
      <view v-else class="history-list">
        <view
          v-for="item in historyList"
          :key="item.id"
          class="history-item animate-fade-in-up"
        >
          <view class="item-main" @click="onItemClick">
            <view class="item-top">
              <text class="item-name">{{ getToolName(item) }}</text>
              <text
                class="item-status"
                :class="item.status === 1 ? 'status-success' : 'status-fail'"
              >{{ item.status === 1 ? '成功' : '失败' }}</text>
            </view>
            <text class="item-desc">{{ item.inputContent || '（无输入内容）' }}</text>
            <text class="item-time">{{ formatTime(item.createTime) }}</text>
          </view>
          <view class="delete-btn press-scale" @click.stop="onDelete(item)">
            <svg class="delete-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M3 6H21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              <path d="M19 6V20C19 20.5523 18.5523 21 18 21H6C5.44772 21 5 20.5523 5 20V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </view>
        </view>
      </view>

      <view v-if="historyList.length > 0" class="clear-all-btn press-scale" @click="onClearAll">
        <text class="clear-all-text">清空历史记录</text>
      </view>

      <view class="safe-area-bottom"></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import PageHeader from '@/components/PageHeader.vue'
import { historyListApi, historyDeleteApi, historyClearAllApi } from '@/api/history'
import { requireLogin } from '@/utils/auth'

const loading = ref(false)
const historyList = ref([])

const getToolName = (item) => {
  return item.toolName || item.aiCode || '未知工具'
}

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 16)
}

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

// 下拉刷新
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

.empty-state {
  padding: $spacing-xl * 3 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  .empty-icon {
    width: 96rpx;
    height: 96rpx;
    color: $text-tertiary;
    margin-bottom: $spacing-md;
  }

  .empty-text {
    font-size: $font-size-md;
    color: $text-tertiary;
  }
}

.history-list {
  padding-top: $spacing-md;
}

.history-item {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-card;
  display: flex;
  align-items: center;

  .item-main {
    flex: 1;
    min-width: 0;
    margin-right: $spacing-sm;

    .item-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: $spacing-xs;

      .item-name {
        font-size: $font-size-md;
        font-weight: 600;
        color: $text-primary;
      }

      .item-status {
        font-size: $font-size-xs;
        padding: 4rpx 16rpx;
        border-radius: $radius-pill;
        background-color: $bg-gray;
      }

      .status-success {
        color: #3a7d44;
      }

      .status-fail {
        color: #c0392b;
      }
    }

    .item-desc {
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      overflow: hidden;
      font-size: $font-size-sm;
      color: $text-secondary;
      line-height: 1.5;
      margin-bottom: $spacing-xs;
    }

    .item-time {
      font-size: $font-size-xs;
      color: $text-tertiary;
    }
  }

  .delete-btn {
    width: 72rpx;
    height: 72rpx;
    border-radius: $radius-pill;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    .delete-icon {
      width: 36rpx;
      height: 36rpx;
      color: $text-tertiary;
    }
  }
}

.clear-all-btn {
  margin: $spacing-lg auto;
  width: 80%;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: transparent;
  border: 1px solid #C0392B;
  text-align: center;
}

.clear-all-text {
  color: #C0392B;
  font-size: $font-size-md;
}
</style>
