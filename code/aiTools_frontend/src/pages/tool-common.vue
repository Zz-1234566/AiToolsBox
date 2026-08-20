<template>
  <view class="page-container">
    <page-header :title="toolInfo.name" showBack></page-header>
    
    <scroll-view scroll-y class="page-content">
      <!-- 工具描述 -->
      <view class="tool-desc">
        <text>{{ toolInfo.desc }}</text>
      </view>
      
      <!-- 输入方式切换栏（多输入方式时显示） -->
      <input-switcher :types="toolInfo.inputTypes || []" :current="currentInputType" @change="switchInputType" />

      <!-- 文字输入 -->
      <text-input-area v-if="currentInputType === 'text'" v-model="inputText" :placeholder="toolInfo.placeholder" />

      <!-- 文件上传 -->
      <file-input-area v-if="currentInputType === 'file'" :title="toolInfo.uploadTitle || '上传文档'" :desc="toolInfo.uploadDesc || '支持 PDF、Word、TXT 格式'" fileType="document" :fileName="fileName" :uploading="uploading" @choose="onFileChoose" />

      <!-- 图片上传 -->
      <file-input-area v-if="currentInputType === 'image'" :title="toolInfo.uploadTitle || '上传图片'" :desc="toolInfo.uploadDesc || '支持 JPG、PNG 格式'" fileType="image" :fileName="fileName" :uploading="uploading" @choose="onFileChoose" />

      <!-- 音频输入（占位） -->
      <audio-input-area v-if="currentInputType === 'audio'" />

      <!-- 提示词区域（可选）：格式提示词 + 生成内容提示词 -->
      <prompt-input-area v-model:formatText="promptFormatText" v-model:generateText="promptGenerateText" @pickFormat="openPromptPicker('format')" @pickGenerate="openPromptPicker('generate')" />
      
      <!-- 操作按钮 -->
      <view class="action-section">
        <button class="primary-btn press-scale" @click="handleGenerate" :disabled="loading">
          <view v-if="loading" class="loading-dots">
            <view class="dot"></view>
            <view class="dot"></view>
            <view class="dot"></view>
          </view>
          <text v-else>{{ toolInfo.actionText }}</text>
        </button>
      </view>
      
      <!-- 结果区域 -->
      <view class="result-section animate-fade-in-up">
        <result-area
          :title="toolInfo.resultTitle"
          :content="resultContent"
          :placeholder="toolInfo.resultPlaceholder"
          @regenerate="handleGenerate"
        ></result-area>
      </view>
      
      <view class="safe-area-bottom"></view>
    </scroll-view>
    
    <!-- 提示词选择弹窗（用户自定义 + 系统） -->
    <view v-if="showPromptPicker" class="prompt-mask" @click="showPromptPicker = false">
      <view class="prompt-picker" @click.stop>
        <text class="prompt-picker-title">选择{{ promptPickerTarget === 'format' ? '格式' : '生成内容' }}提示词</text>
        <scroll-view scroll-y class="prompt-picker-list">
          <!-- 系统提示词组 -->
          <view v-if="systemPromptList.length" class="prompt-group-title">系统提示词</view>
          <view 
            v-for="item in systemPromptList" 
            :key="'sys-' + item.id"
            class="prompt-picker-item press-scale"
            @click="selectPrompt(item)"
          >
            <text class="prompt-picker-text">{{ item.promptName || '默认' }}</text>
          </view>
          <!-- 用户提示词组 -->
          <view v-if="userPromptList.length" class="prompt-group-title">我的提示词</view>
          <view 
            v-for="item in userPromptList" 
            :key="'user-' + item.id"
            class="prompt-picker-item press-scale"
            @click="selectPrompt(item)"
          >
            <text class="prompt-picker-text">{{ item.promptText }}</text>
          </view>
          <!-- 空态 -->
          <view v-if="systemPromptList.length === 0 && userPromptList.length === 0" class="prompt-empty">
            <text>暂无该类型提示词</text>
          </view>
        </scroll-view>
        <view class="prompt-picker-close" @click="showPromptPicker = false">
          <text>关闭</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { requireLogin } from '@/utils/auth'
import PageHeader from '@/components/PageHeader.vue'
import InputSwitcher from '@/components/InputSwitcher.vue'
import TextInputArea from '@/components/TextInputArea.vue'
import FileInputArea from '@/components/FileInputArea.vue'
import AudioInputArea from '@/components/AudioInputArea.vue'
import PromptInputArea from '@/components/PromptInputArea.vue'
import ResultArea from '@/components/ResultArea.vue'
import { uploadFileApi } from '@/api/ai.js'
import { streamRequest, streamUpload } from '../api/stream'
import { formatAiResult } from '@/utils/format'
import { promptListApi, systemPromptListApi } from '@/api/prompt'
import { getTool } from '@/config/tools'

const toolId = ref('')
const currentInputType = ref('text')
const inputText = ref('')
const fileName = ref('')
const filePath = ref('')
const fileObj = ref(null)          // 原生 File/Blob 对象（H5 端用于 multipart 上传且保留真实文件名）
const uploading = ref(false)
const fileUrl = ref('')
const loading = ref(false)
const resultContent = ref('')
const promptFormatText = ref('')    // 用户自定义格式提示词
const promptGenerateText = ref('')  // 用户自定义生成内容提示词
const promptPickerTarget = ref('generate') // 选择弹窗当前填充的目标输入框
const selectedPromptId = ref('')      // 最近选中的提示词 id（可随 document-summary 一并提交）
const userPromptList = ref([])    // 用户自定义提示词（按用途过滤）
const systemPromptList = ref([])  // 系统提示词（按用途过滤）
const showPromptPicker = ref(false) // 是否显示选择弹窗

const toolInfo = computed(() => getTool(toolId.value))

onLoad((option) => {
  if (!requireLogin()) return
  toolId.value = option.id || ''
  // 初始化默认输入方式
  currentInputType.value = toolInfo.value.defaultInput
    || (toolInfo.value.inputTypes && toolInfo.value.inputTypes[0])
    || 'text'
})

// 切换输入方式时重置状态
const switchInputType = (type) => {
  currentInputType.value = type
  // 切换时清空之前的输入
  inputText.value = ''
  filePath.value = ''
  fileName.value = ''
  fileObj.value = null
  fileUrl.value = ''
}

const onFileChoose = async (info) => {
  // UploadArea 组件返回 { filePath, file }，兼容旧版字符串
  const localPath = (typeof info === 'string') ? info : (info && info.filePath)
  if (!localPath) return
  // 原生 File/Blob 对象（H5 端 tempFiles[0]，保留真实文件名，供 multipart+SSE 上传）
  fileObj.value = (info && info.file) || null
  // 优先用 File 的真实文件名，回退到路径文件名
  const rawName = (fileObj.value && fileObj.value.name) || localPath.split('/').pop()
  filePath.value = localPath
  fileName.value = rawName || '已选择文件'
  // 上传到后端通用用户文件区（prefix=file）
  uploading.value = true
  try {
    const res = await uploadFileApi(localPath, 'file')
    const url = (res && res.data && res.data.fileUrl) || ''
    fileUrl.value = url
    uni.showToast({ title: url ? '上传成功' : '上传失败', icon: 'none' })
  } catch (e) {
    fileUrl.value = ''
    uni.showToast({ title: '上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

// 打开选择弹窗：同时拉取该用途下的用户自定义 + 系统提示词，target 指定填充哪个输入框（format/generate）
const openPromptPicker = async (target = 'generate') => {
  promptPickerTarget.value = target
  try {
    // 用户自定义提示词（过滤用途，按当前工具 toolCode 隔离）
    const userRes = await promptListApi(toolId.value)
    userPromptList.value = (userRes.data || []).filter(p => p.promptUse === target)
    // 系统提示词（过滤用途）
    const sysRes = await systemPromptListApi(toolId.value)
    systemPromptList.value = (sysRes.data || []).filter(p => p.promptUse === target)
  } catch (e) {
    userPromptList.value = []
    systemPromptList.value = []
  }
  showPromptPicker.value = true
}

// 选中一条提示词填入对应输入框（用户/系统提示词均带 promptText）
const selectPrompt = (item) => {
  selectedPromptId.value = (item && item.id != null) ? item.id : ''
  if (promptPickerTarget.value === 'format') {
    promptFormatText.value = item.promptText
  } else {
    promptGenerateText.value = item.promptText
  }
  showPromptPicker.value = false
}

// 通用 SSE 文本流式输出（打字机效果）：work-summary / weekly-report / meeting-minutes 共用
// 入参 url 为后端流式接口地址；返回拼接后的完整文本
const runTextStream = (url) => {
  return new Promise((resolve, reject) => {
    let fullText = ''
    resultContent.value = ''
    const charQueue = []
    let streamDone = false
    let typeTimer = null

    const flushChar = () => {
      if (charQueue.length > 0) {
        resultContent.value += charQueue.shift()
      }
      // 流结束且队列排空后收尾
      if (streamDone && charQueue.length === 0) {
        if (typeTimer) {
          clearInterval(typeTimer)
          typeTimer = null
        }
        resolve(fullText)
      }
    }

    streamRequest({
      url,
      data: {
        content: inputText.value,
        promptFormat: promptFormatText.value,
        promptGenerate: promptGenerateText.value
      },
      onChunk: (chunk) => {
        fullText += chunk
        for (const ch of chunk) {
          charQueue.push(ch)
        }
        if (!typeTimer) {
          typeTimer = setInterval(flushChar, 20)
        }
      },
      onDone: () => {
        streamDone = true
        // 队列已空则立即结束，否则等 flushChar 排空后 resolve
        if (charQueue.length === 0) {
          if (typeTimer) {
            clearInterval(typeTimer)
            typeTimer = null
          }
          resolve(fullText)
        }
      },
      onError: (err) => {
        if (typeTimer) {
          clearInterval(typeTimer)
          typeTimer = null
        }
        reject(err)
      }
    })
  })
}

const handleGenerate = async () => {
  if (currentInputType.value === 'text' && !inputText.value.trim()) {
    uni.showToast({ title: '请输入内容', icon: 'none' })
    return
  }
  if ((currentInputType.value === 'file' || currentInputType.value === 'image') && !filePath.value) {
    uni.showToast({ title: '请先上传文件', icon: 'none' })
    return
  }
  if (currentInputType.value === 'audio') {
    uni.showToast({ title: '音频输入功能开发中', icon: 'none' })
    return
  }
  
  loading.value = true
  resultContent.value = ''
  
  try {
    const id = toolId.value
    
    if (id === 'doc-keypoint-extract') {
      // 文档重点提取：multipart 上传文档 + SSE 流式输出（打字机效果）
      // 仅文件输入方式走 multipart+SSE；文字/音频暂未接入对应后端
      if (currentInputType.value !== 'file') {
        uni.showToast({ title: '该输入方式暂未接入，请使用文件上传', icon: 'none' })
        return
      }
      if (!uni.getStorageSync('token')) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        setTimeout(() => {
          uni.navigateTo({ url: '/pages/login' })
        }, 600)
        return
      }

      // 格式和生成内容提示词至少填一个（后端会凑齐，另一个用系统默认）
      if (!promptFormatText.value.trim() && !promptGenerateText.value.trim()) {
        uni.showToast({ title: '请填写格式或生成内容提示词', icon: 'none' })
        return
      }

      // 字符队列打字机：onChunk 收到的文本入队，setInterval 每 20ms 输出一个字符
      let fullText = ''
      resultContent.value = ''
      const charQueue = []
      let streamDone = false
      let typeTimer = null

      await new Promise((resolve, reject) => {
        const flushChar = () => {
          if (charQueue.length > 0) {
            resultContent.value += charQueue.shift()
          }
          // 流结束且队列排空后收尾
          if (streamDone && charQueue.length === 0) {
            if (typeTimer) {
              clearInterval(typeTimer)
              typeTimer = null
            }
            resolve()
          }
        }

        streamUpload({
          url: '/api/ai-office/document-summary/stream',
          file: fileObj.value || filePath.value,
          fields: {
            promptFormat: promptFormatText.value,
            promptGenerate: promptGenerateText.value,
            promptId: selectedPromptId.value
          },
          onChunk: (chunk) => {
            fullText += chunk
            for (const ch of chunk) {
              charQueue.push(ch)
            }
            if (!typeTimer) {
              typeTimer = setInterval(flushChar, 20)
            }
          },
          onDone: () => {
            streamDone = true
            // 队列已空则立即结束，否则等 flushChar 排空后 resolve
            if (charQueue.length === 0) {
              if (typeTimer) {
                clearInterval(typeTimer)
                typeTimer = null
              }
              resolve()
            }
          },
          onError: (err) => {
            if (typeTimer) {
              clearInterval(typeTimer)
              typeTimer = null
            }
            reject(err)
          }
        })
      })
      // 流式完成后格式化（兜底分段，即使 AI 没换行也能分行展示）
      resultContent.value = formatAiResult(fullText)

    } else if (id === 'weekly-report') {
      // 周报生成：SSE 流式输出（打字机效果）；仅文字输入方式走 SSE
      if (currentInputType.value !== 'text') {
        uni.showToast({ title: '该输入方式暂未接入，请使用文字输入', icon: 'none' })
        return
      }
      if (!uni.getStorageSync('token')) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        setTimeout(() => {
          uni.navigateTo({ url: '/pages/login' })
        }, 600)
        return
      }
      if (!promptFormatText.value.trim() && !promptGenerateText.value.trim()) {
        uni.showToast({ title: '请填写格式或生成内容提示词', icon: 'none' })
        return
      }
      const fullText = await runTextStream('/api/ai-office/weekly-report/stream')
      resultContent.value = formatAiResult(fullText)

    } else if (id === 'meeting-minutes') {
      // 会议纪要：SSE 流式输出（打字机效果）；仅文字输入方式走 SSE
      if (currentInputType.value !== 'text') {
        uni.showToast({ title: '该输入方式暂未接入，请使用文字输入', icon: 'none' })
        return
      }
      if (!uni.getStorageSync('token')) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        setTimeout(() => {
          uni.navigateTo({ url: '/pages/login' })
        }, 600)
        return
      }
      if (!promptFormatText.value.trim() && !promptGenerateText.value.trim()) {
        uni.showToast({ title: '请填写格式或生成内容提示词', icon: 'none' })
        return
      }
      const fullText = await runTextStream('/api/ai-office/meeting-minutes/stream')
      resultContent.value = formatAiResult(fullText)

    } else if (id === 'ocr-recognize') {
      // OCR 智能识别：上传图片 → 腾讯云 OCR → 调 AI 整理（SSE 流式）
      if (currentInputType.value !== 'file' && currentInputType.value !== 'image') {
        uni.showToast({ title: '该工具请上传图片', icon: 'none' })
        return
      }
      if (!uni.getStorageSync('token')) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        setTimeout(() => {
          uni.navigateTo({ url: '/pages/login' })
        }, 600)
        return
      }
      if (!filePath.value) {
        uni.showToast({ title: '请先上传图片', icon: 'none' })
        return
      }
      if (!promptFormatText.value.trim() && !promptGenerateText.value.trim()) {
        uni.showToast({ title: '请填写格式或生成内容提示词', icon: 'none' })
        return
      }

      // 流式：multipart 上传 + SSE 增量读
      let fullText = ''
      resultContent.value = ''
      const charQueue = []
      let streamDone = false
      let typeTimer = null

      await new Promise((resolve, reject) => {
        const flushChar = () => {
          if (charQueue.length > 0) {
            resultContent.value += charQueue.shift()
          }
          if (streamDone && charQueue.length === 0) {
            if (typeTimer) { clearInterval(typeTimer); typeTimer = null }
            resolve()
          }
        }
        streamUpload({
          url: '/api/ai-office/ocr-recognize/stream',
          file: filePath.value,
          fields: {
            promptFormat: promptFormatText.value,
            promptGenerate: promptGenerateText.value
          },
          onChunk: (chunk) => {
            fullText += chunk
            for (const ch of chunk) charQueue.push(ch)
            if (!typeTimer) typeTimer = setInterval(flushChar, 20)
          },
          onDone: () => {
            streamDone = true
            if (charQueue.length === 0) {
              if (typeTimer) { clearInterval(typeTimer); typeTimer = null }
              resolve()
            }
          },
          onError: (err) => {
            if (typeTimer) { clearInterval(typeTimer); typeTimer = null }
            // 弹窗显示后端真实错误信息（OCR 未启用 / 上传失败等）
            uni.showModal({ title: '请求失败', content: err && err.message ? err.message : '未知错误', showCancel: false })
            reject(err)
          }
        })
      })
      resultContent.value = formatAiResult(fullText)

    } else if (id === 'work-summary') {
      // 工作总结：SSE 流式输出（打字机效果）；仅文字输入方式走 SSE
      if (currentInputType.value !== 'text') {
        uni.showToast({ title: '该输入方式暂未接入，请使用文字输入', icon: 'none' })
        return
      }
      if (!uni.getStorageSync('token')) {
        uni.showToast({ title: '请先登录', icon: 'none' })
        setTimeout(() => {
          uni.navigateTo({ url: '/pages/login' })
        }, 600)
        return
      }
      // 格式和生成内容提示词至少填一个（后端会凑齐，另一个用系统默认）
      if (!promptFormatText.value.trim() && !promptGenerateText.value.trim()) {
        uni.showToast({ title: '请填写格式或生成内容提示词', icon: 'none' })
        return
      }
      const fullText = await runTextStream('/api/ai-office/work-summary/stream')
      // 流式完成后格式化（兜底分段，即使 AI 没换行也能分行展示）
      resultContent.value = formatAiResult(fullText)
    } else if (id === 'id-photo-bg-change' || id === 'portrait-bg-replace') {
      // 去背景：后端暂未实现
      uni.showToast({ title: '该工具开发中', icon: 'none' })
      return
      
    } else {
      // 暂未接入后端的工具，使用模拟数据
      resultContent.value = `【${toolInfo.value.name}】\n\n这是模拟生成的结果。\n\n后续接入后端接口后会返回真实结果。`
    }
    
  } catch (err) {
    console.error('Generate error:', err)
    resultContent.value = (err && err.message) || '处理失败，请稍后重试。'
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

.tool-desc {
  padding: $spacing-md 0;
  
  text {
    font-size: $font-size-md;
    color: $text-secondary;
    line-height: 1.6;
  }
}
.prompt-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: rgba(0,0,0,0.5);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  
  .prompt-picker {
    width: 80%;
    max-height: 70vh;
    background-color: $bg-white;
    border-radius: $radius-lg;
    padding: $spacing-lg;
    display: flex;
    flex-direction: column;
    
    .prompt-picker-title {
      font-size: $font-size-lg;
      font-weight: 600;
      color: $text-primary;
      text-align: center;
      margin-bottom: $spacing-md;
    }
    
    .prompt-picker-list {
      max-height: 50vh;
    }
    
    .prompt-group-title {
      font-size: $font-size-sm;
      color: $text-tertiary;
      padding: $spacing-sm $spacing-md;
    }
    
    .prompt-empty {
      text-align: center;
      padding: $spacing-xl 0;
      color: $text-tertiary;
      font-size: $font-size-sm;
    }
    
    .prompt-picker-item {
      padding: $spacing-md;
      border-bottom: 1rpx solid $divider-color;
      
      .prompt-picker-text {
        font-size: $font-size-sm;
        color: $text-primary;
        white-space: pre-wrap;
      }
    }
    
    .prompt-picker-close {
      margin-top: $spacing-md;
      text-align: center;
      color: $text-secondary;
      font-size: $font-size-sm;
    }
  }
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
