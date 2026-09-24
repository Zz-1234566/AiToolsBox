<template>
  <view class="page page--no-tabbar">
    <!-- 顶栏：返回 + 标题 + 收藏 -->
    <view class="topbar">
      <view class="topbar__back" @click="goBack">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
        </svg>
      </view>
      <text class="topbar__title">{{ toolName }}</text>
      <view class="topbar__action" @click="toggleFav">
        <svg viewBox="0 0 24 24" fill="currentColor" style="color: var(--text-tertiary, #9CA3AF);">
          <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
        </svg>
      </view>
    </view>

    <scroll-view scroll-y class="page-content">
      <!-- 图片预览 -->
      <view class="proc-preview">
        <view class="proc-preview__inner">
          <text class="proc-preview__emoji">👟</text>
        </view>
      </view>

      <!-- AI 正在处理 -->
      <view class="proc-status">
        <view class="proc-status__spinner">
          <view class="spinner"></view>
        </view>
        <text class="proc-status__title">AI 正在处理中...</text>
        <text class="proc-status__sub">预计需要 10~30 秒，请耐心等待</text>
      </view>

      <!-- 进度条 -->
      <view class="proc-progress">
        <view class="proc-progress__bar">
          <view class="proc-progress__fill" :style="{ width: progress + '%' }"></view>
        </view>
        <text class="proc-progress__num">{{ progress }}%</text>
      </view>

      <!-- 步骤列表 -->
      <view class="proc-steps">
        <view
          v-for="(step, idx) in steps"
          :key="idx"
          class="proc-step"
          :class="{
            'proc-step--done': step.status === 'done',
            'proc-step--active': step.status === 'active',
            'proc-step--pending': step.status === 'pending'
          }"
        >
          <view class="proc-step__icon">
            <svg v-if="step.status === 'done'" viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
              <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
            </svg>
            <view v-else-if="step.status === 'active'" class="spinner spinner--sm"></view>
            <view v-else class="proc-step__dot"></view>
          </view>
          <view class="proc-step__body">
            <text class="proc-step__name">{{ step.name }}</text>
            <text class="proc-step__status">{{ stepStatusText(step.status) }}</text>
          </view>
        </view>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>

    <!-- 底部按钮 -->
    <view class="bottom-action">
      <view class="btn btn--secondary btn--block" @click="cancelProcess">取消处理</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const toolName = ref('图片去背景')
const progress = ref(45)
const favored = ref(false)
const steps = ref([
  { name: '智能识别主体', status: 'done' },
  { name: '精准去除背景', status: 'done' },
  { name: '生成透明 PNG', status: 'active' }
])

onLoad((options) => {
  if (options && options.name) {
    toolName.value = decodeURIComponent(options.name)
  }
})

const stepStatusText = (status) => {
  if (status === 'done') return '已完成'
  if (status === 'active') return '处理中...'
  return '等待中'
}

const toggleFav = () => {
  favored.value = !favored.value
  uni.showToast({ title: favored.value ? '已收藏' : '已取消收藏', icon: 'none' })
}

const goBack = () => {
  uni.navigateBack({ delta: 1 })
}

const cancelProcess = () => {
  uni.showModal({
    title: '取消处理',
    content: '确定要取消当前任务吗？已处理的进度将不会保留。',
    success: (res) => {
      if (res.confirm) {
        uni.navigateBack({ delta: 1 })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page, #F9FAFB);
  display: flex;
  flex-direction: column;
}

/* ===== Topbar ===== */
.topbar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-bottom: 1rpx solid var(--border-light, #F3F4F6);
  position: relative;
}
.topbar__back {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-primary, #111827);
  cursor: pointer;
}
.topbar__back svg {
  width: 40rpx;
  height: 40rpx;
}
.topbar__title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin: 0 16rpx;
}
.topbar__action {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.topbar__action svg {
  width: 36rpx;
  height: 36rpx;
}

/* ===== Page Content ===== */
.page-content {
  flex: 1;
  padding: 24rpx;
}

/* ===== Preview ===== */
.proc-preview {
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}
.proc-preview__inner {
  width: 100%;
  height: 360rpx;
  background: linear-gradient(135deg, #F3F4F6, #E5E7EB);
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.proc-preview__emoji {
  font-size: 120rpx;
}

/* ===== Proc Status ===== */
.proc-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 32rpx 0;
}
.proc-status__spinner {
  margin-bottom: 8rpx;
}
.proc-status__title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
.proc-status__sub {
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
}

/* ===== Spinner ===== */
.spinner {
  width: 48rpx;
  height: 48rpx;
  border: 4rpx solid #E5E7EB;
  border-top-color: var(--brand-primary, #3B82F6);
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}
.spinner--sm {
  width: 24rpx;
  height: 24rpx;
  border-width: 3rpx;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== Progress ===== */
.proc-progress {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.proc-progress__bar {
  flex: 1;
  height: 12rpx;
  background: var(--bg-disabled, #F3F4F6);
  border-radius: 9999rpx;
  overflow: hidden;
}
.proc-progress__fill {
  height: 100%;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  transition: width 0.3s ease;
}
.proc-progress__num {
  font-size: 24rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  min-width: 60rpx;
  text-align: right;
}

/* ===== Steps ===== */
.proc-steps {
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 24rpx;
}
.proc-step {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 16rpx 0;
}
.proc-step__icon {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.proc-step--done .proc-step__icon {
  background: var(--color-success, #10B981);
  color: #fff;
}
.proc-step--active .proc-step__icon {
  background: transparent;
}
.proc-step--pending .proc-step__icon {
  background: transparent;
}
.proc-step__dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--text-tertiary, #9CA3AF);
}
.proc-step__body {
  flex: 1;
}
.proc-step__name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 4rpx;
}
.proc-step__status {
  display: block;
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
}
.proc-step--done .proc-step__status {
  color: var(--color-success, #10B981);
}
.proc-step--active .proc-step__status {
  color: var(--brand-primary, #3B82F6);
}

/* ===== Bottom Action ===== */
.bottom-action {
  padding: 24rpx 32rpx;
  background: var(--bg-card, #FFFFFF);
  border-top: 1rpx solid var(--border-light, #F3F4F6);
}
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 500;
  cursor: pointer;
}
.btn--secondary {
  background: var(--bg-disabled, #F3F4F6);
  color: var(--text-primary, #111827);
}
.btn--block {
  width: 100%;
}

.safe-area-bottom {
  height: 60rpx;
}
</style>