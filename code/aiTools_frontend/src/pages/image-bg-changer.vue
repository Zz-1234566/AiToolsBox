<template>
  <view class="page page--no-tabbar">
    <view class="topbar">
      <view class="topbar__back" hover-class="topbar__back--hover" @click="goBack">
        <text class="topbar__back-icon">‹</text>
      </view>
      <text class="topbar__title">图片换背景图</text>
    </view>

    <view class="placeholder">
      <view class="placeholder__icon">🖼️</view>
      <text class="placeholder__title">图片换背景图（等 UI 截图）</text>
      <text class="placeholder__desc">
        后端已就绪：POST /api/ai-office/image-bg-changer（multipart，最多 10 张）<br/>
        字段：files / background（背景图）/ backgroundColor（red/blue/white）<br/>
        返回 BatchUploadResponse，轮询 GET /api/ai-office/batch/{batchId}/completed
      </text>
      <text class="placeholder__hint">前端 UI 待用户截图后实现</text>
    </view>
  </view>
</template>

<script setup>
const goBack = () => {
  uni.navigateBack({ delta: 1, fail: () => uni.switchTab({ url: '/pages/index' }) })
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #F9FAFB; }
.topbar {
  display: flex; align-items: center; height: 88rpx; padding: 0 24rpx; background: #fff;
  border-bottom: 1rpx solid #F3F4F6;
  &__back { width: 60rpx; height: 60rpx; display: flex; align-items: center; justify-content: center; }
  &__back-icon { font-size: 48rpx; color: #1F2937; }
  &__title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #0f172a; }
}
.placeholder {
  margin: 80rpx 48rpx;
  padding: 48rpx 32rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex; flex-direction: column; align-items: center;
  &__icon { font-size: 96rpx; margin-bottom: 24rpx; }
  &__title { font-size: 32rpx; font-weight: 600; color: #1F2937; margin-bottom: 16rpx; }
  &__desc { font-size: 24rpx; color: #6B7280; line-height: 1.6; text-align: center; }
  &__hint { margin-top: 24rpx; font-size: 22rpx; color: #F59E0B; }
}
</style>
