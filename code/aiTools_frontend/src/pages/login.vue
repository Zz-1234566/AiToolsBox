<template>
  <view class="auth-wrap">
    <!-- 顶部 LOGO -->
    <view class="auth-logo">AIT</view>
    <text class="auth-brand">AI Tools Box</text>

    <text class="auth-title">欢迎回来</text>
    <text class="auth-sub">一个入口，使用各种 AI 工具</text>

    <!-- 账号 -->
    <view class="auth-field" :class="{ shake: shakeField === 'account' }">
      <text class="af-icon">👤</text>
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
      <text class="af-icon">🔒</text>
      <input
        class="af-input"
        :type="showPassword ? 'text' : 'password'"
        v-model="form.password"
        placeholder="请输入密码"
        placeholder-class="af-placeholder"
      />
      <text class="af-eye" @click="showPassword = !showPassword">{{ showPassword ? '🙈' : '👁' }}</text>
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
import { ref, nextTick } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '../api/request'
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
  const pending = uni.getStorageSync('pendingAccount')
  if (pending) {
    form.value.account = pending
    uni.removeStorageSync('pendingAccount')
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
    uni.setStorageSync('token', res.data.token)
    uni.setStorageSync('userInfo', res.data.userInfo)
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
  background: #fff;
  padding: 0 40rpx 48rpx;
  box-sizing: border-box;
}

.auth-logo {
  width: 144rpx;
  height: 144rpx;
  border-radius: 40rpx;
  background: linear-gradient(135deg, #3B82F6 0%, #6366F1 100%);
  color: #fff;
  font-size: 52rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 64rpx auto 24rpx;
}
.auth-brand {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 72rpx;
}

.auth-title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: #111827;
  margin-bottom: 8rpx;
}
.auth-sub {
  display: block;
  font-size: 28rpx;
  color: #9CA3AF;
  margin-bottom: 48rpx;
}

.auth-field {
  display: flex;
  align-items: center;
  gap: 24rpx;
  height: 112rpx;
  padding: 0 32rpx;
  border: 2rpx solid #E5E7EB;
  border-radius: 24rpx;
  margin-bottom: 24rpx;
  background: #fff;
}
.af-icon { font-size: 36rpx; flex-shrink: 0; }
.af-input {
  flex: 1;
  font-size: 28rpx;
  color: #111827;
  background: transparent;
}
.af-placeholder { color: #D1D5DB; }
.af-eye { font-size: 34rpx; flex-shrink: 0; }

.auth-forgot {
  text-align: right;
  font-size: 26rpx;
  color: #3B82F6;
  margin-bottom: 40rpx;
}

.auth-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 40rpx;
  font-size: 26rpx;
  color: #9CA3AF;
}
.auth-foot__link {
  color: #3B82F6;
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
