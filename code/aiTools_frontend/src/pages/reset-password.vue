<template>
  <view class="page-container">
    <view class="reset-card animate-fade-in-up">
      <!-- 标题 -->
      <view class="title-section">
        <text class="title">智汇工具箱</text>
        <text class="subtitle">重置密码</text>
      </view>

      <!-- 表单 -->
      <view class="form-section">
        <view class="input-group" :class="{ shake: shakeField === 'email' }">
          <text class="input-label">邮箱</text>
          <view class="code-row">
            <input
              class="input-field code-input"
              type="text"
              v-model="form.email"
              placeholder="请输入注册时使用的邮箱"
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

        <view class="input-group" :class="{ shake: shakeField === 'account' }">
          <text class="input-label">账号</text>
          <input
            class="input-field"
            type="text"
            v-model="form.account"
            placeholder="请输入账号（如 AIT12345678）"
            placeholder-class="placeholder"
          />
        </view>

        <view class="input-group" :class="{ shake: shakeField === 'code' }">
          <text class="input-label">邮箱验证码</text>
          <input
            class="input-field"
            type="text"
            v-model="form.code"
            placeholder="请输入邮箱收到的验证码"
            placeholder-class="placeholder"
          />
        </view>

        <view class="input-group" :class="{ shake: shakeField === 'newPassword' }">
          <text class="input-label">新密码</text>
          <input
            class="input-field"
            type="password"
            v-model="form.newPassword"
            placeholder="请输入新密码（6-20位）"
            placeholder-class="placeholder"
          />
        </view>

        <view class="input-group" :class="{ shake: shakeField === 'confirmPassword' }">
          <text class="input-label">确认新密码</text>
          <input
            class="input-field"
            type="password"
            v-model="form.confirmPassword"
            placeholder="请再次输入新密码"
            placeholder-class="placeholder"
          />
        </view>

        <button class="btn-primary press-scale" @click="handleSubmit" :disabled="loading">
          {{ loading ? '提交中...' : '重置密码' }}
        </button>
      </view>

      <!-- 底部链接 -->
      <view class="footer-link">
        <text class="link-text">已想起密码？</text>
        <text class="link-action" @click="goToLogin">返回登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { sendCodeApi, resetPasswordApi } from '@/api/user'

const form = ref({
  email: '',
  account: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
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
    await sendCodeApi({ email: form.value.email.trim(), type: 'reset-password' })
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

const handleSubmit = async () => {
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
  if (!form.value.account) {
    triggerShake('account')
    uni.showToast({ title: '请输入账号', icon: 'none' })
    return
  }
  if (!form.value.code) {
    triggerShake('code')
    uni.showToast({ title: '请输入邮箱验证码', icon: 'none' })
    return
  }
  if (!form.value.newPassword) {
    triggerShake('newPassword')
    uni.showToast({ title: '请输入新密码', icon: 'none' })
    return
  }
  if (form.value.newPassword.length < 6 || form.value.newPassword.length > 20) {
    triggerShake('newPassword')
    uni.showToast({ title: '新密码长度需在6-20位之间', icon: 'none' })
    return
  }
  if (!form.value.confirmPassword) {
    triggerShake('confirmPassword')
    uni.showToast({ title: '请输入确认新密码', icon: 'none' })
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    triggerShake('confirmPassword')
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await resetPasswordApi({
      account: form.value.account.trim(),
      code: form.value.code.trim(),
      newPassword: form.value.newPassword,
      confirmPassword: form.value.confirmPassword
    })
    uni.showToast({ title: '密码重置成功', icon: 'success' })
    setTimeout(() => {
      goToLogin()
    }, 800)
  } catch (e) {
    // request.js 已经统一弹出错误提示，页面无需重复提示
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/login' })
  }
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

.reset-card {
  width: 100%;
  max-width: 680rpx;
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-xl $spacing-lg;
  box-shadow: $shadow-float;
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
</style>
