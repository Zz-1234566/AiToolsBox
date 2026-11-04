<template>
  <view class="page">
    <!-- ============ 顶栏 ============ -->
    <view class="topbar">
      <view class="topbar__back" hover-class="topbar__back--hover" @click="goBack">
        <text class="topbar__back-icon">‹</text>
      </view>
      <text class="topbar__title">智能体</text>
      <view class="topbar__history" hover-class="topbar__history--hover" @click="goHistory">
        <text class="topbar__history-text">记录</text>
      </view>
    </view>

    <scroll-view scroll-y class="page-content" :scroll-top="scrollTop" scroll-with-animation>
      <!-- ============ 输入区（未开始运行时） ============ -->
      <view v-if="!started" class="input-area">
        <text class="input-area__label">你的目标</text>
        <textarea
          class="input-area__textarea"
          v-model="goal"
          placeholder="例如：把这份会议录音整理成会议纪要，并列出待办事项"
          :maxlength="2000"
          :disabled="running"
          auto-height
        />
        <text class="input-area__hint">{{ goal.length }}/2000</text>

        <!-- 附件 -->
        <view class="attach-area">
          <view class="attach-area__head">
            <text class="attach-area__label">附件（可选）</text>
            <view class="attach-area__add" @click="onPickFiles">+ 添加</view>
          </view>
          <view v-if="attachments.length === 0" class="attach-area__empty">
            支持图片 / PDF / Word / TXT / 音频，智能体会自己决定用哪个工具处理
          </view>
          <view v-for="(a, i) in attachments" :key="a.url" class="attach-item">
            <text class="attach-item__name">{{ a.name }}</text>
            <text class="attach-item__remove" @click="removeAttachment(i)">移除</text>
          </view>
        </view>

        <!-- 模型（BYOK） -->
        <view class="model-row">
          <text class="model-row__label">使用模型</text>
          <picker
            class="model-row__picker"
            mode="selector"
            :range="modelNames"
            :value="modelIndex"
            @change="onModelChange"
          >
            <view class="model-row__value">{{ modelNames[modelIndex] }}</view>
          </picker>
        </view>

        <button class="btn-primary" :disabled="!goal.trim() || running" @click="onStart">
          {{ running ? '运行中…' : '开始' }}
        </button>
        <text class="btn-hint">智能体会自己决定调用哪些工具，最多 8 步</text>
      </view>

      <!-- ============ 过程 + 答案 ============ -->
      <view v-else class="run-area">
        <!-- 目标回显 -->
        <view class="goal-card">
          <text class="goal-card__label">目标</text>
          <text class="goal-card__text">{{ goal }}</text>
        </view>

        <!-- 步骤 -->
        <view v-for="(s, i) in steps" :key="i" class="step" :class="'step--' + s.type">
          <!-- 思考 -->
          <view v-if="s.type === 'thought'" class="step__thought">
            <text class="step__thought-text">{{ s.text }}</text>
          </view>

          <!-- 工具 -->
          <view v-else-if="s.type === 'tool'" class="step__tool">
            <view class="step__tool-head">
              <text class="step__tool-icon">⚙</text>
              <text class="step__tool-title">{{ s.title }}</text>
            </view>
            <view v-if="s.result" class="step__tool-result">
              <text class="step__tool-result-text">{{ s.result }}</text>
            </view>
          </view>
        </view>

        <!-- 最终答案 -->
        <view v-if="answer" class="answer-card">
          <text class="answer-card__label">最终答案</text>
          <!-- App 端不能用 v-html —— MarkdownView 已按端分流（H5 v-html / App mp-html） -->
          <MarkdownView class="answer-card__md" :source="answer" />
        </view>

        <!-- 出错 -->
        <view v-if="errorMsg" class="error-card">
          <text class="error-card__text">{{ errorMsg }}</text>
        </view>

        <!-- 运行中指示 -->
        <view v-if="running" class="running">
          <text class="running__text">智能体正在思考…</text>
        </view>

        <!-- 完成后：再问一个 -->
        <button v-if="!running" class="btn-secondary" @click="reset">再问一个</button>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { agentRun, uploadAgentFile } from '@/api/agent'
import { pickFiles } from '@/utils/filePicker'
import { safeBack } from '@/utils/pageTransition'

const goal = ref('')
const attachments = ref([])
const started = ref(false)
const running = ref(false)
const steps = ref([])
const answer = ref('')
const errorMsg = ref('')
const scrollTop = ref(0)

/** 用户自选模型（BYOK）；留空 = 用系统默认 */
const modelIndex = ref(0)
const modelNames = ref(['系统默认'])
const modelCodes = ref([''])

const goBack = () => {
  safeBack('/pages/index')
}

const goHistory = () => {
  uni.navigateTo({ url: '/pages/agent-history' })
}

/** 载入用户自己的模型（BYOK）：智能体最烧 token，用户必须能用自己的 Key */
const loadModels = async () => {
  try {
    const res = await listUserAiModels()
    const list = (res && res.data) || []
    const usable = list.filter(m => m.capability === 'text' || m.capability === 'vision')
    if (usable.length) {
      modelNames.value = ['系统默认', ...usable.map(m => m.nickname || m.modelCode)]
      modelCodes.value = ['', ...usable.map(m => m.modelCode)]
    }
  } catch (e) {
    // 拉不到就保持「系统默认」这一个选项，不影响主流程
  }
}
loadModels()

/** 选附件：先上传拿 URL（智能体的 SSE 走 JSON body，带不了 multipart） */
const onPickFiles = async () => {
  const res = await pickFiles({
    count: 10 - attachments.value.length,
    fileType: 'all',
    sourceType: ['album', 'camera'],
    fileLabel: '附件'
  })
  if (!res.ok) {
    if (res.reason !== 'cancelled') {
      uni.showToast({ title: res.message || '选择失败', icon: 'none' })
    }
    return
  }
  uni.showLoading({ title: '上传中…', mask: true })
  try {
    for (const item of res.files) {
      const up = await uploadAgentFile(item)
      if (!up || up.code !== 200 || !up.data || !up.data.fileUrl) {
        uni.showToast({ title: (up && up.message) || '附件上传失败', icon: 'none' })
        break
      }
      attachments.value.push({
        name: up.data.fileName || item.name || '附件',
        url: up.data.fileUrl
      })
    }
  } catch (e) {
    uni.showToast({ title: '附件上传失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

const removeAttachment = (idx) => {
  attachments.value.splice(idx, 1)
}

const onModelChange = (e) => {
  modelIndex.value = Number(e.detail.value) || 0
}

/** 当前普通 chunk 该落到哪里：工具卡片的结果区 / 答案区 */
let chunkTarget = 'answer'

const pushStep = (step) => {
  steps.value.push(step)
  scrollToBottom()
}

const scrollToBottom = async () => {
  await nextTick()
  // 页面内容高度随步骤增长，用一个大值触发滚到底
  scrollTop.value = 999999
}

const onStart = async () => {
  if (running.value || !goal.value.trim()) return
  started.value = true
  running.value = true
  errorMsg.value = ''
  answer.value = ''
  steps.value = []
  chunkTarget = 'answer'

  try {
    agentRun(
      {
        goal: goal.value.trim(),
        modelCode: modelCodes[modelIndex.value] || '',
        files: attachments.value.map(a => ({ name: a.name, url: a.url }))
      },
      {
        onMarker: (marker) => {
          const idx = marker.indexOf(':')
          const kind = idx >= 0 ? marker.slice(0, idx) : marker
          const body = idx >= 0 ? marker.slice(idx + 1) : ''
          if (kind === '思考') {
            chunkTarget = 'answer'
            pushStep({ type: 'thought', text: body })
          } else if (kind === '工具') {
            chunkTarget = 'tool'
            pushStep({ type: 'tool', title: body, result: '' })
          } else if (kind === '工具结果结束') {
            chunkTarget = 'answer'
          } else if (kind === '答案') {
            chunkTarget = 'answer'
          } else if (kind === '完成') {
            running.value = false
          }
        },
        onChunk: (text) => {
          if (!text) return
          if (chunkTarget === 'tool' && steps.value.length) {
            const last = steps.value[steps.value.length - 1]
            if (last.type === 'tool') {
              last.result += text
              scrollToBottom()
              return
            }
          }
          answer.value += text
          scrollToBottom()
        },
        onDone: () => {
          running.value = false
        },
        onError: (err) => {
          running.value = false
          errorMsg.value = (err && err.message) || '智能体运行失败，请重试'
        }
      }
    )
  } catch (e) {
    running.value = false
    errorMsg.value = (e && e.message) || '智能体运行失败，请重试'
  }
}

const reset = () => {
  started.value = false
  running.value = false
  steps.value = []
  answer.value = ''
  errorMsg.value = ''
  goal.value = ''
  attachments.value = []
  chunkTarget = 'answer'
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F9FAFB;
  display: flex;
  flex-direction: column;
}

/* ============ 顶栏 ============ */
.topbar {
  padding: var(--status-bar-height, 0px) 24rpx 0;
  height: 88rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #E5E7EB;

  &__back {
    width: 56rpx;
    height: 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    &-icon { font-size: 44rpx; color: #374151; line-height: 1; }
    &--hover { opacity: 0.6; }
  }
  &__title {
    flex: 1;
    text-align: center;
    font-size: 32rpx;
    font-weight: 600;
    color: #111827;
  }
  &__history {
    width: 96rpx;
    height: 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    &-text { font-size: 26rpx; color: #3B82F6; }
    &--hover { opacity: 0.6; }
  }
}

.page-content {
  flex: 1;
  padding: 24rpx 32rpx 64rpx;
}

/* ============ 输入区 ============ */
.input-area {
  &__label {
    display: block;
    font-size: 26rpx;
    color: #6B7280;
    margin-bottom: 12rpx;
  }
  &__textarea {
    width: 100%;
    min-height: 200rpx;
    background: #FFFFFF;
    border: 2rpx solid #E5E7EB;
    border-radius: 16rpx;
    padding: 24rpx;
    font-size: 28rpx;
    color: #374151;
    box-sizing: border-box;
  }
  &__hint {
    display: block;
    text-align: right;
    font-size: 22rpx;
    color: #9CA3AF;
    margin-top: 8rpx;
  }
}

/* ============ 附件 ============ */
.attach-area {
  margin-top: 28rpx;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12rpx;
  }
  &__label { font-size: 26rpx; color: #6B7280; }
  &__add {
    font-size: 24rpx;
    color: #3B82F6;
    padding: 8rpx 20rpx;
    border-radius: 999rpx;
    background: #EFF6FF;
  }
  &__empty {
    font-size: 22rpx;
    color: #9CA3AF;
    line-height: 1.6;
    padding: 16rpx 20rpx;
    background: #F3F4F6;
    border-radius: 12rpx;
  }
}
.attach-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FFFFFF;
  border-radius: 12rpx;
  padding: 18rpx 20rpx;
  margin-bottom: 12rpx;

  &__name {
    flex: 1;
    font-size: 25rpx;
    color: #374151;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &__remove { font-size: 24rpx; color: #EF4444; padding-left: 20rpx; }
}

/* ============ 模型选择 ============ */
.model-row {
  display: flex;
  align-items: center;
  margin-top: 28rpx;

  &__label { font-size: 26rpx; color: #6B7280; width: 140rpx; }
  &__picker { flex: 1; }
  &__value {
    background: #FFFFFF;
    border: 2rpx solid #E5E7EB;
    border-radius: 12rpx;
    padding: 18rpx 20rpx;
    font-size: 26rpx;
    color: #374151;
  }
}

/* ============ 按钮 ============ */
.btn-primary {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  margin-top: 32rpx;
  background: #3B82F6;
  color: #FFFFFF;
  border-radius: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  border: none;

  &[disabled] { background: #E5E7EB; color: #FFFFFF; }
}
.btn-secondary {
  width: 100%;
  height: 80rpx;
  line-height: 80rpx;
  margin-top: 32rpx;
  background: #FFFFFF;
  color: #3B82F6;
  border: 2rpx solid #3B82F6;
  border-radius: 16rpx;
  font-size: 28rpx;
}
.btn-hint {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: #9CA3AF;
  margin-top: 12rpx;
}

/* ============ 运行区 ============ */
.goal-card {
  background: #EFF6FF;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 24rpx;

  &__label { display: block; font-size: 22rpx; color: #3B82F6; margin-bottom: 8rpx; }
  &__text { font-size: 26rpx; color: #1F2937; line-height: 1.6; }
}

.step {
  margin-bottom: 16rpx;

  &__thought {
    background: #FFFFFF;
    border-left: 6rpx solid #C7D2FE;
    border-radius: 12rpx;
    padding: 16rpx 20rpx;
  }
  &__thought-text { font-size: 24rpx; color: #6B7280; line-height: 1.6; }

  &__tool {
    background: #FFFFFF;
    border-radius: 16rpx;
    padding: 20rpx 24rpx;
  }
  &__tool-head {
    display: flex;
    align-items: center;
  }
  &__tool-icon { font-size: 26rpx; margin-right: 12rpx; }
  &__tool-title { flex: 1; font-size: 26rpx; color: #374151; font-weight: 600; }
  &__tool-result {
    margin-top: 12rpx;
    background: #F9FAFB;
    border-radius: 10rpx;
    padding: 14rpx 16rpx;
    max-height: 400rpx;
    overflow: hidden;
  }
  &__tool-result-text { font-size: 23rpx; color: #6B7280; line-height: 1.6; white-space: pre-wrap; }
}

.answer-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-top: 24rpx;

  &__label { display: block; font-size: 24rpx; color: #3B82F6; margin-bottom: 12rpx; }
  &__md { font-size: 28rpx; }
}

.error-card {
  background: #FEF2F2;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  margin-top: 24rpx;

  &__text { font-size: 25rpx; color: #B91C1C; line-height: 1.6; }
}

.running {
  text-align: center;
  padding: 24rpx 0;

  &__text { font-size: 24rpx; color: #9CA3AF; }
}
</style>
