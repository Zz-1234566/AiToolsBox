<template>
  <view class="cp-wrap">
    <!-- 返回 -->
    <view class="auth-back" @click="goBack">
      <text class="auth-back__icon">‹</text>
    </view>

    <text class="auth-title">修改密码</text>
    <text class="auth-sub">为了账号安全，请设置新密码</text>

    <!-- 原密码 -->
    <text class="cp-label">原密码</text>
    <view class="auth-field" :class="{ shake: shakeField === 'oldPassword' }">
      <text class="af-icon">🔒</text>
      <input class="af-input" :type="show1 ? 'text' : 'password'" v-model="form.oldPassword"
             placeholder="请输入当前密码" placeholder-class="af-placeholder" />
      <text class="af-eye" @click="show1 = !show1">{{ show1 ? '🙈' : '👁' }}</text>
    </view>

    <!-- 新密码 -->
    <text class="cp-label">新密码</text>
    <view class="auth-field" :class="{ shake: shakeField === 'newPassword' }">
      <text class="af-icon">🔒</text>
      <input class="af-input" :type="show2 ? 'text' : 'password'" v-model="form.newPassword"
             placeholder="请输入新密码" placeholder-class="af-placeholder" />
      <text class="af-eye" @click="show2 = !show2">{{ show2 ? '🙈' : '👁' }}</text>
    </view>

    <!-- 确认新密码 -->
    <text class="cp-label">确认新密码</text>
    <view class="auth-field" :class="{ shake: shakeField === 'confirmPassword' }">
      <text class="af-icon">🔒</text>
      <input class="af-input" :type="show3 ? 'text' : 'password'" v-model="form.confirmPassword"
             placeholder="请再次输入新密码" placeholder-class="af-placeholder" />
      <text class="af-eye" @click="show3 = !show3">{{ show3 ? '🙈' : '👁' }}</text>
    </view>

    <!-- 密码强度 -->
    <view class="pwd-strength">
      <view class="pwd-strength__row">
        <text class="pwd-strength__label">密码强度：</text>
        <view class="pwd-strength__bars">
          <view v-for="i in 4" :key="i" class="pwd-strength__bar"
                :class="i <= strength.score ? 'pwd-strength__bar--' + strength.level : ''"></view>
        </view>
      </view>
      <view class="pwd-strength__scale">
        <text>弱</text><text>中</text><text>强</text>
      </view>
    </view>

    <view class="btn-solid btn-submit" :class="{ 'btn-solid--disabled': loading }" @click="loading ? null : handleSubmit()">
      {{ loading ? '提交中...' : '确认修改' }}
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { changePasswordApi } from '@/api/user'

const form = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
const shakeField = ref('')
const show1 = ref(false)
const show2 = ref(false)
const show3 = ref(false)

/** 密码强度：按长度/字符种类打分（纯前端，0~4 段） */
const strength = computed(() => {
  const p = form.value.newPassword || ''
  if (!p) return { score: 0, level: 'low' }
  let score = 0
  if (p.length >= 6) score++
  if (p.length >= 10) score++
  if (/[a-zA-Z]/.test(p) && /\d/.test(p)) score++
  if (/[^a-zA-Z0-9]/.test(p)) score++
  score = Math.min(score, 4)
  const level = score <= 1 ? 'low' : (score <= 2 ? 'mid' : 'high')
  return { score, level }
})

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

const goBack = () => {
  uni.navigateBack({ delta: 1 })
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.cp-wrap {
  min-height: 100vh;
  background: #fff;
  padding: 0 40rpx 48rpx;
  box-sizing: border-box;
}

.auth-back { padding: 24rpx 0 8rpx; }
.auth-back__icon { font-size: 56rpx; color: #111827; line-height: 1; }

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

.cp-label {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 16rpx;
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

/* 密码强度条 */
.pwd-strength { margin: 8rpx 0 24rpx; }
.pwd-strength__row { display: flex; align-items: center; gap: 24rpx; }
.pwd-strength__label { font-size: 26rpx; color: #4B5563; flex-shrink: 0; }
.pwd-strength__bars { flex: 1; display: flex; gap: 8rpx; }
.pwd-strength__bar {
  flex: 1; height: 12rpx; border-radius: 6rpx; background: #E5E7EB;
}
.pwd-strength__bar--low { background: #EF4444; }
.pwd-strength__bar--mid { background: #F59E0B; }
.pwd-strength__bar--high { background: #10B981; }
.pwd-strength__scale {
  display: flex; justify-content: space-between;
  margin-left: 150rpx; margin-top: 8rpx;
  font-size: 24rpx; color: #9CA3AF;
}

.btn-submit { margin-top: 16rpx; }

.shake { animation: shake-x 0.4s; }
@keyframes shake-x {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-12rpx); }
  75% { transform: translateX(12rpx); }
}
</style>
