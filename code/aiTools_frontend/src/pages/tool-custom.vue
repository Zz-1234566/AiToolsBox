<template>
  <view class="page-container">
    <page-header title="自定义工具" showBack></page-header>
    
    <scroll-view scroll-y class="page-content">
      <!-- 工具配置 -->
      <view class="config-card">
        <view class="form-item">
          <text class="form-label">工具名称</text>
          <input 
            class="form-input" 
            v-model="toolName"
            placeholder="给你的工具起个名字"
            placeholder-class="input-placeholder"
          />
        </view>
        
        <view class="form-item">
          <text class="form-label">AI 提示词</text>
          <textarea 
            class="form-textarea" 
            v-model="promptText"
            placeholder="请输入你希望 AI 执行的任务，例如：请帮我总结这段文字的重点。"
            placeholder-class="textarea-placeholder"
          />
        </view>
        
        <view class="form-item no-border">
          <text class="form-label">输入方式</text>
          <view class="input-type-group">
            <view 
              v-for="type in inputTypes" 
              :key="type.value"
              class="type-option"
              :class="{ active: inputType === type.value }"
              @click="inputType = type.value"
            >
              <text>{{ type.label }}</text>
            </view>
          </view>
        </view>
      </view>
      
      <!-- 输入区域 -->
      <view class="input-section">
        <view v-if="inputType === 'text'" class="input-card">
          <textarea 
            class="tool-textarea" 
            v-model="inputText"
            placeholder="请输入需要处理的内容..."
            placeholder-class="textarea-placeholder"
          />
        </view>
        
        <upload-area
          v-else
          title="上传文件"
          desc="支持 PDF、Word、图片格式"
          :fileType="'document'"
          :fileName="fileName"
          @choose="onFileChoose"
        ></upload-area>
      </view>
      
      <!-- 操作按钮 -->
      <view class="action-section">
        <button class="primary-btn" @click="handleGenerate" :disabled="loading">
          <text v-if="loading">运行中...</text>
          <text v-else>运行自定义工具</text>
        </button>
      </view>
      
      <!-- 结果区域 -->
      <view class="result-section">
        <result-area
          title="运行结果"
          :content="resultContent"
          placeholder="自定义工具的运行结果将在这里显示..."
          @regenerate="handleGenerate"
        ></result-area>
      </view>
      
      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { requireLogin } from '@/utils/auth'
import PageHeader from '@/components/PageHeader.vue'
import UploadArea from '@/components/UploadArea.vue'
import ResultArea from '@/components/ResultArea.vue'
import { request } from '@/api/request'

const toolName = ref('')
const promptText = ref('')
const inputType = ref('text')
const inputText = ref('')
const fileName = ref('')
const loading = ref(false)
const resultContent = ref('')
const resultItems = ref([])

const inputTypes = [
  { label: '文字输入', value: 'text' },
  { label: '文件上传', value: 'file' }
]

const onFileChoose = (info) => {
  const filePath = typeof info === 'string' ? info : (info && info.filePath)
  fileName.value = filePath ? (filePath.split('/').pop() || '已选择文件') : ''
}

const handleGenerate = async () => {
  if (!toolName.value.trim()) {
    uni.showToast({ title: '请输入工具名称', icon: 'none' })
    return
  }
  if (!promptText.value.trim()) {
    uni.showToast({ title: '请输入 AI 提示词', icon: 'none' })
    return
  }
  if (inputType.value === 'text' && !inputText.value.trim()) {
    uni.showToast({ title: '请输入需要处理的内容', icon: 'none' })
    return
  }
  if (inputType.value === 'file' && !fileName.value) {
    uni.showToast({ title: '请先上传文件', icon: 'none' })
    return
  }
  
  loading.value = true
  resultContent.value = ''
  resultItems.value = []

  // 文本输入包装成 Blob（后端 batchUpload 只接 multipart files）
  const formData = new FormData()
  if (inputType.value === 'text') {
    const blob = new Blob([inputText.value], { type: 'text/plain' })
    formData.append('files', blob, 'input.txt')
  } else {
    formData.append('files', inputText.value, fileName.value || 'upload')
  }
  // 工具名 + 提示词 拼成后端 prompt
  const fullPrompt = `[工具名称] ${toolName.value}
[任务] ${promptText.value}`
  formData.append('prompt', fullPrompt)

  try {
    const res = await request({
      url: '/api/ai-office/ai-file-reader/batch-upload',
      method: 'POST',
      data: formData,
      header: { 'Content-Type': 'multipart/form-data' }
    })
    if (res && res.code === 200 && res.data) {
      pollBatchTask(res.data.batchId, res.data.fileCount || 1)
    } else {
      uni.showToast({ title: res?.message || '提交失败', icon: 'none' })
      loading.value = false
    }
  } catch (e) {
    uni.showToast({ title: '请求失败：' + (e?.message || '未知错误'), icon: 'none' })
    loading.value = false
  }
}

// 轮询批量任务
const pollBatchTask = async (batchId, fileCount) => {
  try {
    const res = await request({
      url: `/api/ai-office/batch/${batchId}/completed?since=0`,
      method: 'GET'
    })
    if (res && res.code === 200 && res.data) {
      const items = res.data.results || []
      resultItems.value = items
      const lines = []
      for (const item of items) {
        if (item.output) lines.push(item.output)
      }
      resultContent.value = lines.join('\n\n')
      const status = res.data.status
      if (status === 2 || status === 3 || status === 4) {
        loading.value = false
      } else {
        setTimeout(() => pollBatchTask(batchId, fileCount), 2000)
      }
    } else {
      setTimeout(() => pollBatchTask(batchId, fileCount), 2000)
    }
  } catch (e) {
    setTimeout(() => pollBatchTask(batchId, fileCount), 3000)
  }
}

onLoad(() => {
  if (!requireLogin()) return
})
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

.config-card {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: 0 $spacing-md;
  margin-top: $spacing-md;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-card;
}

.form-item {
  padding: $spacing-md 0;
  border-bottom: 1rpx solid $divider-color;
  
  &.no-border {
    border-bottom: none;
  }
  
  .form-label {
    display: block;
    font-size: $font-size-md;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-sm;
  }
  
  .form-input {
    height: 72rpx;
    background-color: $bg-gray;
    border-radius: $radius-md;
    padding: 0 $spacing-md;
    font-size: $font-size-md;
    color: $text-primary;
  }
  
  .form-textarea {
    width: 100%;
    min-height: 240rpx;
    background-color: $bg-gray;
    border-radius: $radius-md;
    padding: $spacing-md;
    font-size: $font-size-md;
    color: $text-primary;
    line-height: 1.7;
  }
  
  .input-type-group {
    display: flex;
    gap: $spacing-md;
    
    .type-option {
      flex: 1;
      height: 72rpx;
      border-radius: $radius-pill;
      background-color: $bg-gray;
      display: flex;
      align-items: center;
      justify-content: center;
      
      text {
        font-size: $font-size-sm;
        color: $text-secondary;
      }
      
      &.active {
        background-color: $text-primary;
        
        text {
          color: $bg-white;
        }
      }
    }
  }
}

.input-placeholder,
.textarea-placeholder {
  color: $text-tertiary;
  font-size: $font-size-md;
}

.input-section {
  margin-bottom: $spacing-md;
}

.input-card {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  box-shadow: $shadow-card;
}

.tool-textarea {
  width: 100%;
  min-height: 300rpx;
  font-size: $font-size-md;
  color: $text-primary;
  line-height: 1.7;
}

.action-section {
  margin-bottom: $spacing-md;
  
  .primary-btn {
    width: 100%;
    height: 96rpx;
    border-radius: $radius-pill;
    background-color: $text-primary;
    color: $bg-white;
    font-size: $font-size-lg;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    
    &:active {
      opacity: 0.85;
    }
    
    &[disabled] {
      opacity: 0.6;
    }
  }
}

.result-section {
  margin-bottom: $spacing-md;
}
</style>
