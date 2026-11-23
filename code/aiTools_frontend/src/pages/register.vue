<template>
  <view class="auth-wrap">
    <!-- 返回 -->
    <view class="auth-back" @click="goBack">
      <text class="auth-back__icon">‹</text>
    </view>

    <image class="auth-logo-img" :class="{ 'auth-logo-img--dark': isDarkMode }" src="/static/ref/logo.png" mode="widthFix" />
    <text class="auth-brand">AI Tools Box</text>

    <text class="auth-title">创建账号</text>
    <text class="auth-sub">欢迎加入 AI Tools Box</text>

    <!-- 邮箱 -->
    <view class="auth-field" :class="{ shake: shakeField === 'email' }">
      <view class="af-icon"><svg viewBox="0 0 24 24" style="width:36rpx;height:36rpx;fill:var(--text-tertiary)"><path d="M20 4H4a2 2 0 00-2 2v12a2 2 0 002 2h16a2 2 0 002-2V6a2 2 0 00-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/></svg></view>
      <view class="af-body">
        <text class="af-label">邮箱</text>
        <input class="af-input" v-model="form.email" placeholder="请输入邮箱" placeholder-class="af-placeholder" />
      </view>
    </view>

    <!-- 验证码 -->
    <view class="auth-field" :class="{ shake: shakeField === 'code' }">
      <view class="af-icon"><svg viewBox="0 0 24 24" style="width:36rpx;height:36rpx;fill:var(--text-tertiary)"><path d="M12 2l8 4v6c0 5-3.4 9.3-8 10-4.6-.7-8-5-8-10V6l8-4z"/></svg></view>
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
      <view class="af-icon"><svg viewBox="0 0 24 24" style="width:36rpx;height:36rpx;fill:var(--text-tertiary)"><path d="M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-5 0-9 2.5-9 5.5V22h18v-2.5c0-3-4-5.5-9-5.5z"/></svg></view>
      <view class="af-body">
        <text class="af-label">用户名</text>
        <input class="af-input" v-model="form.username" placeholder="请输入用户名" placeholder-class="af-placeholder" />
      </view>
    </view>

    <!-- 设置密码 -->
    <view class="auth-field" :class="{ shake: shakeField === 'password' }">
      <view class="af-icon"><svg viewBox="0 0 24 24" style="width:36rpx;height:36rpx;fill:var(--text-tertiary)"><path d="M17 9V7a5 5 0 10-10 0v2H5v12h14V9h-2zM9 7a3 3 0 116 0v2H9V7z"/></svg></view>
      <view class="af-body">
        <text class="af-label">设置密码</text>
        <input class="af-input" :type="showPwd ? 'text' : 'password'" v-model="form.password" placeholder="请输入 6-16 位密码" placeholder-class="af-placeholder" />
      </view>
      <view class="af-eye" @click="showPwd = !showPwd"><template v-if="showPwd"><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6c2 0 3.7.6 5.2 1.5M22 12s-3.6 6-10 6c-2 0-3.7-.6-5.2-1.5" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/><path d="M4 4l16 16" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg></template><template v-else><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12z" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.6" fill="none" stroke="#9A9AA4" stroke-width="1.8"/></svg></template></view>
    </view>

    <!-- 确认密码 -->
    <view class="auth-field" :class="{ shake: shakeField === 'confirmPassword' }">
      <view class="af-icon"><svg viewBox="0 0 24 24" style="width:36rpx;height:36rpx;fill:var(--text-tertiary)"><path d="M17 9V7a5 5 0 10-10 0v2H5v12h14V9h-2zM9 7a3 3 0 116 0v2H9V7z"/></svg></view>
      <view class="af-body">
        <text class="af-label">确认密码</text>
        <input class="af-input" :type="showPwd2 ? 'text' : 'password'" v-model="form.confirmPassword" placeholder="请再次输入密码" placeholder-class="af-placeholder" />
      </view>
      <view class="af-eye" @click="showPwd2 = !showPwd2"><template v-if="showPwd2"><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6c2 0 3.7.6 5.2 1.5M22 12s-3.6 6-10 6c-2 0-3.7-.6-5.2-1.5" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/><path d="M4 4l16 16" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linecap="round"/></svg></template><template v-else><svg class="af-eye-svg" viewBox="0 0 24 24"><path d="M2 12s3.6-6 10-6 10 6 10 6-3.6 6-10 6S2 12 2 12z" fill="none" stroke="#9A9AA4" stroke-width="1.8" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.6" fill="none" stroke="#9A9AA4" stroke-width="1.8"/></svg></template></view>
    </view>

    <view class="btn-solid btn-register" :class="{ 'btn-solid--disabled': loading }" @click="loading ? null : handleRegister()">
      {{ loading ? '注册中...' : '注册' }}
    </view>

    <view class="auth-foot">
      <text>已有账号？</text>
      <text class="auth-foot__link" @click="goToLoginPage">立即登录</text>
    </view>

    <!-- 注册成功弹层 -->
    <view v-if="showSuccess" class="success-mask" @touchmove.stop.prevent="onNoop">
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
import { STORAGE_KEYS } from '@/utils/auth'
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { safeBack } from '@/utils/pageTransition'
import { request } from '../api/request'
import { isDarkMode } from '@/utils/theme'
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
/**
 * 空处理函数：只为了给遮罩加 `@touchmove.stop.prevent`。
 *
 * 遮罩层若不锁住 touchmove，用户在遮罩空白处上下拖动会**连带滚动底层页面**，
 * 弹层内容相对遮罩发生位移（真机廉价感最明显的表现之一）。
 * 范式同 pages/image-bg-remover.vue。
 */
const onNoop = () => {}

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
  uni.setStorageSync(STORAGE_KEYS.PENDING_ACCOUNT, registeredAccount.value)
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
  background: var(--bg-card);
  /* App 端刘海屏：顶部避让状态栏（H5 端该变量为 0）。
     ⚠️ 用四值简写，避免后续 padding 简写把 padding-top 重置掉。 */
  padding: var(--status-bar-height, 0px) 40rpx 48rpx;
  box-sizing: border-box;
}

.auth-back {
  padding: 24rpx 0 8rpx;
}
.auth-back__icon {
  font-size: 56rpx;
  color: var(--text-primary);
  line-height: 1;
}

.auth-logo-img {
  display: block;
  width: 210rpx;
  height: auto;
  margin: 8rpx auto 24rpx;
}
.auth-logo-img--dark {
  filter: brightness(0) invert(1);
}
.auth-brand {
  display: block;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 64rpx;
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
  margin-bottom: 40rpx;
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
.af-body { flex: 1; min-width: 0; }
.af-label {
  display: block;
  font-size: 22rpx;
  color: var(--text-tertiary);
  margin-bottom: 4rpx;
}
.af-input {
  width: 100%;
  font-size: 28rpx;
  color: var(--text-primary);
  background: transparent;
}
.af-placeholder { color: var(--text-tertiary); }
.af-eye { display: flex; flex-shrink: 0; }
.af-eye-svg { width: 36rpx; height: 36rpx; }
.af-code {
  font-size: 26rpx;
  color: var(--brand-primary);
  font-weight: 500;
  flex-shrink: 0;
}
.af-code--disabled { color: var(--text-tertiary); }

.btn-register { margin-top: 40rpx; }

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
  background: var(--bg-card);
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
  background: linear-gradient(135deg, #34D399, var(--color-success));
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
  color: var(--text-primary);
  margin-bottom: 24rpx;
}
.success-tip { font-size: 26rpx; color: var(--text-tertiary); }
.success-account {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--brand-primary);
  letter-spacing: 2rpx;
  margin: 12rpx 0 16rpx;
}
.success-note {
  font-size: 24rpx;
  color: var(--text-tertiary);
  margin-bottom: 40rpx;
  text-align: center;
}
</style>
