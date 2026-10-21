<template>
  <view class="page-header">
    <view class="header-inner">
      <view v-if="showBack" class="back-btn" @click="onBack">
        <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M15 19L8 12L15 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      <view v-else class="placeholder"></view>
      <text class="title">{{ title }}</text>
      <view class="placeholder"></view>
    </view>
  </view>
</template>

<script setup>
import { safeBack } from '@/utils/pageTransition'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  showBack: {
    type: Boolean,
    default: false
  }
})

/**
 * 返回：复用项目统一的 safeBack（本组件被 6 个页面使用，不允许再自带一套）。
 * safeBack 内部会判 getCurrentPages().length，栈底时改走 reLaunch，
 * 避免 H5 刷新本页后点返回没反应。
 */
const onBack = () => {
  safeBack('/pages/index')
}
</script>

<style lang="scss" scoped>
.page-header {
  background-color: $bg-white;
  padding-top: var(--status-bar-height);
  border-bottom: 1rpx solid $divider-color;
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 $spacing-md;
}

.back-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-pill;
  background-color: $bg-gray;
  
  .icon {
    width: 40rpx;
    height: 40rpx;
    color: $text-primary;
  }
}

.placeholder {
  width: 64rpx;
  height: 64rpx;
}

.title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}
</style>
