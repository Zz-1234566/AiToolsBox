<template>
  <view class="page page--no-tabbar" :style="[themeStyle, { paddingBottom: '0' }]">
    <!-- Hero：大居中头像 + 用户名 + @uid -->
    <section class="me-hero" @click="goToProfile">
      <view class="avatar avatar--xl" style="background: var(--brand-primary);">
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
        <view class="stat-item__icon"><view class="ICO"><svg viewBox="0 0 24 24" style="width:100%;height:100%;fill:var(--text-secondary)"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg></view></view>
        <text class="stat-item__label">提示词管理</text>
      </view>
      <view class="stat-item" @click="goToHistory">
        <view class="stat-item__icon"><view class="ICO"><svg viewBox="0 0 24 24" style="width:100%;height:100%;fill:var(--text-secondary)"><path d="M13 3a9 9 0 00-9 9H1l3.89 3.89.07.14L9 12H6a7 7 0 117 7 6.96 6.96 0 01-4.95-2.05l-1.42 1.42A9 9 0 1013 3zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8z"/></svg></view></view>
        <text class="stat-item__label">使用记录</text>
      </view>
    </section>

    <!-- 设置列表（账号设置 / 关于） -->
    <section class="me-list">
      <view v-if="adminMode" class="me-item" @click="goToAdminProfiles">
        <view class="me-item__icon"><view class="ICO"><svg viewBox="0 0 24 24" style="width:100%;height:100%;fill:var(--text-secondary)"><path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/></svg></view></view>
        <text class="me-item__label">用户画像词条</text>
        <text class="me-item__chev">›</text>
      </view>
      <view class="me-item" @click="goToMyModels">
        <text class="me-item__label">我的 AI 模型</text>
        <text class="me-item__chev">›</text>
      </view>
      <view class="me-item" @click="goToSettings">
        <view class="me-item__icon"><view class="ICO"><svg viewBox="0 0 24 24" style="width:100%;height:100%;fill:var(--text-secondary)"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 00.12-.61l-1.92-3.32a.488.488 0 00-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 00-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.488.488 0 00-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 00-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 00-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1112 8.4a3.6 3.6 0 010 7.2z"/></svg></view></view>
        <text class="me-item__label">账号设置</text>
        <text class="me-item__chev">›</text>
      </view>
      <view class="me-item" style="border-bottom: none;" @click="goToAbout">
        <view class="me-item__icon"><view class="ICO"><svg viewBox="0 0 24 24" style="width:100%;height:100%;fill:var(--text-secondary)"><path d="M12 2a10 10 0 100 20 10 10 0 000-20zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg></view></view>
        <text class="me-item__label">关于 AI Tools Box</text>
        <text class="me-item__chev">›</text>
      </view>
    </section>
  </view>
</template>

<script setup>
import { STORAGE_KEYS } from '@/utils/auth'
import { themeStyle, refreshTabBarStyle } from '@/utils/theme'
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'

const userInfo = ref(null)

const initials = computed(() => {
  if (!userInfo.value) return '登'
  const name = userInfo.value.nickname || userInfo.value.username || ''
  return name ? name.charAt(0).toUpperCase() : 'U'
})

const isLoggedIn = () => !!userInfo.value

/** 管理员模式：只有管理员能看到「用户画像词条」入口 */
const adminMode = computed(() => !!(userInfo.value && userInfo.value.role === 'admin'))

const goToAdminProfiles = () => go('/pages/admin-profiles')

const loadUserInfo = () => {
  userInfo.value = uni.getStorageSync(STORAGE_KEYS.USER_INFO) || null
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
const goToMyModels = () => {
  if (!isLoggedIn()) return go('/pages/login')
  go('/pages/my-models')
}

onShow(() => {
  // tabBar 配色与主题同步（主题是在 settings 这个非 tabBar 页切换的）
  refreshTabBarStyle()
  loadUserInfo()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page, #F9FAFB);
  /* App 端自绘导航栏（navigationStyle: custom）：顶部避让状态栏/刘海。
     uni 注入 --status-bar-height，H5 端为 0，不影响布局。
     放在 .page 而非 .me-hero，是为了让渐变背景延伸到状态栏下方、
     内容（头像）却被正确推下去。 */
  padding-top: var(--status-bar-height, 0px);
}

/* ===== Hero ===== */
.stat-item__icon view,
.stat-item__icon svg,
.me-item__icon view,
.me-item__icon svg { width: 40rpx; height: 40rpx; }

.me-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  /* 参考图：资料区不用暖色渐变，跟随页面底色 */
  background: var(--bg-page);
  padding: calc(var(--status-bar-height, 0px) + 40rpx) 32rpx 56rpx;
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
  border-bottom: 1rpx solid var(--border-light, var(--border-color));
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