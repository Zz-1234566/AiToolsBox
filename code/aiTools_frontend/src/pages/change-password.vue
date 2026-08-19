<template>
  <view class="page-container">
    <page-header :title="'修改密码'" showBack></page-header>

    <view class="change-password-card animate-fade-in-up">
      <!-- 标题 -->
      <view class="title-section">
        <text class="subtitle">修改登录密码</text>
      </view>

      <!-- 表单 -->
      <view class="form-section">
        <view class="input-group" :class="{ shake: shakeField === 'oldPassword' }">
          <text class="input-label">旧密码</text>
          <input
            class="input-field"
            type="password"
            v-model="form.oldPassword"
            placeholder="请输入旧密码"
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
          {{ loading ? '提交中...' : '确认修改' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { changePasswordApi } from '@/api/user'

const form = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
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

const handleSubmit = async () => {
  if (!form.value.oldPassword) {
    triggerShake('oldPassword')
    uni.showToast({ title: '请输入旧密码', icon: 'none' })
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
    await changePasswordApi(form.value.oldPassword, form.value.newPassword)
    uni.showToast({ title: '密码修改成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 800)
  } catch (e) {
    // request.js 已经统一弹出错误提示（旧密码错误返回 1003）
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  flex-direction: column;
}

.change-password-card {
  margin: $spacing-lg $spacing-lg 0;
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

  .subtitle {
    font-size: $font-size-lg;
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
</style>
