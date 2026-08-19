<template>
  <view class="page-container animate-fade-in">
    <page-header title="论坛" :showBack="false"></page-header>

    <view class="page-content">
      <!-- 占位图标 -->
      <view class="placeholder-icon animate-fade-in-up">
        <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M21 12C21 16.4183 16.9706 20 12 20C10.7646 20 9.57906 19.7985 8.48212 19.4315L4 21L5.57116 16.7697C4.58804 15.4103 4 13.7688 4 12C4 7.58172 8.02944 4 12 4C16.9706 4 21 7.58172 21 12Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M8.5 10.5H15.5M8.5 14H12.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </view>
      <text class="placeholder-text animate-fade-in-up">论坛建设中，敬请期待</text>
    </view>

    <view class="safe-area-bottom"></view>
  </view>
</template>

<script setup>
import PageHeader from '@/components/PageHeader.vue'
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
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
}

.placeholder-icon {
  width: 168rpx;
  height: 168rpx;
  border-radius: $radius-lg;
  background-color: $bg-white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-card;

  .icon {
    width: 88rpx;
    height: 88rpx;
    color: $text-tertiary;
  }
}

.placeholder-text {
  margin-top: $spacing-lg;
  font-size: $font-size-md;
  color: $text-tertiary;
}
</style>
