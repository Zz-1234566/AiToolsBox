<template>
  <view class="result-area">
    <view class="result-header">
      <text class="result-title">{{ title }}</text>
      <view class="result-actions">
        <view class="action-btn" @click="onCopy">
          <svg class="action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="9" y="9" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.5"/>
            <path d="M5 15H4C3.46957 15 2.96086 14.7893 2.58579 14.4142C2.21071 14.0391 2 13.5304 2 13V4C2 3.46957 2.21071 2.96086 2.58579 2.58579C2.96086 2.21071 3.46957 2 4 2H13C13.5304 2 14.0391 2.21071 14.4142 2.58579C14.7893 2.96086 15 3.46957 15 4V5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </view>
        <view class="action-btn" @click="onRegenerate">
          <svg class="action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M23 4V10H17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M1 20V14H7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M3.51 9.00098C4.01717 7.56473 4.87913 6.27325 6.01574 5.22807C7.15236 4.18288 8.52586 3.41724 10.0152 2.9991C11.5046 2.58097 13.0778 2.52027 14.5988 2.82155C16.1198 3.12282 17.5394 3.77676 18.74 4.71998L23 9.00098M1 14.001L5.26 18.282C6.46056 19.2252 7.88024 19.8791 9.4012 20.1804C10.9222 20.4817 12.4954 20.421 13.9848 20.0029C15.4741 19.5847 16.8476 18.8191 17.9843 17.7739C19.1209 16.7287 19.9828 15.4372 20.49 14.001" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </view>
      </view>
    </view>
    <view class="result-content">
      <text v-if="content" class="result-text">{{ content }}</text>
      <text v-else class="result-placeholder">{{ placeholder }}</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  title: {
    type: String,
    default: '生成结果'
  },
  content: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '结果将在这里显示...'
  }
})

const emit = defineEmits(['copy', 'regenerate'])

const onCopy = () => {
  uni.setClipboardData({
    data: props.content || '',
    success: () => {
      uni.showToast({ title: '已复制', icon: 'none' })
      emit('copy')
    }
  })
}

const onRegenerate = () => {
  emit('regenerate')
}
</script>

<style lang="scss" scoped>
.result-area {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  box-shadow: $shadow-card;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
  
  .result-title {
    font-size: $font-size-md;
    font-weight: 600;
    color: $text-primary;
  }
  
  .result-actions {
    display: flex;
    gap: $spacing-sm;
  }
  
  .action-btn {
    width: 56rpx;
    height: 56rpx;
    border-radius: $radius-pill;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    
    &:active {
      background-color: $border-color;
    }
    
    .action-icon {
      width: 32rpx;
      height: 32rpx;
      color: $text-secondary;
    }
  }
}

.result-content {
  min-height: 240rpx;
  background-color: $bg-gray;
  border-radius: $radius-md;
  padding: $spacing-md;
  
  .result-text {
    font-size: $font-size-md;
    color: $text-primary;
    line-height: 1.7;
    white-space: pre-wrap;   /* 保留换行符，自动换行 */
    word-break: break-word;  /* 长单词/URL 换行 */
  }
  
  .result-placeholder {
    font-size: $font-size-md;
    color: $text-tertiary;
    line-height: 1.7;
  }
}
</style>
