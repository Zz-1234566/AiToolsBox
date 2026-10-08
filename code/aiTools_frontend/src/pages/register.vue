<template>
  <view class="auth-wrap">
    <!-- 返回 -->
    <view class="auth-back" @click="goBack">
      <text class="auth-back__icon">‹</text>
    </view>

    <view class="auth-logo">AIT</view>
    <text class="auth-brand">AI Tools Box</text>

    <text class="auth-title">创建账号</text>
    <text class="auth-sub">欢迎加入 AI Tools Box</text>

    <!-- 邮箱 -->
    <view class="auth-field" :class="{ shake: shakeField === 'email' }">
      <text class="af-icon">✉️</text>
      <view class="af-body">
        <text class="af-label">邮箱</text>
        <input class="af-input" v-model="form.email" placeholder="请输入邮箱" placeholder-class="af-placeholder" />
      </view>
    </view>

    <!-- 验证码 -->
    <view class="auth-field" :class="{ shake: shakeField === 'code' }">
      <text class="af-icon">🛡</text>
      <view class="af-body">
        <text class="af-label">验证码</text>
        <input class="af-input" v-model="form.code" placeholder="请输入验证码" placeholder-class="af-placeholder" />
      </view>
      <text class="af-code" :class="{ 'af-code--disabled': countdown > 0 || sendingCode }" @click="handleSendCode">
        {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
      </text>
    </view>

    <!-- 用户名 -->
    <view class="auth-field" :class="{ shake: shakeField === 'username' }">
      <text class="af-icon">👤</text>
      <view class="af-body">
        <text class="af-label">用户名</text>
        <input class="af-input" v-model="form.username" placeholder="请输入用户名" placeholder-class="af-placeholder" />
      </view>
    </view>

    <!-- 设置密码 -->
    <view class="auth-field" :class="{ shake: shakeField === 'password' }">
      <text class="af-icon">🔒</text>
      <view class="af-body">
        <text class="af-label">设置密码</text>
        <input class="af-input" :type="showPwd ? 'text' : 'password'" v-model="form.password" placeholder="请输入 6-16 位密码" placeholder-class="af-placeholder" />
      </view>
      <text class="af-eye" @click="showPwd = !showPwd">{{ showPwd ? '🙈' : '👁' }}</text>
    </view>

    <!-- 确认密码 -->
    <view class="auth-field" :class="{ shake: shakeField === 'confirmPassword' }">
      <text class="af-icon">🔒</text>
      <view class="af-body">
        <text class="af-label">确认密码</text>
        <input class="af-input" :type="showPwd2 ? 'text' : 'password'" v-model="form.confirmPassword" placeholder="请再次输入密码" placeholder-class="af-placeholder" />
      </view>
      <text class="af-eye" @click="showPwd2 = !showPwd2">{{ showPwd2 ? '🙈' : '👁' }}</text>
    </view>

    <view class="btn-solid btn-register" :class="{ 'btn-solid--disabled': loading }" @click="loading ? null : handleRegister()">
      {{ loading ? '注册中...' : '注册' }}
    </view>

    <view class="auth-foot">
      <text>已有账号？</text>
      <text class="auth-foot__link" @click="goToLoginPage">立即登录</text>
    </view>

    <!-- 注册成功弹层 -->
    <view v-if="showSuccess" class="success-mask">
      <view class="success-card">
        <view class="success-icon">✓</view>
        <text class="success-title">注册成功</text>
        <text class="success-tip">你的账号是</text>
        <text class="success-account">{{ registeredAccount }}</text>
        <text class="success-note">请牢记账号，可用于登录与找回密码</text>
        <view class="btn-solid" @click="goToLogin">去登录</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { safeBack } from '@/utils/pageTransition'
import { request } from '../api/request'
import { sendCodeApi } from '../api/user'

const form = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  code: ''
})

const loading = ref(false)
const showSuccess = ref(false)
const registeredAccount = ref('')
const shakeField = ref('')
const countdown = ref(0)
const sendingCode = ref(false)
const showPwd = ref(false)
const showPwd2 = ref(false)
let countdownTimer = null

// 页面卸载时清理倒计时定时器
onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
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

// 发送邮箱验证码：校验邮箱后触发 60s 倒计时
const handleSendCode = async () => {
  if (countdown.value > 0 || sendingCode.value) return
  if (!form.value.email) {
    triggerShake('email')
    uni.showToast({ title: '请输入邮箱', icon: 'none' })
    return
  }
  const emailRegex = /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/
  if (!emailRegex.test(form.value.email)) {
    triggerShake('email')
    uni.showToast({ title: '邮箱格式不正确', icon: 'none' })
    return
  }

  sendingCode.value = true
  try {
    await sendCodeApi({ email: form.value.email.trim(), type: 'register' })
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    countdown.value = 60
    if (countdownTimer) {
      clearInterval(countdownTimer)
    }
    countdownTimer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } catch (e) {
    // request.js 已经统一弹出错误提示，页面无需重复提示
  } finally {
    sendingCode.value = false
  }
}

const handleRegister = async () => {
  if (!form.value.username) {
    triggerShake('username')
    uni.showToast({ title: '请输入用户名', icon: 'none' })
    return
  }
  if (!form.value.password) {
    triggerShake('password')
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }
  if (!form.value.confirmPassword) {
    triggerShake('confirmPassword')
    uni.showToast({ title: '请输入确认密码', icon: 'none' })
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    triggerShake('confirmPassword')
    uni.showToast({ title: '两次密码不一致', icon: 'none' })
    return
  }
  if (!form.value.email) {
    triggerShake('email')
    uni.showToast({ title: '请输入邮箱', icon: 'none' })
    return
  }
  const emailRegex = /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/
  if (!emailRegex.test(form.value.email)) {
    triggerShake('email')
    uni.showToast({ title: '邮箱格式不正确', icon: 'none' })
    return
  }
  if (!form.value.code) {
    triggerShake('code')
    uni.showToast({ title: '请输入邮箱验证码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = await request({
      url: '/api/user/register',
      method: 'POST',
      data: {
        username: form.value.username,
        email: form.value.email,
        password: form.value.password,
        confirmPassword: form.value.confirmPassword,
        code: form.value.code.trim()
      }
    })

    registeredAccount.value = res.data.account
    showSuccess.value = true
  } catch (e) {
    // request.js 已经统一弹出错误提示，页面无需重复提示
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  // 用 storage 兜底保存账号
  uni.setStorageSync('pendingAccount', registeredAccount.value)
  uni.navigateTo({ url: `/pages/login?account=${registeredAccount.value}` })
}

const goToLoginPage = () => {
  uni.navigateTo({ url: '/pages/login' })
}

const goBack = () => safeBack('/pages/login')
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.auth-wrap {
  min-height: 100vh;
  background: #fff;
  padding: 0 40rpx 48rpx;
  box-sizing: border-box;
}

.auth-back {
  padding: 24rpx 0 8rpx;
}
.auth-back__icon {
  font-size: 56rpx;
  color: #111827;
  line-height: 1;
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
  margin: 8rpx auto 24rpx;
}
.auth-brand {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 64rpx;
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
  margin-bottom: 40rpx;
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
.af-body { flex: 1; min-width: 0; }
.af-label {
  display: block;
  font-size: 22rpx;
  color: #9CA3AF;
  margin-bottom: 4rpx;
}
.af-input {
  width: 100%;
  font-size: 28rpx;
  color: #111827;
  background: transparent;
}
.af-placeholder { color: #D1D5DB; }
.af-eye { font-size: 34rpx; flex-shrink: 0; }
.af-code {
  font-size: 26rpx;
  color: #3B82F6;
  font-weight: 500;
  flex-shrink: 0;
}
.af-code--disabled { color: #9CA3AF; }

.btn-register { margin-top: 40rpx; }

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

.shake { animation: shake-x 0.4s; }
@keyframes shake-x {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-12rpx); }
  75% { transform: translateX(12rpx); }
}

/* 注册成功弹层 */
.success-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 0 60rpx;
}
.success-card {
  width: 100%;
  background: #fff;
  border-radius: 32rpx;
  padding: 56rpx 40rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.success-icon {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #34D399, #10B981);
  color: #fff;
  font-size: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
}
.success-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #111827;
  margin-bottom: 24rpx;
}
.success-tip { font-size: 26rpx; color: #9CA3AF; }
.success-account {
  font-size: 40rpx;
  font-weight: 700;
  color: #3B82F6;
  letter-spacing: 2rpx;
  margin: 12rpx 0 16rpx;
}
.success-note {
  font-size: 24rpx;
  color: #9CA3AF;
  margin-bottom: 40rpx;
  text-align: center;
}
</style>
