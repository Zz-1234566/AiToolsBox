<template>
  <view class="page-container">
    <view class="register-card animate-fade-in-up">
      <!-- 标题 -->
      <view class="title-section">
        <text class="title">智汇工具箱</text>
        <text class="subtitle">创建新账号</text>
      </view>

      <!-- 表单 -->
      <view class="form-section">
        <view class="input-group" :class="{ shake: shakeField === 'username' }">
          <text class="input-label">用户名</text>
          <input
            class="input-field"
            type="text"
            v-model="form.username"
            placeholder="请输入用户名"
            placeholder-class="placeholder"
          />
        </view>

        <view class="input-group" :class="{ shake: shakeField === 'password' }">
          <text class="input-label">密码</text>
          <input
            class="input-field"
            type="password"
            v-model="form.password"
            placeholder="请输入密码"
            placeholder-class="placeholder"
          />
        </view>

        <view class="input-group" :class="{ shake: shakeField === 'confirmPassword' }">
          <text class="input-label">确认密码</text>
          <input
            class="input-field"
            type="password"
            v-model="form.confirmPassword"
            placeholder="请再次输入密码"
            placeholder-class="placeholder"
          />
        </view>

        <view class="input-group" :class="{ shake: shakeField === 'code' }">
          <text class="input-label">邮箱验证码</text>
          <view class="code-row">
            <input
              class="input-field code-input"
              type="text"
              v-model="form.code"
              placeholder="请输入邮箱验证码"
              placeholder-class="placeholder"
            />
            <button
              class="code-btn"
              :disabled="countdown > 0 || sendingCode"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `重新发送(${countdown}s)` : '发送验证码' }}
            </button>
          </view>
        </view>

        <view class="input-group" :class="{ shake: shakeField === 'email' }">
          <text class="input-label">邮箱</text>
          <input
            class="input-field"
            type="text"
            v-model="form.email"
            placeholder="请输入邮箱（用于找回账号）"
            placeholder-class="placeholder"
          />
        </view>

        <button class="btn-primary press-scale" @click="handleRegister" :disabled="loading">
          {{ loading ? '注册中...' : '注 册' }}
        </button>
      </view>

      <!-- 底部链接 -->
      <view class="footer-link">
        <text class="link-text">已有账号？</text>
        <text class="link-action" @click="goToLogin">去登录</text>
      </view>

      <!-- 注册成功弹窗 -->
      <view class="success-modal" v-if="showSuccess">
        <view class="success-content animate-scale-in">
          <text class="success-title">注册成功</text>
          <text class="success-desc">你的账号是：</text>
          <text class="success-account">{{ registeredAccount }}</text>
          <text class="success-hint">请牢记你的账号，登录时需要使用</text>
          <button class="btn-success press-scale" @click="goToLogin">去登录</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
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
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg;
}

.register-card {
  width: 100%;
  max-width: 680rpx;
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-xl $spacing-lg;
  box-shadow: $shadow-float;
  position: relative;
}

.title-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: $spacing-xl;

  .title {
    font-size: $font-size-xxl;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: $spacing-sm;
  }

  .subtitle {
    font-size: $font-size-md;
    color: $text-tertiary;
  }
}

.form-section {
  margin-bottom: $spacing-lg;
}

.input-group {
  margin-bottom: $spacing-md;

  .input-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    margin-bottom: $spacing-xs;
    display: block;
  }

  .input-field {
    width: 100%;
    height: 96rpx;
    padding: 0 $spacing-md;
    font-size: $font-size-md;
    color: $text-primary;
    background-color: $bg-gray;
    border-radius: $radius-md;
    border: 2rpx solid transparent;
    transition: border-color 0.2s;

    &:focus {
      border-color: $text-primary;
    }
  }
}

.placeholder {
  color: $text-tertiary;
  font-size: $font-size-md;
}

.code-row {
  display: flex;
  align-items: center;

  .code-input {
    flex: 1;
    width: auto;
  }

  .code-btn {
    flex-shrink: 0;
    height: 96rpx;
    line-height: 96rpx;
    padding: 0 $spacing-md;
    margin-left: $spacing-sm;
    font-size: $font-size-sm;
    font-weight: 600;
    color: #FFFFFF;
    background-color: $text-primary;
    border-radius: $radius-md;
    border: none;
    transition: opacity 0.2s;

    &:active {
      opacity: 0.8;
    }

    &[disabled] {
      opacity: 0.5;
    }
  }
}
.btn-primary {
  width: 100%;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  font-size: $font-size-lg;
  font-weight: 600;
  color: #FFFFFF;
  background-color: $text-primary;
  border-radius: $radius-md;
  border: none;
  margin-top: $spacing-md;
  transition: opacity 0.2s, transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);

  &:active {
    opacity: 0.8;
  }

  &[disabled] {
    opacity: 0.5;
  }
}

.footer-link {
  display: flex;
  align-items: center;
  justify-content: center;

  .link-text {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }

  .link-action {
    font-size: $font-size-sm;
    color: $text-primary;
    font-weight: 600;
    margin-left: 8rpx;

    &:active {
      opacity: 0.7;
    }
  }
}

.success-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.success-content {
  width: 580rpx;
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  display: flex;
  flex-direction: column;
  align-items: center;

  .success-title {
    font-size: $font-size-xl;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: $spacing-md;
  }

  .success-desc {
    font-size: $font-size-md;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
  }

  .success-account {
    font-size: $font-size-xl;
    font-weight: 700;
    color: $text-primary;
    background-color: $bg-gray;
    padding: $spacing-sm $spacing-lg;
    border-radius: $radius-md;
    margin-bottom: $spacing-md;
    letter-spacing: 2rpx;
  }

  .success-hint {
    font-size: $font-size-sm;
    color: $text-tertiary;
    margin-bottom: $spacing-lg;
    text-align: center;
  }

  .btn-success {
    width: 100%;
    height: 88rpx;
    line-height: 88rpx;
    text-align: center;
    font-size: $font-size-md;
    font-weight: 600;
    color: #FFFFFF;
    background-color: $text-primary;
    border-radius: $radius-md;
    border: none;
    transition: opacity 0.2s, transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);

    &:active {
      opacity: 0.8;
    }
  }
}
</style>
