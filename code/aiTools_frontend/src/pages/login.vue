<template>
  <view class="auth-wrap">
    <!-- 顶部 LOGO -->
    <image class="auth-logo-img" :class="{ 'auth-logo-img--dark': isDarkMode }" src="/static/ref/logo.png" mode="widthFix" />
    <text class="auth-brand">AI Tools Box</text>

    <text class="auth-title">欢迎回来</text>
    <text class="auth-sub">一个入口，使用各种 AI 工具</text>

    <!-- 账号 -->
    <view class="auth-field" :class="{ shake: shakeField === 'account' }">
      <view class="af-icon"><svg viewBox="0 0 24 24" style="width:36rpx;height:36rpx;fill:var(--text-tertiary)"><path d="M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-5 0-9 2.5-9 5.5V22h18v-2.5c0-3-4-5.5-9-5.5z"/></svg></view>
      <input
        class="af-input"
        type="text"
        v-model="form.account"
        placeholder="请输入手机号或账号"
        placeholder-class="af-placeholder"
      />
    </view>

    <!-- 密码 -->
    <view class="auth-field" :class="{ shake: shakeField === 'password' }">
      <view class="af-icon"><svg viewBox="0 0 24 24" style="width:36rpx;height:36rpx;fill:var(--text-tertiary)"><path d="M17 9V7a5 5 0 10-10 0v2H5v12h14V9h-2zM9 7a3 3 0 116 0v2H9V7z"/></svg></view>
      <input
        class="af-input"
        :type="showPassword ? 'text' : 'password'"
        v-model="form.password"
        placeholder="请输入密码"
        placeholder-class="af-placeholder"
      />
      <view class="af-eye" @click="showPassword = !showPassword">
        <svg viewBox="0 0 24 24" style="width:40rpx;height:40rpx;fill:none;stroke:var(--text-tertiary);stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round">
          <path v-if="!showPassword" d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12z"/>
          <circle v-if="!showPassword" cx="12" cy="12" r="3"/>
          <path v-if="showPassword" d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24"/>
          <path v-if="showPassword" d="M1 1l22 22"/>
        </svg>
      </view>
    </view>

    <view class="auth-forgot" @click="goToResetPassword">忘记密码？</view>

    <view class="btn-solid" :class="{ 'btn-solid--disabled': loading }" @click="loading ? null : handleLogin()">
      {{ loading ? '登录中...' : '登录' }}
    </view>

    <view class="auth-foot">
      <text>还没有账号？</text>
      <text class="auth-foot__link" @click="goToRegister">立即注册</text>
    </view>
  </view>
</template>

<script setup>
import { STORAGE_KEYS } from '@/utils/auth'
import { ref, nextTick } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '../api/request'
import { isDarkMode } from '@/utils/theme'
import { switchTab } from '@/utils/pageTransition'

const form = ref({
  account: '',
  password: ''
})

const loading = ref(false)
const showPassword = ref(false)
const shakeField = ref('')

// 表单校验失败：给对应输入框加 shake class，0.4s 后移除
const triggerShake = (field) => {
  shakeField.value = ''
  nextTick(() => {
    shakeField.value = field
  })
  setTimeout(() => {
    shakeField.value = ''
  }, 400)
}

const applyPendingAccount = () => {
  // storage 兜底（注册成功 / 找回账号后回填账号）
  const pending = uni.getStorageSync(STORAGE_KEYS.PENDING_ACCOUNT)
  if (pending) {
    form.value.account = pending
    uni.removeStorageSync(STORAGE_KEYS.PENDING_ACCOUNT)
  }
}

onLoad((options) => {
  // 方式1：URL 参数
  if (options && options.account) {
    form.value.account = options.account
  }
  applyPendingAccount()
})

// 从找回账号页返回时（页面复用，onLoad 不再触发），回填查询到的账号
onShow(() => {
  applyPendingAccount()
})

const handleLogin = async () => {
  if (!form.value.account) {
    triggerShake('account')
    uni.showToast({ title: '请输入账号', icon: 'none' })
    return
  }
  if (!form.value.password) {
    triggerShake('password')
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }

  loading.value = true
  try {
    const res = await request({
      url: '/api/user/login',
      method: 'POST',
      data: {
        account: form.value.account,
        password: form.value.password
      }
    })

    // 存储 token 和用户信息
    uni.setStorageSync(STORAGE_KEYS.TOKEN, res.data.token)
    uni.setStorageSync(STORAGE_KEYS.USER_INFO, res.data.userInfo)
    // 通知其他页面（如“我的”Tab）登录状态已变化
    uni.$emit('loginStatusChanged')
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      switchTab('/pages/index')
    }, 1000)
  } catch (e) {
    // request.js 已经统一弹出错误提示，页面无需重复提示
  } finally {
    loading.value = false
  }
}

const goToRegister = () => {
  uni.navigateTo({ url: '/pages/register' })
}

const goToResetPassword = () => {
  uni.navigateTo({ url: '/pages/reset-password' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.auth-wrap {
  min-height: 100vh;
  background: var(--bg-card);
  /* App 端刘海屏：顶部避让状态栏（H5 端该变量为 0，不影响布局）。
     ⚠️ 用四值简写，避免后续 padding 简写把 padding-top 重置掉。 */
  padding: var(--status-bar-height, 0px) 40rpx 48rpx;
  box-sizing: border-box;
}

.auth-logo-img {
  display: block;
  width: 210rpx;
  height: auto;
  margin: 64rpx auto 24rpx;
}
/* 深色底上深色 logo 看不清 → 反白 */
.auth-logo-img--dark {
  filter: brightness(0) invert(1);
}
.auth-brand {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 72rpx;
}

.auth-title {
  display: block;
  font-size: 56rpx;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8rpx;
}
.auth-sub {
  display: block;
  font-size: 28rpx;
  color: var(--text-tertiary);
  margin-bottom: 48rpx;
}

.auth-field {
  display: flex;
  align-items: center;
  gap: 24rpx;
  height: 112rpx;
  padding: 0 32rpx;
  border: 2rpx solid var(--border-color);
  border-radius: 24rpx;
  margin-bottom: 24rpx;
  background: var(--bg-card);
}
.af-icon { font-size: 36rpx; flex-shrink: 0; }
.af-input {
  flex: 1;
  font-size: 28rpx;
  color: var(--text-primary);
  background: transparent;
}
.af-placeholder { color: var(--text-tertiary); }
.af-eye { display: flex; align-items: center; flex-shrink: 0; }

.auth-forgot {
  text-align: right;
  font-size: 26rpx;
  color: var(--brand-primary);
  margin-bottom: 40rpx;
}

.auth-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 40rpx;
  font-size: 26rpx;
  color: var(--text-tertiary);
}
.auth-foot__link {
  color: var(--brand-primary);
  font-weight: 600;
  margin-left: 6rpx;
}

/* 校验失败抖动 */
.shake {
  animation: shake-x 0.4s;
}
@keyframes shake-x {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-12rpx); }
  75% { transform: translateX(12rpx); }
}
</style>
