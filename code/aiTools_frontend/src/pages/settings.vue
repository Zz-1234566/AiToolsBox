<template>
  <view class="page-container">
    <page-header :title="$t('settings.title')" showBack></page-header>

    <scroll-view scroll-y class="page-content">
      <!-- 外观设置 -->
      <view class="settings-group">
        <view class="group-title">{{ $t('settings.appearance') }}</view>
        <view class="group-card">
          <view class="setting-item">
            <view class="item-left">
              <view class="item-icon dark-icon">
                <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </view>
              <text class="item-text">{{ $t('settings.darkTheme') }}</text>
            </view>
            <view class="theme-switch" @click="toggleTheme">
              <view class="switch-track" :class="{ active: isDarkTheme }">
                <view class="switch-thumb"></view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 语言设置 -->
      <view class="settings-group">
        <view class="group-title">{{ $t('settings.language') }}</view>
        <view class="group-card">
          <view class="setting-item">
            <view class="item-left">
              <view class="item-icon lang-icon">
                <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5"/>
                  <path d="M2 12H22" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <path d="M12 2C14.5 5.5 16 8.5 16 12C16 15.5 14.5 18.5 12 22C9.5 18.5 8 15.5 8 12C8 8.5 9.5 5.5 12 2Z" stroke="currentColor" stroke-width="1.5"/>
                </svg>
              </view>
              <text class="item-text">{{ $t('settings.switchLabel') }}</text>
            </view>
            <view class="lang-switch" @click="toggleLanguage">
              <view class="switch-track" :class="{ active: isEnglish }">
                <view class="switch-thumb"></view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 账号设置 -->
      <view class="settings-group">
        <view class="group-title">{{ $t('settings.account') }}</view>
        <view class="group-card">
          <view class="setting-item" @click="handleLogin">
            <view class="item-left">
              <view class="item-icon account-icon">
                <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.5"/>
                  <path d="M4 20C4 15.5817 7.58172 12 12 12C16.4183 12 20 15.5817 20 20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                </svg>
              </view>
              <text class="item-text">{{ $t('settings.loginSwitch') }}</text>
            </view>
            <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
          <view class="setting-item" @click="goToChangePassword" v-if="isLoggedIn">
            <view class="item-left">
              <view class="item-icon password-icon">
                <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M6 11V8C6 4.68629 8.68629 2 12 2C15.3137 2 18 4.68629 18 8V11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <rect x="4" y="11" width="16" height="10" rx="2" stroke="currentColor" stroke-width="1.5"/>
                  <circle cx="12" cy="16" r="1.5" fill="currentColor"/>
                </svg>
              </view>
              <text class="item-text">{{ $t('settings.changePassword') }}</text>
            </view>
            <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
        </view>
      </view>

      <!-- 通用设置 -->
      <view class="settings-group">
        <view class="group-title">{{ $t('settings.general') }}</view>
        <view class="group-card">
          <view class="setting-item" @click="clearCache">
            <view class="item-left">
              <view class="item-icon cache-icon">
                <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M3 6H21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <path d="M19 6V20C19 20.5523 18.5523 21 18 21H6C5.44772 21 5 20.5523 5 20V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                </svg>
              </view>
              <text class="item-text">{{ $t('settings.clearCache') }}</text>
            </view>
            <text class="item-extra">12.5 MB</text>
          </view>
          <view class="setting-item" @click="goToAbout">
            <view class="item-left">
              <view class="item-icon about-icon">
                <svg class="icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5"/>
                  <path d="M12 16V12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                  <circle cx="12" cy="8" r="1" fill="currentColor"/>
                </svg>
              </view>
              <text class="item-text">{{ $t('settings.aboutUs') }}</text>
            </view>
            <svg class="arrow-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </view>
        </view>
      </view>

      <!-- 版本信息 -->
      <view class="version-info">
        <text class="version-text">{{ $t('common.appName') }} v{{ appVersion }}</text>
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
import { initTheme, toggleTheme as doToggleTheme } from '@/utils/theme'
import { requireLogin } from '@/utils/auth'

const { t, locale } = useI18n()

const isLoggedIn = ref(false)
const isDarkTheme = ref(initTheme())
const isEnglish = ref(locale.value === 'en')
const appVersion = ref('1.0.0')

onShow(() => {
  if (!requireLogin()) return
  isLoggedIn.value = !!uni.getStorageSync('token')
})

const toggleTheme = () => {
  isDarkTheme.value = !isDarkTheme.value
  doToggleTheme(isDarkTheme.value)
  uni.showToast({
    title: isDarkTheme.value ? t('toast.darkEnabled') : t('toast.lightEnabled'),
    icon: 'none'
  })
}

const toggleLanguage = () => {
  isEnglish.value = !isEnglish.value
  const newLocale = isEnglish.value ? 'en' : 'zh'
  locale.value = newLocale
  uni.setStorageSync('language', newLocale)

  // 同步更新 TabBar 文字
  uni.setTabBarItem({ index: 0, text: t('tabbar.home') })
  uni.setTabBarItem({ index: 1, text: t('tabbar.search') })
  uni.setTabBarItem({ index: 2, text: t('tabbar.my') })

  uni.showToast({
    title: isEnglish.value ? t('toast.switchedEnglish') : t('toast.switchedChinese'),
    icon: 'none'
  })
}

const handleLogin = () => {
  uni.showToast({ title: t('toast.featureDev', { name: 'Login' }), icon: 'none' })
}

const goToChangePassword = () => {
  uni.navigateTo({ url: '/pages/change-password' })
}

const clearCache = () => {
  uni.showModal({
    title: t('modal.clearCacheTitle'),
    content: t('modal.clearCacheContent'),
    confirmColor: isDarkTheme.value ? '#F0F0F0' : '#211E1E',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: t('toast.cacheCleared'), icon: 'none' })
      }
    }
  })
}

const goToAbout = () => {
  uni.showToast({ title: t('toast.featureDev', { name: 'About' }), icon: 'none' })
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

.settings-group {
  margin-top: $spacing-lg;

  .group-title {
    font-size: $font-size-sm;
    color: $text-tertiary;
    margin-bottom: $spacing-sm;
    padding-left: $spacing-sm;
  }

  .group-card {
    background-color: $bg-white;
    border-radius: $radius-lg;
    padding: 0 $spacing-md;
    box-shadow: $shadow-card;
  }
}

.setting-item {
  height: 112rpx;
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

  .item-left {
    display: flex;
    align-items: center;
    flex: 1;
    min-width: 0;
  }

  .item-icon {
    width: 72rpx;
    height: 72rpx;
    border-radius: $radius-md;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: $spacing-md;
    flex-shrink: 0;
    background-color: $bg-gray;

    .icon {
      width: 40rpx;
      height: 40rpx;
      color: $text-primary;
    }
  }

  .item-text {
    font-size: $font-size-md;
    color: $text-primary;
  }

  .item-extra {
    font-size: $font-size-sm;
    color: $text-tertiary;
    margin-right: $spacing-sm;
  }

  .arrow-icon {
    width: 40rpx;
    height: 40rpx;
    color: $text-tertiary;
    flex-shrink: 0;
  }
}

.theme-switch,
.lang-switch {
  flex-shrink: 0;

  .switch-track {
    width: 96rpx;
    height: 56rpx;
    border-radius: $radius-pill;
    background-color: $bg-gray;
    position: relative;
    transition: background-color 0.2s ease;

    &.active {
      background-color: $text-primary;

      .switch-thumb {
        transform: translateX(40rpx);
      }
    }
  }

  .switch-thumb {
    width: 48rpx;
    height: 48rpx;
    border-radius: $radius-pill;
    background-color: #FFFFFF;
    position: absolute;
    top: 4rpx;
    left: 4rpx;
    box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
    transition: transform 0.2s ease;
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
