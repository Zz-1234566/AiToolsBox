<template>
  <view class="input-section">
    <view class="file-input-wrapper">
      <upload-area
        :title="title"
        :desc="desc"
        :fileType="fileType"
        :fileName="fileName"
        @choose="$emit('choose', $event)"
      ></upload-area>
    </view>
  </view>
  <view v-if="uploading" class="uploading-tip-outside">
    <text>上传中...</text>
  </view>
</template>

<script setup>
import UploadArea from '@/components/UploadArea.vue'

defineProps({
  title: { type: String, default: '上传文件' },
  desc: { type: String, default: '支持 PDF、Word、TXT 格式' },
  fileType: { type: String, default: 'document' },
  fileName: { type: String, default: '' },
  uploading: { type: Boolean, default: false }
})
defineEmits(['choose'])
</script>

<style lang="scss" scoped>
.input-section {
  height: 380rpx;
  margin-bottom: $spacing-md;
  display: flex;
  flex-direction: column;
}

// 统一高度容器：让文件/图片上传区与文字输入区视觉对齐
.file-input-wrapper {
  flex: 1;
  display: flex;

  :deep(.upload-area) {
    flex: 1;
    height: 100%;
  }
}

.uploading-tip-outside {
  margin-top: $spacing-sm;
  display: flex;
  align-items: center;
  justify-content: center;

  text {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}
</style>
