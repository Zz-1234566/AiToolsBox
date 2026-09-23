<template>
  <view class="page page--no-tabbar" style="padding-bottom: 0;">
    <!-- Hero：渐变 + 头像 + 名 + 会员有效期 + 高频会员 tag + VIP 卡 -->
    <section class="me-hero">
      <view class="me-hero__user">
        <view class="avatar avatar--lg" style="background: linear-gradient(135deg, #93C5FD, #3B82F6);">
          <text class="avatar-letter">U</text>
        </view>
        <view class="me-hero__info">
          <text class="me-hero__name">用户123456</text>
          <text class="me-hero__exp">会员有效期：2025-12-31</text>
          <text class="vip-tag">🏅 高频会员</text>
        </view>
      </view>
      <!-- VIP 卡 -->
      <view class="vip-card">
        <view class="vip-card__text">
          <text class="vip-card__title">会员特权</text>
          <text class="vip-card__sub">解锁更多高级工具和功能</text>
        </view>
        <button class="vip-card__btn">立即开通</button>
      </view>
    </section>

    <!-- 数据统计（4 项：收藏 / 最近使用 / 工作流 / 使用记录） -->
    <section class="stats">
      <view class="stat-item">
        <text class="stat-item__icon">⭐</text>
        <text class="stat-item__label">我的收藏</text>
        <text class="stat-item__num">12</text>
      </view>
      <view class="stat-item">
        <text class="stat-item__icon">🕘</text>
        <text class="stat-item__label">最近使用</text>
        <text class="stat-item__num">23</text>
      </view>
      <view class="stat-item">
        <text class="stat-item__icon">📋</text>
        <text class="stat-item__label">我的工作流</text>
        <text class="stat-item__num">5</text>
      </view>
      <view class="stat-item stat-item--link">
        <text class="stat-item__icon">📜</text>
        <text class="stat-item__label">使用记录</text>
        <text class="stat-item__chev">›</text>
      </view>
    </section>

    <!-- 设置列表（账号设置 / 通知 / 深色模式 / 关于） -->
    <section class="me-list">
      <view class="me-item">
        <text class="me-item__icon">⚙️</text>
        <text class="me-item__label">账号设置</text>
        <text class="me-item__chev">›</text>
      </view>
      <view class="me-item">
        <text class="me-item__icon">🔔</text>
        <text class="me-item__label">通知</text>
        <text class="me-item__chev">›</text>
      </view>
      <view class="me-item">
        <text class="me-item__icon">🌙</text>
        <text class="me-item__label">深色模式</text>
        <label class="switch-toggle" @click="toggleDark">
          <view class="switch-track" :class="{ 'is-on': darkMode }">
            <view class="switch-thumb"></view>
          </view>
        </label>
      </view>
      <view class="me-item" style="border-bottom: none;">
        <text class="me-item__icon">ℹ️</text>
        <text class="me-item__label">关于 AI Tools Box</text>
        <text class="me-item__chev">›</text>
      </view>
    </section>
  </view>
</template>

<script setup>
import { ref } from 'vue'
const darkMode = ref(false)
function toggleDark() {
  darkMode.value = !darkMode.value
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page, #F9FAFB);
}

/* ===== Hero ===== */
.me-hero {
  position: relative;
  background: linear-gradient(135deg, #EFF6FF 0%, #F5F3FF 100%);
  padding: 20rpx 20rpx 0;
  padding-bottom: 64rpx;
}
.me-hero__user {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.me-hero__info {
  flex: 1;
}
.me-hero__name {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin-bottom: 4rpx;
}
.me-hero__exp {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  margin-bottom: 8rpx;
}
.vip-tag {
  display: inline-block;
  font-size: 22rpx;
  font-weight: 500;
  background: linear-gradient(135deg, #FCD34D, #F59E0B);
  color: white;
  padding: 2rpx 16rpx;
  border-radius: 8rpx;
}

.avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  flex-shrink: 0;
}
.avatar--lg {
  width: 96rpx;
  height: 96rpx;
}
.avatar-letter {
  font-size: 32rpx;
  color: white;
  font-weight: 600;
}

/* VIP 卡（绝对定位覆盖到 hero 与 stats 之间） */
.vip-card {
  position: absolute;
  left: 32rpx;
  right: 32rpx;
  bottom: 0;
  transform: translateY(50%);
  background: #1F2937;
  border-radius: 24rpx;
  padding: 24rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);
}
.vip-card__text {
  flex: 1;
}
.vip-card__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: white;
  margin-bottom: 4rpx;
}
.vip-card__sub {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}
.vip-card__btn {
  height: 64rpx;
  padding: 0 28rpx;
  background: var(--brand-primary, #3B82F6);
  color: white;
  border: none;
  border-radius: 9999rpx;
  font-size: 26rpx;
  font-weight: 500;
}

/* ===== 数据统计 ===== */
.stats {
  margin: 60rpx 32rpx 0;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 12rpx 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 8rpx 0;
  position: relative;
}
.stat-item__icon {
  font-size: 40rpx;
}
.stat-item__label {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
}
.stat-item__num {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
.stat-item--link {
  position: relative;
}
.stat-item__chev {
  position: absolute;
  right: 8rpx;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-tertiary, #9CA3AF);
  font-size: 36rpx;
}

/* ===== 设置列表 ===== */
.me-list {
  margin: 32rpx 32rpx 32rpx;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.me-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 32rpx;
  border-bottom: 1rpx solid var(--border-light, #F3F4F6);
}
.me-item__icon {
  font-size: 40rpx;
  width: 48rpx;
  text-align: center;
}
.me-item__label {
  flex: 1;
  font-size: 32rpx;
  color: var(--text-primary, #111827);
}
.me-item__chev {
  color: var(--text-tertiary, #9CA3AF);
  font-size: 36rpx;
}

/* ===== Switch ===== */
.switch-toggle {
  position: relative;
  display: inline-block;
}
.switch-track {
  width: 88rpx;
  height: 48rpx;
  background: #E5E7EB;
  border-radius: 24rpx;
  position: relative;
  cursor: pointer;
  transition: background 200ms ease;
  display: block;

  &.is-on {
    background: var(--brand-primary, #3B82F6);
    .switch-thumb {
      transform: translateX(40rpx);
    }
  }
}
.switch-thumb {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 40rpx;
  height: 40rpx;
  background: white;
  border-radius: 50%;
  transition: transform 200ms ease;
  box-shadow: 0 1rpx 3rpx rgba(0, 0, 0, 0.2);
}
</style>