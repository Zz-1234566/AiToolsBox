<template>
  <view class="page-container">
    <view class="forget-card animate-fade-in-up">
      <!-- 标题 -->
      <view class="title-section">
        <text class="title">智汇工具箱</text>
        <text class="subtitle">找回账号</text>
      </view>

      <!-- 输入邮箱查询 -->
      <view class="form-section" v-if="!result">
        <view class="input-group" :class="{ shake: shakeField === 'email' }">
          <text class="input-label">邮箱</text>
          <input
            class="input-field"
            type="text"
            v-model="email"
            placeholder="请输入注册时使用的邮箱"
            placeholder-class="placeholder"
          />
        </view>
        <button class="btn-primary press-scale" @click="handleFind" :disabled="loading">
          {{ loading ? '查询中...' : '查 询' }}
        </button>
      </view>

      <!-- 查询结果 -->
      <view class="result-section animate-fade-in-up" v-else>
        <text class="result-label">你的账号是</text>
        <text class="result-account">{{ result.account }}</text>
        <text class="result-username">用户名：{{ result.username }}</text>
        <text class="result-hint">请牢记账号，登录时需要使用</text>
        <button class="btn-primary press-scale" @click="goToLogin">去登录</button>
      </view>

      <!-- 底部链接 -->
      <view class="footer-link">
        <text class="link-text">已想起账号？</text>
        <text class="link-action" @click="goBack">返回登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { findAccountApi } from '@/api/user'

const email = ref('')
const loading = ref(false)
const shakeField = ref('')
const result = ref(null)

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

const handleFind = async () => {
  if (!email.value) {
    triggerShake('email')
    uni.showToast({ title: '请输入邮箱', icon: 'none' })
    return
  }
  const emailRegex = /^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/
  if (!emailRegex.test(email.value)) {
    triggerShake('email')
    uni.showToast({ title: '邮箱格式不正确', icon: 'none' })
    return
  }

  loading.value = true
  try {
    const res = await findAccountApi(email.value.trim())
    result.value = res.data
  } catch (e) {
    // request.js 已经统一弹出错误提示，页面无需重复提示
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  // 回填账号：登录页 onShow 会读取 pendingAccount
  if (result.value) {
    uni.setStorageSync('pendingAccount', result.value.account)
  }
  goBack()
}

const goBack = () => {
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

.forget-card {
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

.result-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: $spacing-lg;

  .result-label {
    font-size: $font-size-sm;
    color: $text-tertiary;
    margin-bottom: $spacing-lg;
  }

  .result-account {
    font-size: $font-size-xxl;
    font-weight: 700;
    color: $text-primary;
    letter-spacing: 2rpx;
    margin-bottom: $spacing-md;
  }

  .result-username {
    font-size: $font-size-md;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
  }

  .result-hint {
    font-size: $font-size-sm;
    color: $text-tertiary;
    margin-bottom: $spacing-md;
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
