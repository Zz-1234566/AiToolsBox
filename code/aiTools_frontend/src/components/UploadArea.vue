<template>
  <view class="upload-area" @click="chooseFile">
    <view class="upload-inner">
      <svg class="upload-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M17 8L12 3L7 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M12 3V15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <text class="upload-title">{{ title }}</text>
      <text class="upload-desc">{{ desc }}</text>
    </view>
    <view v-if="fileName" class="file-tag">
      <text class="file-name">{{ fileName }}</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  title: {
    type: String,
    default: '上传文件'
  },
  desc: {
    type: String,
    default: '支持 PDF、Word、图片格式'
  },
  accept: {
    type: String,
    default: 'all'
  },
  // 选择类型：'image' 走 uni.chooseImage（图片），'document' 走 uni.chooseFile（文档）
  fileType: {
    type: String,
    default: 'image'
  },
  fileName: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['choose'])

// 文档模式默认允许的扩展名（uni.chooseFile 的 extension 参数，H5 端会映射为 input 的 accept 属性）
const DOCUMENT_EXTENSIONS = ['.txt', '.pdf', '.docx']

const chooseFile = () => {
  if (props.fileType === 'document') {
    // uni.chooseFile 仅 H5（及 HarmonyOS）支持，App/微信小程序端不存在该 API
    if (typeof uni.chooseFile !== 'function') {
      uni.showToast({ title: '当前平台暂不支持选择文档', icon: 'none' })
      return
    }
    // accept prop 可覆盖默认扩展名（如 ".txt,.md"），默认限制 txt/pdf/docx
    const extensions = (props.accept && props.accept !== 'all')
      ? props.accept.split(',').map((s) => s.trim())
      : DOCUMENT_EXTENSIONS
    uni.chooseFile({
      count: 1,
      extension: extensions,
      success: (res) => {
        // 同时返回临时路径字符串和原生 File 对象（H5 端 tempFiles[0] 是 File）
        emit('choose', {
          filePath: res.tempFilePaths[0],
          file: (res.tempFiles && res.tempFiles[0]) || null
        })
      },
      fail: () => {
        uni.showToast({ title: '选择文件失败', icon: 'none' })
      }
    })
    return
  }
  // image：原逻辑
  uni.chooseImage({
    count: 1,
    success: (res) => {
      // 同时返回临时路径字符串和原生 File 对象（H5 端 tempFiles[0] 是 File）
      emit('choose', {
        filePath: res.tempFilePaths[0],
        file: (res.tempFiles && res.tempFiles[0]) || null
      })
    },
    fail: () => {
      uni.showToast({ title: '选择文件失败', icon: 'none' })
    }
  })
}
</script>

<style lang="scss" scoped>
.upload-area {
  background-color: $bg-white;
  border-radius: $radius-lg;
  border: 2rpx dashed $border-color;
  padding: $spacing-xl;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  
  &:active {
    background-color: $bg-gray;
  }
}

.upload-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  
  .upload-icon {
    width: 64rpx;
    height: 64rpx;
    color: $text-tertiary;
    margin-bottom: $spacing-sm;
  }
  
  .upload-title {
    font-size: $font-size-md;
    font-weight: 500;
    color: $text-primary;
    margin-bottom: 8rpx;
  }
  
  .upload-desc {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}

.file-tag {
  margin-top: $spacing-md;
  background-color: $bg-gray;
  border-radius: $radius-pill;
  padding: $spacing-xs $spacing-md;
  
  .file-name {
    font-size: $font-size-sm;
    color: $text-secondary;
  }
}
</style>
