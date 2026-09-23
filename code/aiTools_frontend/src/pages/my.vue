<template>
  <view class="page-container animate-fade-in">
    <page-header :title="t('my.title')" :showBack="false"></page-header>

    <scroll-view scroll-y class="page-content">
      <!-- 用户信息卡片 - 已登录（按设计稿：渐变背景 + 头像 + VIP 卡叠加） -->
      <view v-if="isLoggedIn" class="user-hero">
        <view class="user-row" @click="goToProfile">
          <view class="avatar">
            <image v-if="userInfo.avatar" class="avatar-img" :src="userInfo.avatar" mode="aspectFill"></image>
            <svg v-else class="avatar-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.5"/>
              <path d="M4 20C4 15.5817 7.58172 12 12 12C16.4183 12 20 15.5817 20 20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </view>
          <view class="user-info">
            <text class="user-name">{{ userInfo.username || userInfo.account }}</text>
            <text class="user-desc">ID: {{ userInfo.account }}</text>
          </view>
        </view>
        <!-- VIP 卡（深色叠加在 Hero 底部） -->
        <view class="vip-card press-scale" @click="showToast('vip')">
          <view class="vip-text">
            <text class="vip-title">会员特权</text>
            <text class="vip-sub">解锁更多高级工具和功能</text>
          </view>
          <view class="vip-btn">立即开通</view>
        </view>
      </view>

      <!-- 用户信息卡片 - 未登录 -->
      <view v-else class="user-hero user-hero--empty">
        <view class="user-row" @click="goToLogin">
          <view class="avatar">
            <svg class="avatar-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.5"/>
              <path d="M4 20C4 15.5817 7.58172 12 12 12C16.4183 12 20 15.5817 20 20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </view>
          <view class="user-info">
            <text class="user-name">{{ t('my.loginBtn') }}</text>
            <text class="user-desc">{{ t('my.loginHint') }}</text>
          </view>
        </view>
      </view>

      <!-- 数据统计（4 项一行，按设计稿：我的收藏 / 最近使用 / 我的工作流 / 使用记录） -->
      <view class="stats">
        <view class="stat-item press-scale" @click="goToFavorites">
          <text class="stat-icon">⭐</text>
          <text class="stat-label">{{ t('my.favorites') }}</text>
          <text class="stat-num">12</text>
        </view>
        <view class="stat-item press-scale" @click="goToHistory">
          <text class="stat-icon">🕘</text>
          <text class="stat-label">{{ t('my.history') }}</text>
          <text class="stat-num">23</text>
        </view>
        <view class="stat-item press-scale" @click="goToPrompt">
          <text class="stat-icon">📋</text>
          <text class="stat-label">{{ t('my.prompts') }}</text>
          <text class="stat-num">5</text>
        </view>
        <view class="stat-item stat-item--link press-scale" @click="goToHistory">
          <text class="stat-icon">📜</text>
          <text class="stat-label">使用记录</text>
          <text class="stat-chev">›</text>
        </view>
      </view>

      <!-- 设置列表（按设计稿：图标 + 文字 + chevron / Switch） -->
      <view class="menu-list">
        <view class="menu-item press-scale" @click="goToSettings">
          <view class="menu-left">
            <text class="menu-icon">⚙️</text>
            <text class="menu-text">{{ t('my.settings') }}</text>
          </view>
          <text class="menu-chev">›</text>
        </view>
        <view class="menu-item press-scale" @click="showToast('aboutUs')">
          <view class="menu-left">
            <text class="menu-icon">ℹ️</text>
            <text class="menu-text">{{ t('my.aboutUs') }}</text>
          </view>
          <text class="menu-chev">›</text>
        </view>
        <view class="menu-item press-scale" @click="showToast('privacy')">
          <view class="menu-left">
            <text class="menu-icon">🔒</text>
            <text class="menu-text">{{ t('my.privacy') }}</text>
          </view>
          <text class="menu-chev">›</text>
        </view>
        <view class="menu-item press-scale" @click="handleClearCache">
          <view class="menu-left">
            <text class="menu-icon">🧹</text>
            <text class="menu-text">{{ t('my.clearCache') }}</text>
          </view>
          <text class="menu-extra">{{ cacheSizeText }}</text>
        </view>
        <view v-if="isLoggedIn" class="menu-item menu-item--logout press-scale" @click="handleLogout">
          <view class="menu-left">
            <text class="menu-icon">↩️</text>
            <text class="menu-text logout-text">{{ t('my.logout') }}</text>
          </view>
          <text class="menu-chev">›</text>
        </view>
      </view>

      <!-- 版本信息 -->
      <view class="version-info">
        <text class="version-text">{{ t('common.appName') }} v1.0.0</text>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/PageHeader.vue'
import { logoutApi } from '@/api/user'

const { t } = useI18n()

const isLoggedIn = ref(false)
const userInfo = ref({
  id: null,
  account: '',
  username: '',
  avatar: ''
})
const cacheSizeText = ref('0KB')

// 清除缓存时保留登录态和用户偏好
const KEYS_TO_KEEP = ['token', 'userInfo', 'language', 'theme']

// 检查登录状态
const checkLoginStatus = () => {
  const token = uni.getStorageSync('token')
  const storedUserInfo = uni.getStorageSync('userInfo')
  if (token && storedUserInfo) {
    isLoggedIn.value = true
    userInfo.value = storedUserInfo
  } else {
    isLoggedIn.value = false
    userInfo.value = { id: null, account: '', username: '', avatar: '' }
  }
}

// 每次页面显示都检查登录状态（Tab 切换也会触发，登录后切回本页即可刷新）
onShow(() => {
  checkLoginStatus()
  getStorageSize()
})

// 从设置页返回时刷新状态（页面每次显示时重新检查）
uni.$on('loginStatusChanged', () => {
  checkLoginStatus()
})

const showToast = (key) => {
  uni.showToast({ title: t('toast.featureDev', { name: t(`my.${key}`) }), icon: 'none' })
}

const getStorageSize = () => {
  try {
    const info = uni.getStorageInfoSync()
    const size = info.currentSize || 0 // 单位 KB
    if (size === 0) {
      cacheSizeText.value = '0KB'
    } else if (size < 1024) {
      cacheSizeText.value = size + 'KB'
    } else {
      cacheSizeText.value = (size / 1024).toFixed(1) + 'MB'
    }
  } catch (e) {
    cacheSizeText.value = '0KB'
  }
}

const handleClearCache = () => {
  uni.showModal({
    title: '清除缓存',
    content: '将清除本地临时缓存，不影响登录状态和历史记录。',
    confirmColor: '#3B82F6',
    success: async (res) => {
      if (!res.confirm) return
      try {
        const info = uni.getStorageInfoSync()
        const keys = info.keys || []
        keys.forEach((key) => {
          if (!KEYS_TO_KEEP.includes(key)) {
            uni.removeStorageSync(key)
          }
        })
        cacheSizeText.value = '0KB'
        uni.showToast({ title: '缓存已清除', icon: 'none' })
      } catch (e) {
        uni.showToast({ title: '清除失败', icon: 'none' })
      }
    }
  })
}

const goToLogin = () => {
  uni.navigateTo({ url: '/pages/login' })
}

const goToHistory = () => {
  if (!isLoggedIn.value) {
    uni.showToast({ title: t('toast.needLogin'), icon: 'none' })
    setTimeout(() => {
      uni.navigateTo({ url: '/pages/login' })
    }, 600)
    return
  }
  uni.navigateTo({ url: '/pages/history' })
}

const goToPrompt = () => {
  if (!isLoggedIn.value) {
    uni.showToast({ title: t('toast.needLogin'), icon: 'none' })
    setTimeout(() => {
      uni.navigateTo({ url: '/pages/login' })
    }, 600)
    return
  }
  uni.navigateTo({ url: '/pages/prompt-list' })
}

const goToFavorites = () => {
  uni.navigateTo({ url: '/pages/favorites' })
}

const goToSettings = () => {
  uni.navigateTo({ url: '/pages/settings' })
}

const goToProfile = () => {
  uni.navigateTo({ url: '/pages/profile' })
}

const handleLogout = () => {
  uni.showModal({
    title: t('my.logout'),
    content: t('toast.logoutConfirm'),
    confirmColor: '#3B82F6',
    success: async (res) => {
      if (res.confirm) {
        try {
          await logoutApi()
        } catch (e) {
          // 接口失败也继续本地清理，保证用户能退出登录
        }
        uni.removeStorageSync('token')
        uni.removeStorageSync('userInfo')
        isLoggedIn.value = false
        userInfo.value = { id: null, account: '', username: '', avatar: '' }
        uni.showToast({ title: t('toast.loggedOut'), icon: 'none' })
        setTimeout(() => {
          uni.navigateTo({ url: '/pages/login' })
        }, 600)
      }
    }
  })
}
</script>

<style lang="scss" scoped>
@use '@/uni.scss' as *;
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  flex-direction: column;
}

.page-content {
  flex: 1;
  padding: 0 $spacing-4;
}

/* ============================================================
   Hero 区（蓝紫渐变 + VIP 卡叠加）
   ============================================================ */
.user-hero {
  position: relative;
  background: linear-gradient(135deg, #EFF6FF 0%, #F5F3FF 100%);
  margin: $spacing-3 (-$spacing-4) 0;
  padding: $spacing-5 $spacing-4 $spacing-10;
  border-radius: $radius-lg;
}

.user-row {
  display: flex;
  align-items: center;
  transition: opacity 0.1s ease-in-out;

  &:active {
    opacity: 0.85;
  }
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: $radius-pill;
  background: linear-gradient(135deg, #93C5FD 0%, #3B82F6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: $spacing-3;
  overflow: hidden;
  flex-shrink: 0;
  color: #FFFFFF;

  .avatar-icon {
    width: 56rpx;
    height: 56rpx;
  }

  .avatar-img {
    width: 100%;
    height: 100%;
  }
}

.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;

  .user-name {
    font-size: $font-size-lg;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 4rpx;
  }

  .user-desc {
    font-size: $font-size-sm;
    color: $text-secondary;
  }
}

.vip-card {
  position: absolute;
  left: $spacing-4;
  right: $spacing-4;
  bottom: 0;
  transform: translateY(50%);
  background-color: #1F2937;
  border-radius: $radius-lg;
  padding: $spacing-3 $spacing-4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: $shadow-float;
  transition: opacity 0.1s ease-in-out;

  &:active {
    opacity: 0.85;
  }

  .vip-text {
    flex: 1;
    color: #FFFFFF;
    min-width: 0;

    .vip-title {
      display: block;
      font-size: $font-size-md;
      font-weight: 600;
      margin-bottom: 2rpx;
    }

    .vip-sub {
      display: block;
      font-size: $font-size-xs;
      color: rgba(255, 255, 255, 0.8);
    }
  }

  .vip-btn {
    height: 56rpx;
    padding: 0 $spacing-3;
    background-color: $brand-primary;
    color: #FFFFFF;
    border-radius: $radius-pill;
    font-size: $font-size-sm;
    font-weight: 500;
    display: flex;
    align-items: center;
    flex-shrink: 0;
  }
}

/* ============================================================
   数据统计（4 项）
   ============================================================ */
.stats {
  margin: 80rpx $spacing-2 $spacing-4;
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-3 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  box-shadow: $shadow-card;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: $spacing-2 0;
  position: relative;
  transition: opacity 0.1s ease-in-out;

  &:active {
    opacity: 0.85;
  }

  .stat-icon {
    font-size: 40rpx;
  }

  .stat-label {
    font-size: $font-size-xs;
    color: $text-secondary;
  }

  .stat-num {
    font-size: $font-size-lg;
    font-weight: 600;
    color: $text-primary;
  }
}

.stat-item--link {
  .stat-chev {
    position: absolute;
    right: $spacing-1;
    top: 50%;
    transform: translateY(-50%);
    color: $text-tertiary;
    font-size: $font-size-lg;
  }
}

/* ============================================================
   设置列表
   ============================================================ */
.menu-list {
  background-color: $bg-white;
  border-radius: $radius-lg;
  margin-bottom: $spacing-4;
  box-shadow: $shadow-card;
  overflow: hidden;
}

.menu-item {
  height: 104rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 $spacing-4;
  border-bottom: 1rpx solid $divider-color;
  transition: opacity 0.1s ease-in-out, background-color 0.1s ease-in-out;

  &:last-child {
    border-bottom: none;
  }

  &:active {
    opacity: 0.85;
    background-color: $bg-gray;
  }

  .menu-left {
    flex: 1;
    display: flex;
    align-items: center;
    gap: $spacing-3;
    min-width: 0;
  }

  .menu-icon {
    font-size: 36rpx;
    width: 40rpx;
    text-align: center;
  }

  .menu-text {
    font-size: $font-size-md;
    color: $text-primary;
  }

  .logout-text {
    color: $color_danger;
  }

  .menu-extra {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }

  .menu-chev {
    color: $text-tertiary;
    font-size: $font-size-lg;
  }
}

.menu-item--logout {
  .menu-text {
    color: $color_danger;
  }
}

/* ============================================================
   版本
   ============================================================ */
.version-info {
  display: flex;
  justify-content: center;
  padding: $spacing-6 0;

  .version-text {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}
</style>