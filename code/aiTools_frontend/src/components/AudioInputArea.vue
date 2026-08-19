<template>
  <view class="input-section">
    <view class="audio-placeholder">
      <svg class="audio-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="9" y="3" width="6" height="12" rx="3" stroke="currentColor" stroke-width="1.5"/>
        <path d="M5 11a7 7 0 0 0 14 0" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <line x1="12" y1="18" x2="12" y2="21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
      <text class="audio-tip">音频输入功能开发中</text>
      <text class="audio-desc">将支持录音上传，自动转文字后处理</text>
    </view>
  </view>
</template>

<script setup>
// 音频输入占位组件，无 props
</script>

<style lang="scss" scoped>
.input-section {
  height: 380rpx;
  margin-bottom: $spacing-md;
}

.audio-placeholder {
  height: 100%;
  background-color: $bg-white;
  border-radius: $radius-lg;
  border: 2rpx dashed $border-color;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;

  .audio-icon {
    width: 80rpx;
    height: 80rpx;
    color: $text-tertiary;
  }

  .audio-tip {
    font-size: $font-size-md;
    color: $text-secondary;
  }

  .audio-desc {
    font-size: $font-size-xs;
    color: $text-tertiary;
  }
}
</style>
