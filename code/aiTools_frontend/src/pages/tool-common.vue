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

      <!-- 文件上传：doc-keypoint-extract / ocr-recognize 走多文件批量（共享 BatchFilePicker 组件）；其它工具单文件 -->
      <file-input-area v-if="currentInputType === 'file' && !isBatchTool" :title="toolInfo.uploadTitle || '上传文档'" :desc="toolInfo.uploadDesc || '支持 PDF、Word、TXT 格式'" fileType="document" :fileName="fileName" :uploading="uploading" @choose="onFileChoose" />

      <!-- 多文件选择组件：doc-keypoint-extract / ocr-recognize 共用（fileRule 来自 tools.js） -->
      <BatchFilePicker
        v-if="isBatchTool && (currentInputType === 'file' || currentInputType === 'image')"
        ref="batchPickerRef"
        :rule="toolInfo.fileRule"
        :disabled="loading"
      />

      <!-- 图片上传（非多文件工具，单文件） -->
      <file-input-area v-if="currentInputType === 'image' && !isBatchTool" :title="toolInfo.uploadTitle || '上传图片'" :desc="toolInfo.uploadDesc || '支持 JPG、PNG 格式'" fileType="image" :fileName="fileName" :uploading="uploading" @choose="onFileChoose" />

      <!-- 音频输入（占位） -->
      <audio-input-area v-if="currentInputType === 'audio'" />

      <!-- 提示词区域（可选）：格式提示词 + 生成内容提示词 -->
      <prompt-input-area v-model:formatText="promptFormatText" :formatPromptDisplay="formatPromptDisplay" v-model:generateText="promptGenerateText" @pickFormat="openPromptPicker('format')" @pickGenerate="openPromptPicker('generate')" />
      
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
      
      <!-- 结果区域：批量工具用 BatchResultCards 卡片列表，其它工具用单文件 ResultArea -->
      <view class="result-section animate-fade-in-up">
        <BatchResultCards
          v-if="isBatchTool && batchCards.length > 0"
          :items="batchCards"
          :total="batchTotal"
        />
        <result-area
          v-else
          :title="toolInfo.resultTitle"
          :content="resultContent"
          :placeholder="toolInfo.resultPlaceholder"
          @regenerate="handleGenerate"
        ></result-area>
      </view>
      
      <view class="safe-area-bottom"></view>
    </scroll-view>
    
    <!-- 提示词选择弹窗（系统提示词 + 我的提示词 + AI 生成三 tab） -->
    <view v-if="showPromptPicker" class="prompt-mask" @click="closePromptPicker">
      <view class="prompt-picker" @click.stop>
        <text class="prompt-picker-title">选择{{ promptPickerTarget === 'format' ? '格式' : '生成内容' }}提示词</text>

        <!-- Tab 切换栏 -->
        <view class="prompt-tabs">
          <view
            v-for="t in pickerTabs"
            :key="t.key"
            class="prompt-tab"
            :class="{ active: currentTab === t.key }"
            @click="switchTab(t.key)"
          >
            <text>{{ t.label }}</text>
          </view>
        </view>

        <scroll-view scroll-y class="prompt-picker-list">
          <!-- ============ Tab 1：系统提示词 ============ -->
          <template v-if="currentTab === 'system'">
            <view v-if="systemPromptList.length" class="prompt-group-title">系统提示词</view>
            <view
              v-for="item in systemPromptList"
              :key="'sys-' + item.id"
              class="prompt-picker-item press-scale"
              @click="selectPrompt(item)"
            >
              <text class="prompt-picker-text">{{ item.promptName || '默认' }}</text>
            </view>
            <view v-if="systemPromptList.length === 0" class="prompt-empty">
              <text>暂无该类型系统提示词</text>
            </view>
          </template>

          <!-- ============ Tab 2：我的提示词 ============ -->
          <template v-if="currentTab === 'user'">
            <view v-if="userPromptList.length" class="prompt-group-title">我的提示词</view>
            <view
              v-for="item in userPromptList"
              :key="'user-' + item.id"
              class="prompt-picker-item press-scale"
              @click="selectPrompt(item)"
            >
              <text class="prompt-picker-text">{{ item.promptName || '（未命名）' }}</text>
            </view>
            <view v-if="userPromptList.length === 0" class="prompt-empty">
              <text>暂无该类型我的提示词</text>
            </view>
          </template>

          <!-- ============ Tab 3：AI 生成 ============ -->
          <template v-if="currentTab === 'ai'">
            <view class="ai-gen-form">
              <text class="ai-gen-label">参考需求（描述你想要的提示词风格/场景）</text>
              <textarea
                class="ai-gen-textarea"
                v-model="aiRequirement"
                :placeholder="aiRequirementPlaceholder"
                placeholder-class="textarea-placeholder"
                maxlength="1000"
                :disabled="aiLoading"
              />
              <button
                class="ai-gen-btn press-scale"
                :disabled="aiLoading || !aiRequirement.trim()"
                @click="handleAiGenerate"
              >
                <view v-if="aiLoading" class="loading-dots">
                  <view class="dot"></view>
                  <view class="dot"></view>
                  <view class="dot"></view>
                </view>
                <text v-else>去生成</text>
              </button>

              <!-- 生成结果预览 -->
              <view v-if="aiGeneratedText" class="ai-gen-preview">
                <text class="ai-gen-label">生成结果（可应用或应用并保存）</text>
                <view class="ai-gen-preview-box">
                  <text class="ai-gen-preview-text">{{ aiGeneratedText }}</text>
                </view>
                <view class="ai-gen-actions">
                  <view class="ai-gen-action-btn secondary press-scale" @click="applyGenerated(false)">
                    <text>应用</text>
                  </view>
                  <view class="ai-gen-action-btn primary press-scale" @click="openSaveNameModal">
                    <text>应用并保存</text>
                  </view>
                </view>
              </view>
            </view>
          </template>
        </scroll-view>

        <view class="prompt-picker-close" @click="closePromptPicker">
          <text>关闭</text>
        </view>
      </view>

      <!-- 保存命名弹窗（应用并保存时弹出，让用户编辑提示词名称） -->
      <view v-if="showSaveNameModal" class="save-name-mask" @click="cancelSaveName">
        <view class="save-name-modal" @click.stop>
          <text class="save-name-title">为提示词命名</text>
          <text class="save-name-hint">同一工具下名称不可重复，可修改后保存</text>
          <input
            class="save-name-input"
            v-model="saveNameInput"
            placeholder="请输入名称"
            placeholder-class="textarea-placeholder"
            :maxlength="64"
            focus
          />
          <view class="save-name-actions">
            <view class="save-name-btn secondary press-scale" @click="cancelSaveName">
              <text>取消</text>
            </view>
            <view class="save-name-btn primary press-scale" @click="confirmSaveName">
              <text>保存</text>
            </view>
          </view>
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
import BatchFilePicker from '@/components/BatchFilePicker.vue'
import { uploadFileApi, batchUpload, ocrBatchUpload, aiFileReaderBatchUpload, batchCompleted } from '@/api/ai.js'
import { streamRequest, streamUpload } from '../api/stream'
import { formatAiResult } from '@/utils/format'
import { request } from '@/api/request'
import { promptListApi, systemPromptListApi, generatePromptApi, promptAddApi } from '@/api/prompt'
import { getTool, validate } from '@/config/tools'
import BatchResultCards from '@/components/BatchResultCards.vue'

const toolId = ref('')
const currentInputType = ref('text')
const inputText = ref('')
const fileName = ref('')
const filePath = ref('')
const fileObj = ref(null)          // 原生 File/Blob 对象（H5 端用于 multipart 上传且保留真实文件名）
const uploading = ref(false)
const batchPickerRef = ref(null)   // BatchFilePicker 组件引用：通过 getFiles() 拿当前文件列表
const fileUrl = ref('')
const loading = ref(false)
const resultContent = ref('')
const batchCards = ref([])        // 批量结果卡片数据：[{ index, fileName, status, costMs, errorMsg, output }]
const batchTotal = ref(0)          // 批量任务总文件数（用于卡片显示 i/N）
const batchProgress = ref(0)       // 批量任务已处理数（用于 loading 提示）
const promptFormatText = ref('')    // 用户自定义格式提示词
const promptGenerateText = ref('')  // 用户自定义生成内容提示词
const formatPromptDisplay = ref('')  // 系统统一管理的格式提示词（只读）
const promptPickerTarget = ref('generate') // 选择弹窗当前填充的目标输入框（format/generate）
const selectedPromptId = ref('')      // 最近选中的提示词 id（可随 document-summary 一并提交）
const userPromptList = ref([])    // 用户自定义提示词（按用途过滤）
const systemPromptList = ref([])  // 系统提示词（按用途过滤）
const showPromptPicker = ref(false) // 是否显示选择弹窗
const currentTab = ref('system')    // 弹窗当前 tab：system / user / ai
const aiRequirement = ref('')        // AI 生成 tab 的需求输入
const aiGeneratedText = ref('')      // AI 生成的提示词预览
const aiLoading = ref(false)         // AI 生成中（防重复点）
const showSaveNameModal = ref(false) // 应用并保存时弹出的命名小窗
const saveNameInput = ref('')        // 命名小窗的输入
const saveNameSaving = ref(false)    // 命名保存中（防重复点）

const pickerTabs = [
  { key: 'system', label: '系统提示词' },
  { key: 'user', label: '我的提示词' },
  { key: 'ai', label: 'AI 生成' }
]

// AI 生成 tab 的需求占位提示（按 target 给场景化示例）
const aiRequirementPlaceholder = computed(() => {
  if (promptPickerTarget.value === 'format') {
    return '例如：按四段式输出：已完成/进行中/问题/下一步'
  }
  return '例如：语气正式，结构化，使用项目符号'
})

const toolInfo = computed(() => getTool(toolId.value))

// 是否是支持多文件批量上传的工具（tools.js 中定义了 fileRule）
const isBatchTool = computed(() => !!toolInfo.value.fileRule)

// 通用校验（按工具 + 输入方式）：返回第一个失败的错误文案，null = 通过
// 在 handleGenerate 入口前置校验，不通过直接 return + toast，不进 if-else 分支
const runValidation = () => validate(toolId.value, currentInputType.value, {
  filePath: filePath.value,
  batchFiles: (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : [],
  inputText: inputText.value,
  promptFormat: promptFormatText.value,
  promptGenerate: promptGenerateText.value,
  token: !!uni.getStorageSync('token')
})

// 拉取系统格式提示词（只读展示用）
const fetchSystemFormat = async () => {
  if (!toolId.value) return
  try {
    const res = await request({
      url: '/api/prompt/system/format',
      method: 'GET',
      data: { toolCode: toolId.value }
    })
    if (res && res.code === 200) {
      formatPromptDisplay.value = (res.data && typeof res.data === 'string') ? res.data : ''
    }
  } catch (e) {
    console.error('拉取系统格式提示词失败:', e)
  }
}

onLoad((option) => {
  if (!requireLogin()) return
  toolId.value = option.id || ''
  // 初始化默认输入方式
  currentInputType.value = toolInfo.value.defaultInput
    || (toolInfo.value.inputTypes && toolInfo.value.inputTypes[0])
    || 'text'
  // 拉取系统统一管理的格式提示词（只读展示）
  fetchSystemFormat()
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
  // 清空 BatchFilePicker 组件内部文件列表
  if (batchPickerRef.value && batchPickerRef.value.clear) {
    batchPickerRef.value.clear()
  }
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
  // 默认显示系统提示词 tab；重置 AI 生成 tab 的状态（避免上次残留）
  currentTab.value = 'system'
  aiRequirement.value = ''
  aiGeneratedText.value = ''
  aiLoading.value = false
  showSaveNameModal.value = false
  saveNameInput.value = ''
  saveNameSaving.value = false
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

// 关闭弹窗：点遮罩或关闭按钮
const closePromptPicker = () => {
  showPromptPicker.value = false
  showSaveNameModal.value = false
  saveNameInput.value = ''
  // 注意：textarea 已填内容（promptFormatText/promptGenerateText）保留，不清
}

// Tab 切换（不重新请求，只换显示）
const switchTab = (key) => {
  if (currentTab.value === key) return
  currentTab.value = key
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

// AI 生成：调后端 DeepSeek，按用户需求生成提示词
const handleAiGenerate = async () => {
  const requirement = aiRequirement.value.trim()
  if (!requirement) {
    uni.showToast({ title: '请输入参考需求', icon: 'none' })
    return
  }
  if (aiLoading.value) return
  aiLoading.value = true
  try {
    const res = await generatePromptApi({
      toolCode: toolId.value,
      toolName: toolInfo.value.name || '',
      toolDesc: toolInfo.value.desc || '',
      promptUse: promptPickerTarget.value,
      requirement
    })
    const text = (res && res.data && res.data.promptText) || ''
    if (!text) {
      uni.showToast({ title: '生成结果为空，请重试', icon: 'none' })
      return
    }
    aiGeneratedText.value = text
  } catch (e) {
    // request.js 已经 uni.showToast 错误消息，这里仅记日志
    console.error('AI generate failed:', e)
  } finally {
    aiLoading.value = false
  }
}

// 应用生成结果（只填入，不保存）
const applyGenerated = () => {
  const text = aiGeneratedText.value
  if (!text) return
  // AI 生成的内容无 id，清空 selectedPromptId
  selectedPromptId.value = ''
  if (promptPickerTarget.value === 'format') {
    promptFormatText.value = text
  } else {
    promptGenerateText.value = text
  }
  showPromptPicker.value = false
}

// 打开"应用并保存"命名弹窗：默认值 = 工具名 + 用途 + 提示词 + 自增数字
const openSaveNameModal = () => {
  if (!aiGeneratedText.value) return
  const baseName = buildDefaultPromptName()
  saveNameInput.value = baseName
  showSaveNameModal.value = true
}

const cancelSaveName = () => {
  showSaveNameModal.value = false
  saveNameInput.value = ''
}

// 计算默认提示词名：{工具名}{用途}提示词{自增数字}
// 例：智能识别 + 格式 + 提示词1；下次新增 → 智能识别格式提示词2
// 自增范围：仅匹配同工具同用途的现有命名
const buildDefaultPromptName = () => {
  const toolName = (toolInfo.value && toolInfo.value.name) || '提示词'
  const useLabel = promptPickerTarget.value === 'format' ? '格式' : '生成内容'
  const prefix = toolName + useLabel + '提示词'
  const re = new RegExp('^' + escapeRegExp(prefix) + '(\d+)$')
  let maxNum = 0
  for (const p of userPromptList.value) {
    const n = (p.promptName || '').match(re)
    if (n) {
      const num = parseInt(n[1], 10)
      if (num > maxNum) maxNum = num
    }
  }
  return prefix + (maxNum + 1)
}

// RegExp 转义辅助
const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

// 确认保存：填入 textarea + 调 promptAddApi
const confirmSaveName = async () => {
  if (saveNameSaving.value) return
  const text = aiGeneratedText.value
  const name = saveNameInput.value.trim()
  if (!name) {
    uni.showToast({ title: '请输入名称', icon: 'none' })
    return
  }
  saveNameSaving.value = true
  try {
    await promptAddApi(text, promptPickerTarget.value, toolId.value, name)
    // 填入 textarea
    selectedPromptId.value = ''
    if (promptPickerTarget.value === 'format') {
      promptFormatText.value = text
    } else {
      promptGenerateText.value = text
    }
    // 刷新 userPromptList（让用户能看到新保存的）
    try {
      const userRes = await promptListApi(toolId.value)
      userPromptList.value = (userRes.data || []).filter(p => p.promptUse === promptPickerTarget.value)
    } catch (e) { /* 刷新失败不影响主流程 */ }
    uni.showToast({ title: '已保存到我的提示词', icon: 'success' })
    showSaveNameModal.value = false
    showPromptPicker.value = false
  } catch (e) {
    // request.js 已 toast 错误（同名会提示"该工具下已存在同名提示词"）
    console.error('Save generated prompt failed:', e)
  } finally {
    saveNameSaving.value = false
  }
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

/**
 * 轮询批量任务结果：前端驱动（自适应间隔）
 *   - 有新 items 时：立即追加渲染 + 立即再拉
 *   - 无新 items 时：sleep 1.5s 再拉
 *   - 终态（status >= 2）时：停
 * @param {String} batchId - 批量任务 ID
 * @param {String} toolCode - 当前工具编码（预留：以后按 toolCode 决定卡片渲染方式）
 */
const pollBatchCompleted = async (batchId, toolCode) => {
  let since = 0
  let aborted = false
  // 用户切走 / 重新生成时停止轮询
  const stop = () => { aborted = true }
  // 用 uni.$once 监听页面卸载（uniapp 无 onUnload composable，简单粗暴）
  const origUnload = uni.$once
  // 简易实现：跑 1000 次上限（每个文件 sleep 1.5s + AI 处理 5-30s 足够）
  const MAX_LOOPS = 200
  for (let i = 0; i < MAX_LOOPS && !aborted; i++) {
    let res
    try {
      res = await batchCompleted(batchId, since)
    } catch (e) {
      // 拉取失败：可能是网络抖动，继续重试
      await new Promise(r => setTimeout(r, 1500))
      continue
    }
    if (!res) {
      await new Promise(r => setTimeout(r, 1500))
      continue
    }
    // 追加新 items
    const newItems = res.results || []
    if (newItems.length) {
      batchCards.value = batchCards.value.concat(newItems)
      since += newItems.length
      batchProgress.value = res.processedIndex || since
    }
    // 终态：status 2=COMPLETED 3=PARTIAL 4=FAILED
    const status = res.status
    if (status === 2 || status === 3 || status === 4) {
      break
    }
    // 没新结果 + 还在进行中：等一下再拉
    if (newItems.length === 0) {
      await new Promise(r => setTimeout(r, 1500))
    }
  }
  return stop
}

const handleGenerate = async () => {
  // 通用前置校验（按 tools.js 的 validateRules；不通过直接 toast + return，不进 if-else 分支）
  const err = runValidation()
  if (err) {
    uni.showToast({ title: err, icon: 'none' })
    return
  }

  loading.value = true
  resultContent.value = ''
  // 批量场景：清空卡片数组（保证重新生成时刷新）
  batchCards.value = []
  batchTotal.value = 0
  batchProgress.value = 0

  try {
    const id = toolId.value

    if (id === 'doc-keypoint-extract') {
      // 文档重点提取：B2 多文件批量（轮询方案）
      // 流程：batchUpload 拿 batchId → 轮询 batchCompleted 拉增量 items → 渲染到 BatchResultCards
      const docBatchFiles = (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : []
      const { batchId, fileCount } = await batchUpload({
        files: docBatchFiles,
        fields: {
          promptFormat: promptFormatText.value,
          promptGenerate: promptGenerateText.value,
          promptId: selectedPromptId.value
        }
      })
      batchTotal.value = fileCount
      await pollBatchCompleted(batchId, 'doc-keypoint-extract')

    } else if (id === 'ai-file-reader') {
      // AI 文件解读：B2 多文件批量（轮询方案），后端只接单个 prompt 字符串
      // 格式提示词 + 生成内容提示词拼接后传入；两者皆空时后端用默认解读提示词
      const readerFiles = (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : []
      const promptParts = []
      if (promptFormatText.value.trim()) promptParts.push(promptFormatText.value.trim())
      if (promptGenerateText.value.trim()) promptParts.push(promptGenerateText.value.trim())
      const { batchId, fileCount } = await aiFileReaderBatchUpload({
        files: readerFiles,
        fields: {
          prompt: promptParts.join('\n\n')
        }
      })
      batchTotal.value = fileCount
      await pollBatchCompleted(batchId, 'ai-file-reader')
    } else if (id === 'weekly-report') {
      // 周报生成：SSE 流式输出（前置校验已统一处理）
      const fullText = await runTextStream('/api/ai-office/weekly-report/stream')
      resultContent.value = formatAiResult(fullText)

    } else if (id === 'meeting-minutes') {
      // 会议纪要：SSE 流式输出（前置校验已统一处理）
      const fullText = await runTextStream('/api/ai-office/meeting-minutes/stream')
      resultContent.value = formatAiResult(fullText)

    } else if (id === 'ocr-recognize') {
      // OCR 智能识别：上传图片/PDF → 腾讯云 OCR → 调 AI 整理（前置校验已统一处理）
      // 兼容两种模式：老用户用单文件 filePath，新用户用组件多文件
      const ocrFiles = (batchPickerRef.value && batchPickerRef.value.getFiles) ? batchPickerRef.value.getFiles() : []
      const useBatch = ocrFiles.length > 0

      if (useBatch) {
        // 多文件模式：轮询方案
        const { batchId, fileCount } = await ocrBatchUpload({
          files: ocrFiles,
          fields: {
            promptFormat: promptFormatText.value,
            promptGenerate: promptGenerateText.value
          }
        })
        batchTotal.value = fileCount
        await pollBatchCompleted(batchId, 'ocr-recognize')
      } else {
        // 单文件模式：保留原打字机效果
        let fullText = ''
        resultContent.value = ''
        const charQueue = []
        let streamDone = false
        let typeTimer = null
        await new Promise((resolve, reject) => {
          const flushChar = () => {
            if (charQueue.length > 0) resultContent.value += charQueue.shift()
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
              uni.showModal({ title: '请求失败', content: err && err.message ? err.message : '未知错误', showCancel: false })
              reject(err)
            }
          })
        })
        resultContent.value = formatAiResult(fullText)
      }

    } else if (id === 'work-summary') {
      // 工作总结：SSE 流式输出（前置校验已统一处理）
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

    // Tab 切换栏
    .prompt-tabs {
      display: flex;
      border-bottom: 1rpx solid $divider-color;
      margin-bottom: $spacing-sm;

      .prompt-tab {
        flex: 1;
        text-align: center;
        padding: $spacing-sm 0;
        font-size: $font-size-sm;
        color: $text-secondary;
        position: relative;

        &.active {
          color: $text-primary;
          font-weight: 600;

          &::after {
            content: '';
            position: absolute;
            left: 50%;
            bottom: 0;
            transform: translateX(-50%);
            width: 40rpx;
            height: 4rpx;
            background-color: $text-primary;
            border-radius: 2rpx;
          }
        }
      }
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

    // ============ AI 生成 tab 样式 ============
    .ai-gen-form {
      padding: $spacing-sm $spacing-md;

      .ai-gen-label {
        display: block;
        font-size: $font-size-sm;
        color: $text-secondary;
        margin-bottom: $spacing-xs;
      }

      .ai-gen-textarea {
        width: 100%;
        min-height: 160rpx;
        background-color: $bg-color;
        border-radius: $radius-md;
        padding: $spacing-sm $spacing-md;
        font-size: $font-size-sm;
        color: $text-primary;
        border: 2rpx solid $border-color;
        box-sizing: border-box;
      }

      .ai-gen-btn {
        width: 100%;
        height: 80rpx;
        margin-top: $spacing-md;
        border-radius: $radius-pill;
        background-color: $text-primary;
        color: $bg-white;
        font-size: $font-size-md;
        font-weight: 500;
        display: flex;
        align-items: center;
        justify-content: center;
        border: none;

        &[disabled] {
          opacity: 0.5;
        }
      }

      .ai-gen-preview {
        margin-top: $spacing-md;

        .ai-gen-preview-box {
          background-color: $bg-color;
          border-radius: $radius-md;
          padding: $spacing-md;
          max-height: 400rpx;
          overflow-y: auto;
          border: 1rpx solid $border-color;

          .ai-gen-preview-text {
            font-size: $font-size-sm;
            color: $text-primary;
            white-space: pre-wrap;
            word-break: break-all;
            line-height: 1.6;
          }
        }

        .ai-gen-actions {
          display: flex;
          gap: $spacing-sm;
          margin-top: $spacing-md;

          .ai-gen-action-btn {
            flex: 1;
            height: 80rpx;
            border-radius: $radius-pill;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: $font-size-sm;

            &.secondary {
              background-color: $bg-color;
              color: $text-primary;
              border: 1rpx solid $border-color;
            }

            &.primary {
              background-color: $text-primary;
              color: $bg-white;
            }

            &:active {
              opacity: 0.8;
            }
          }
        }
      }
    }
    
    .prompt-picker-close {
      margin-top: $spacing-md;
      text-align: center;
      color: $text-secondary;
      font-size: $font-size-sm;
    }
  }

  // ============ "应用并保存"命名小窗样式 ============
  .save-name-mask {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background-color: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10;

    .save-name-modal {
      width: 80%;
      background-color: $bg-white;
      border-radius: $radius-lg;
      padding: $spacing-lg;
      display: flex;
      flex-direction: column;

      .save-name-title {
        font-size: $font-size-md;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: $spacing-xs;
      }

      .save-name-hint {
        font-size: $font-size-xs;
        color: $text-tertiary;
        margin-bottom: $spacing-md;
      }

      .save-name-input {
        width: 100%;
        height: 80rpx;
        background-color: $bg-color;
        border-radius: $radius-md;
        padding: 0 $spacing-md;
        font-size: $font-size-sm;
        color: $text-primary;
        border: 1rpx solid $border-color;
        box-sizing: border-box;
        margin-bottom: $spacing-md;
      }

      .save-name-actions {
        display: flex;
        gap: $spacing-sm;

        .save-name-btn {
          flex: 1;
          height: 80rpx;
          border-radius: $radius-pill;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: $font-size-sm;

          &.secondary {
            background-color: $bg-color;
            color: $text-primary;
            border: 1rpx solid $border-color;
          }

          &.primary {
            background-color: $text-primary;
            color: $bg-white;
          }

          &:active {
            opacity: 0.8;
          }
        }
      }
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

// ==================== doc-keypoint-extract / ocr-recognize 多文件批量（B2） ====================
// 样式已抽到 src/components/BatchFilePicker.vue，本页面不再维护
</style>