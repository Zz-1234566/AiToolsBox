<template>
  <view class="page-container animate-fade-in">
    <page-header :title="t('my.title')" :showBack="false"></page-header>
    
    <scroll-view scroll-y class="page-content">
      <!-- 用户信息卡片 - 已登录 -->
      <view v-if="isLoggedIn" class="user-card animate-fade-in-up" @click="goToProfile">
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
        <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      
      <!-- 用户信息卡片 - 未登录 -->
      <view v-else class="user-card animate-fade-in-up" @click="goToLogin">
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
        <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      
      <!-- 常用功能入口 -->
      <view class="quick-actions">
        <view class="action-item" @click="goToHistory">
          <view class="action-icon">
            <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 8V12L15 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              <path d="M3 12C3 16.9706 7.02944 21 12 21C16.9706 21 21 16.9706 21 12C21 7.02944 16.9706 3 12 3C7.02944 3 3 7.02944 3 12Z" stroke="currentColor" stroke-width="1.5"/>
            </svg>
          </view>
          <text class="action-text">{{ t('my.history') }}</text>
        </view>
        <view class="action-item" @click="goToPrompt">
          <view class="action-icon">
            <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M4 5C4 3.89543 4.89543 3 6 3H18C19.1046 3 20 3.89543 20 5V15C20 16.1046 19.1046 17 18 17H11L6 21V17H6C4.89543 17 4 16.1046 4 15V5Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
          <text class="action-text">{{ t('my.prompts') }}</text>
        </view>
        <view class="action-item" @click="goToFavorites">
          <view class="action-icon">
            <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M5 5C5 3.34315 6.34315 2 8 2H16C17.6569 2 19 3.34315 19 5V21L12 17.5L5 21V5Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
          <text class="action-text">{{ t('my.favorites') }}</text>
        </view>
        <view class="action-item" @click="goToSettings">
          <view class="action-icon">
            <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
              <path d="M19.4 15C19.2669 15.3016 19.2272 15.6362 19.286 15.9606C19.3448 16.285 19.4995 16.5843 19.73 16.82L19.79 16.88C19.976 17.0657 20.1235 17.2863 20.2241 17.5291C20.3248 17.7719 20.3766 18.0322 20.3766 18.295C20.3766 18.5578 20.3248 18.8181 20.2241 19.0609C20.1235 19.3037 19.976 19.5243 19.79 19.71C19.6043 19.896 19.3837 20.0435 19.1409 20.1441C18.8981 20.2448 18.6378 20.2966 18.375 20.2966C18.1122 20.2966 17.8519 20.2448 17.6091 20.1441C17.3663 20.0435 17.1457 19.896 16.96 19.71L16.9 19.65C16.6643 19.4195 16.365 19.2648 16.0406 19.206C15.7162 19.1472 15.3816 19.1869 15.08 19.32C14.7843 19.4467 14.532 19.6572 14.3553 19.9253C14.1786 20.1934 14.0853 20.5072 14.0867 20.8278L14.09 21.5C14.09 22.0304 13.8793 22.5391 13.5042 22.9142C13.1291 23.2934 12.6204 23.504 12.09 23.504C11.5596 23.504 11.0509 23.2934 10.6758 22.9142C10.3007 22.5391 10.09 22.0304 10.09 21.5L10.0867 20.8278C10.0881 20.5072 9.9948 20.1934 9.8181 19.9253C9.6414 19.6572 9.3891 19.4467 9.0933 19.32C8.7919 19.1869 8.4574 19.1472 8.133 19.206C7.8086 19.2648 7.5093 19.4195 7.2736 19.65L7.21 19.71C7.0243 19.896 6.8037 20.0435 6.5609 20.1441C6.3181 20.2448 6.0578 20.2966 5.795 20.2966C5.5322 20.2966 5.2719 20.2448 5.0291 20.1441C4.7863 20.0435 4.5657 19.896 4.38 19.71C4.194 19.5243 4.0465 19.3037 3.9459 19.0609C3.8452 18.8181 3.7934 18.5578 3.7934 18.295C3.7934 18.0322 3.8452 17.7719 3.9459 17.5291C4.0465 17.2863 4.194 17.0657 4.38 16.88L4.44 16.82C4.6705 16.5843 4.8252 16.285 4.884 15.9606C4.9428 15.6362 4.9031 15.3016 4.77 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
          <text class="action-text">{{ t('my.settings') }}</text>
        </view>
      </view>
      
      <!-- 设置列表 -->
      <view class="menu-list">
        <view class="menu-item press-scale" @click="showToast('aboutUs')">
          <text class="menu-text">{{ t('my.aboutUs') }}</text>
          <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </view>
        <view class="menu-item press-scale" @click="showToast('privacy')">
          <text class="menu-text">{{ t('my.privacy') }}</text>
          <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </view>
        <view class="menu-item press-scale" @click="handleClearCache">
          <text class="menu-text">{{ t('my.clearCache') }}</text>
          <text class="menu-extra">{{ cacheSizeText }}</text>
        </view>
        <view v-if="isLoggedIn" class="menu-item press-scale" @click="handleLogout">
          <text class="menu-text logout-text">{{ t('my.logout') }}</text>
          <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
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
import { ref, computed } from 'vue'
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
    confirmColor: '#211E1E',
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
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  flex-direction: column;
}

.page-content {
  flex: 1;
  padding: 0 $spacing-md;
}

.user-card {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-lg;
  margin-top: $spacing-md;
  margin-bottom: $spacing-md;
  display: flex;
  align-items: center;
  box-shadow: $shadow-card;
  
  &:active {
    background-color: $bg-gray;
  }
  
  .avatar {
    width: 120rpx;
    height: 120rpx;
    border-radius: $radius-pill;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: $spacing-md;
    overflow: hidden;
    
    .avatar-icon {
      width: 64rpx;
      height: 64rpx;
      color: $text-secondary;
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
    
    .user-name {
      font-size: $font-size-lg;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 8rpx;
    }
    
    .user-desc {
      font-size: $font-size-sm;
      color: $text-tertiary;
    }
  }
  
  .arrow-icon {
    width: 40rpx;
    height: 40rpx;
    color: $text-tertiary;
  }
}

.quick-actions {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
  display: flex;
  justify-content: space-around;
  box-shadow: $shadow-card;
  
  .action-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: $spacing-sm;
    
    &:active {
      opacity: 0.7;
    }
    
    .action-icon {
      width: 88rpx;
      height: 88rpx;
      border-radius: $radius-pill;
      background-color: $bg-gray;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: $spacing-sm;
      
      .icon {
        width: 44rpx;
        height: 44rpx;
        color: $text-primary;
      }
    }
    
    .action-text {
      font-size: $font-size-sm;
      color: $text-primary;
    }
  }
}

.menu-list {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: 0 $spacing-md;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-card;
  
  .menu-item {
    height: 104rpx;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1rpx solid $divider-color;
    
    &:last-child {
      border-bottom: none;
    }
    
    &:active {
      background-color: $bg-gray;
    }
    
    .menu-text {
      font-size: $font-size-md;
      color: $text-primary;
    }
    
    .logout-text {
      color: #e74c3c;
    }
    
    .menu-extra {
      font-size: $font-size-sm;
      color: $text-tertiary;
      margin-right: $spacing-sm;
    }
    
    .arrow-icon {
      width: 40rpx;
      height: 40rpx;
      color: $text-tertiary;
    }
  }
}

.version-info {
  display: flex;
  justify-content: center;
  padding: $spacing-xl 0;
  
  .version-text {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}
</style>
