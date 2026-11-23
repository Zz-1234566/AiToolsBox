<template>
  <view class="page-header">
    <view class="header-inner">
      <view v-if="showBack" class="back-btn" @click="onBack">
        <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M15 19L8 12L15 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      <view class="title-wrap">
        <text class="title">{{ title }}</text>
        <text v-if="subtitle" class="subtitle">{{ subtitle }}</text>
      </view>
      <view class="actions">
        <slot name="actions"></slot>
      </view>
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
  /** 副标题：设计稿里二级页标题下的一行说明，可选 */
  subtitle: {
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
/*
 * 对齐 v6 设计稿：左对齐 24px 大标题、透明底、无分隔线、返回键为纯图标。
 * 底色用 var(--bg-page) 而非透明，保证 sticky 吸顶时下方内容不会透出来。
 */
.page-header {
  background-color: var(--bg-page);
  padding-top: var(--status-bar-height);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  min-height: 88rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 32rpx 16rpx;
}

.back-btn {
  width: 56rpx;
  height: 56rpx;
  margin-left: -16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--text-primary);

  .icon {
    width: 44rpx;
    height: 44rpx;
  }
}

.title-wrap {
  flex: 1;
  min-width: 0;
}

.title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  letter-spacing: -1rpx;
  color: var(--text-primary);
  line-height: 1.2;
}

.subtitle {
  display: block;
  font-size: 24rpx;
  color: var(--text-tertiary);
  margin-top: 6rpx;
  line-height: 1.4;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}
</style>
