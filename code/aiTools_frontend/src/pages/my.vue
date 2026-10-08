<template>
  <view class="page page--no-tabbar" style="padding-bottom: 0;">
    <!-- Hero：大居中头像 + 用户名 + @uid -->
    <section class="me-hero" @click="goToProfile">
      <view class="avatar avatar--xl" style="background: linear-gradient(135deg, #93C5FD, #3B82F6);">
        <image v-if="userInfo && userInfo.avatar" class="avatar-img" :src="userInfo.avatar" mode="aspectFill"></image>
        <text v-else class="avatar-letter">{{ initials }}</text>
      </view>
      <view class="me-hero__info">
        <text class="me-hero__name">{{ userInfo ? (userInfo.username || '用户') : '未登录，点击登录' }}</text>
        <text class="me-hero__sub">{{ userInfo ? '@' + userInfo.account : '@guest' }}</text>
      </view>
    </section>

    <!-- 功能入口（提示词管理 / 使用记录） -->
    <section class="stats">
      <view class="stat-item" @click="goToPromptList">
        <text class="stat-item__icon">✍️</text>
        <text class="stat-item__label">提示词管理</text>
      </view>
      <view class="stat-item" @click="goToHistory">
        <text class="stat-item__icon">📜</text>
        <text class="stat-item__label">使用记录</text>
      </view>
    </section>

    <!-- 设置列表（账号设置 / 关于） -->
    <section class="me-list">
      <view class="me-item" @click="goToSettings">
        <text class="me-item__icon">⚙️</text>
        <text class="me-item__label">账号设置</text>
        <text class="me-item__chev">›</text>
      </view>
      <view class="me-item" style="border-bottom: none;" @click="goToAbout">
        <text class="me-item__icon">ℹ️</text>
        <text class="me-item__label">关于 AI Tools Box</text>
        <text class="me-item__chev">›</text>
      </view>
    </section>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'

const userInfo = ref(null)

const initials = computed(() => {
  if (!userInfo.value) return '登'
  const name = userInfo.value.nickname || userInfo.value.username || ''
  return name ? name.charAt(0).toUpperCase() : 'U'
})

const isLoggedIn = () => !!userInfo.value

const loadUserInfo = () => {
  userInfo.value = uni.getStorageSync('userInfo') || null
}

const go = (url) => uni.navigateTo({ url })

const goToProfile = () => {
  if (!isLoggedIn()) return go('/pages/login')
  go('/pages/profile')
}

const goToPromptList = () => {
  if (!isLoggedIn()) return go('/pages/login')
  go('/pages/prompt-list')
}
const goToHistory = () => {
  if (!isLoggedIn()) return go('/pages/login')
  go('/pages/history')
}
const goToSettings = () => go('/pages/settings')
const goToAbout = () => go('/pages/about')

onShow(() => {
  loadUserInfo()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page, #F9FAFB);
}

/* ===== Hero ===== */
.me-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  background: linear-gradient(135deg, #EFF6FF 0%, #F5F3FF 100%);
  padding: 80rpx 32rpx 64rpx;
  cursor: pointer;
}
.me-hero__info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.me-hero__name {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
  text-align: center;
}

.me-hero__sub {
  display: block;
  font-size: 24rpx;
  color: var(--text-tertiary, #9CA3AF);
  text-align: center;
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
.avatar--xl {
  width: 160rpx;
  height: 160rpx;
  border: 4rpx solid #FFFFFF;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
}
.avatar-letter {
  font-size: 32rpx;
  color: white;
  font-weight: 600;
}
.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

/* ===== 数据统计 ===== */
.stats {
  margin: 60rpx 32rpx 0;
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 12rpx 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 8rpx 0;
  position: relative;
  cursor: pointer;
}
.stat-item__icon {
  font-size: 40rpx;
}
.stat-item__label {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
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
  cursor: pointer;
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
</style>