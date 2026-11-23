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
      <svg class="af-icon-svg" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="2" fill="none" stroke="#9A9AA4" stroke-width="1.8"/><path d="M8 10V7a4 4 0 118 0v3" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg>
      <input class="af-input" :type="show1 ? 'text' : 'password'" v-model="form.oldPassword"
             placeholder="请输入当前密码" placeholder-class="af-placeholder" />
      <view class="af-eye" @click="show1 = !show1"><template v-if="show1"><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6c2 0 3.7.6 5.2 1.5M22 12s-3.6 6-10 6c-2 0-3.7-.6-5.2-1.5" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/><path d="M4 4l16 16" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg></template><template v-else><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12z" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.6" fill="none" stroke="#9A9AA4" stroke-width="1.8"/></svg></template></view>
    </view>

    <!-- 新密码 -->
    <text class="cp-label">新密码</text>
    <view class="auth-field" :class="{ shake: shakeField === 'newPassword' }">
      <svg class="af-icon-svg" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="2" fill="none" stroke="#9A9AA4" stroke-width="1.8"/><path d="M8 10V7a4 4 0 118 0v3" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg>
      <input class="af-input" :type="show2 ? 'text' : 'password'" v-model="form.newPassword"
             placeholder="请输入新密码" placeholder-class="af-placeholder" />
      <view class="af-eye" @click="show2 = !show2"><template v-if="show2"><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6c2 0 3.7.6 5.2 1.5M22 12s-3.6 6-10 6c-2 0-3.7-.6-5.2-1.5" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/><path d="M4 4l16 16" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg></template><template v-else><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12z" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.6" fill="none" stroke="#9A9AA4" stroke-width="1.8"/></svg></template></view>
    </view>

    <!-- 确认新密码 -->
    <text class="cp-label">确认新密码</text>
    <view class="auth-field" :class="{ shake: shakeField === 'confirmPassword' }">
      <svg class="af-icon-svg" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="2" fill="none" stroke="#9A9AA4" stroke-width="1.8"/><path d="M8 10V7a4 4 0 118 0v3" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg>
      <input class="af-input" :type="show3 ? 'text' : 'password'" v-model="form.confirmPassword"
             placeholder="请再次输入新密码" placeholder-class="af-placeholder" />
      <view class="af-eye" @click="show3 = !show3"><template v-if="show3"><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6c2 0 3.7.6 5.2 1.5M22 12s-3.6 6-10 6c-2 0-3.7-.6-5.2-1.5" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/><path d="M4 4l16 16" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg></template><template v-else><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12z" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.6" fill="none" stroke="#9A9AA4" stroke-width="1.8"/></svg></template></view>
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
import { safeBack } from '@/utils/pageTransition'
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
      safeBack('/pages/my')
    }, 800)
  } catch (e) {
    // request.js 已经统一弹出错误提示（旧密码错误返回 1003）
  } finally {
    loading.value = false
  }
}

const goBack = () => safeBack('/pages/my')
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.cp-wrap {
  min-height: 100vh;
  background: var(--bg-card);
  /* App 端刘海屏：顶部避让状态栏（H5 端该变量为 0）。
     ⚠️ 用四值简写，避免后续 padding 简写把 padding-top 重置掉。 */
  padding: var(--status-bar-height, 0px) 40rpx 48rpx;
  box-sizing: border-box;
}

.auth-back { padding: 24rpx 0 8rpx; }
.auth-back__icon { font-size: 56rpx; color: var(--text-primary); line-height: 1; }

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
  margin-bottom: 40rpx;
}

.cp-label {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16rpx;
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
.af-icon { display: flex; flex-shrink: 0; }
.af-icon-svg { width: 36rpx; height: 36rpx; }
.af-input {
  flex: 1;
  font-size: 28rpx;
  color: var(--text-primary);
  background: transparent;
}
.af-placeholder { color: var(--text-tertiary); }
.af-eye { display: flex; flex-shrink: 0; }
.af-eye-svg { width: 36rpx; height: 36rpx; }

/* 密码强度条 */
.pwd-strength { margin: 8rpx 0 24rpx; }
.pwd-strength__row { display: flex; align-items: center; gap: 24rpx; }
.pwd-strength__label { font-size: 26rpx; color: var(--text-secondary); flex-shrink: 0; }
.pwd-strength__bars { flex: 1; display: flex; gap: 8rpx; }
.pwd-strength__bar {
  flex: 1; height: 12rpx; border-radius: 6rpx; background: var(--bg-gray);
}
.pwd-strength__bar--low { background: var(--color-danger); }
.pwd-strength__bar--mid { background: #F59E0B; }
.pwd-strength__bar--high { background: var(--color-success); }
.pwd-strength__scale {
  display: flex; justify-content: space-between;
  margin-left: 150rpx; margin-top: 8rpx;
  font-size: 24rpx; color: var(--text-tertiary);
}

.btn-submit { margin-top: 16rpx; }

.shake { animation: shake-x 0.4s; }
@keyframes shake-x {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-12rpx); }
  75% { transform: translateX(12rpx); }
}
</style>
