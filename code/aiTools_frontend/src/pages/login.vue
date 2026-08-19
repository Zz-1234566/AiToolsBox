<template>
  <view class="page-container">
    <view class="login-card animate-fade-in-up">
      <!-- 标题 -->
      <view class="title-section">
        <text class="title">智汇工具箱</text>
        <text class="subtitle">登录你的账号</text>
      </view>

      <!-- 表单 -->
      <view class="form-section">
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

        <view class="input-group" :class="{ shake: shakeField === 'password' }">
          <text class="input-label">密码</text>
          <input
            class="input-field"
            :type="showPassword ? 'text' : 'password'"
            v-model="form.password"
            placeholder="请输入密码"
            placeholder-class="placeholder"
          />
        </view>

        <button class="btn-primary press-scale" @click="handleLogin" :disabled="loading">
          {{ loading ? '登录中...' : '登 录' }}
        </button>
      </view>

      <!-- 底部链接 -->
      <view class="footer-link">
        <text class="link-text">还没有账号？</text>
        <text class="link-action" @click="goToRegister">去注册</text>
      </view>
      <view class="footer-link">
        <text class="link-text">忘记账号？</text>
        <text class="link-action" @click="goToForget">找回账号</text>
      </view>
      <view class="footer-link">
        <text class="link-text">忘记密码？</text>
        <text class="link-action" @click="goToResetPassword">重置密码</text>
      </view>
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

const goToForget = () => {
  uni.navigateTo({ url: '/pages/forget' })
}

const goToResetPassword = () => {
  uni.navigateTo({ url: '/pages/reset-password' })
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

.login-card {
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
