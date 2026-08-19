<template>
  <view class="page-container">
    <page-header :title="'个人资料'" showBack></page-header>

    <scroll-view scroll-y class="page-content">
      <view class="profile-card animate-fade-in-up">
        <!-- 头像 -->
        <view class="avatar-section" @click="chooseAvatar">
          <view class="avatar">
            <image v-if="avatar" class="avatar-img" :src="avatar" mode="aspectFill"></image>
            <svg v-else class="avatar-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.5"/>
              <path d="M4 20C4 15.5817 7.58172 12 12 12C16.4183 12 20 15.5817 20 20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </view>
          <text class="avatar-tip">{{ uploading ? '上传中...' : '点击更换头像' }}</text>
        </view>

        <!-- 账号（只读） -->
        <view class="info-row">
          <text class="info-label">账号</text>
          <text class="info-value">{{ account }}</text>
        </view>

        <!-- 用户名 -->
        <view class="input-group">
          <text class="input-label">用户名</text>
          <input
            class="input-field"
            type="text"
            v-model="username"
            placeholder="请输入用户名"
            placeholder-class="placeholder"
          />
        </view>

        <button class="btn-primary press-scale" @click="handleSave" :disabled="loading || uploading">
          {{ loading ? '保存中...' : '保 存' }}
        </button>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '@/components/PageHeader.vue'
import { updateProfileApi } from '@/api/user'
import { uploadFileApi } from '@/api/ai'
import { ERROR_CODE } from '@/api/errorCode'
import { requireLogin } from '@/utils/auth'

const account = ref('')
const username = ref('')
const avatar = ref('')
const loading = ref(false)
const uploading = ref(false)

// 从本地缓存读取当前用户信息
onShow(() => {
  if (!requireLogin()) return
  const userInfo = uni.getStorageSync('userInfo')
  if (userInfo) {
    account.value = userInfo.account || ''
    username.value = userInfo.username || ''
    avatar.value = userInfo.avatar || ''
  }
})

// 选择并上传头像（复用通用文件上传接口）
const chooseAvatar = () => {
  if (uploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const filePath = res.tempFilePaths[0]
      uploading.value = true
      uni.showLoading({ title: '上传中...' })
      try {
        const uploadRes = await uploadFileApi(filePath)
        if (uploadRes.code === ERROR_CODE.SUCCESS && uploadRes.data) {
          avatar.value = uploadRes.data.fileUrl
          uni.showToast({ title: '头像上传成功', icon: 'success' })
        } else {
          uni.showToast({ title: uploadRes.message || '头像上传失败', icon: 'none' })
        }
      } catch (e) {
        // request.js 已经统一弹出错误提示
      } finally {
        uploading.value = false
        uni.hideLoading()
      }
    }
  })
}

const handleSave = async () => {
  if (!username.value.trim()) {
    uni.showToast({ title: '请输入用户名', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = await updateProfileApi({
      username: username.value.trim(),
      avatar: avatar.value
    })
    // 同步更新本地缓存的用户信息
    uni.setStorageSync('userInfo', res.data)
    uni.$emit('loginStatusChanged')
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 800)
  } catch (e) {
    // request.js 已经统一弹出错误提示
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

.page-content {
  flex: 1;
  padding: 0 $spacing-md;
}

.profile-card {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-xl $spacing-lg;
  margin-top: $spacing-md;
  box-shadow: $shadow-card;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: $spacing-xl;

  .avatar {
    width: 160rpx;
    height: 160rpx;
    border-radius: $radius-pill;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: $spacing-sm;
    overflow: hidden;

    .avatar-icon {
      width: 88rpx;
      height: 88rpx;
      color: $text-secondary;
    }

    .avatar-img {
      width: 100%;
      height: 100%;
    }
  }

  .avatar-tip {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $divider-color;
  margin-bottom: $spacing-md;

  .info-label {
    font-size: $font-size-sm;
    color: $text-secondary;
  }

  .info-value {
    font-size: $font-size-md;
    color: $text-primary;
    font-weight: 600;
  }
}

.input-group {
  margin-bottom: $spacing-lg;

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
