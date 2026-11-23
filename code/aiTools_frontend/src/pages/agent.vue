<template>
  <view class="page">
    <!-- #ifdef APP-PLUS -->
    <sse-renderjs />
    <!-- #endif -->

    <view class="topbar">
      <view class="topbar__text">
        <text class="topbar__title">智能体</text>
        <text class="topbar__sub">把复杂任务交给 AI，自动调用工具完成</text>
      </view>
      <view class="topbar__avatar">
        <view class="ai-avatar-wrap" :class="robotStateClass">
          <image class="ai-avatar" :src="ROBOT_BASE" mode="aspectFit" />
          <view v-if="stateBadge" class="ai-badge">
            <text v-if="stateBadge === 'question'" class="ai-badge__q">?</text>
            <svg v-else class="ai-badge__ico" viewBox="0 0 24 24" fill="none"
                 stroke="#F5671D" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18h6M10 21h4"/>
              <path d="M12 3a6 6 0 00-3.4 10.9V16h6.8v-2.1A6 6 0 0012 3z"/>
            </svg>
          </view>
        </view>
      </view>
    </view>

    <!-- 欢迎态 -->
    <view v-if="!started" class="welcome">
      <image class="welcome__robot" :src="ROBOT_BASE" mode="aspectFit" />
      <text class="welcome__t1">你好，我是 AI 助手</text>
      <text class="welcome__t2">有什么可以帮你？</text>
      <text class="sugs__label">试试这样问我：</text>
      <view class="sugs">
        <view class="sug" @click="goal = '帮我生成一份工作总结，并列出待办事项'; onStart()">帮我生成一份工作总结，并列出待办事项</view>
        <view class="sug" @click="goal = '提取这份文档的重点内容'; onStart()">提取这份文档的重点内容</view>
        <view class="sug" @click="goal = '把这段会议录音整理成会议纪要'; onStart()">把这段会议录音整理成会议纪要</view>
      </view>
    </view>

    <!-- 对话态 -->
    <scroll-view v-else scroll-y class="chat" :scroll-into-view="scrollAnchor" scroll-with-animation>
      <view class="msg msg--me">
        <view class="bubble bubble--me">{{ goal }}</view>
      </view>

      <view v-if="attachments.length" class="msg msg--me">
        <view class="files">
          <view v-for="(a,i) in attachments" :key="a.url" class="file">
            <text class="file__n">{{ a.name }}</text>
            <text class="file__x" @click="removeAttachment(i)">×</text>
          </view>
        </view>
      </view>

      <view class="msg msg--ai">
        <view class="ai-body">
          <view class="card">
            <view class="card__title">任务规划</view>
            <view v-if="!steps.length" class="tl__empty">
              <svg class="tl__spin" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="9" fill="none" stroke="#F5671D" stroke-width="3"
                        stroke-linecap="round" stroke-dasharray="42 15"/>
              </svg>
              <text class="tl__empty-t">正在思考…</text>
            </view>
            <view v-for="(st,i) in steps" :key="i" class="tl" :class="{ 'tl--run': isRunningStep(i), 'tl--wait': !isRunningStep(i) && !isDoneStep(i) }">
              <view class="tl__dot">
                <svg v-if="isDoneStep(i)" class="tl__ico" viewBox="0 0 24 24">
                  <path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="#fff" stroke-width="3"
                        stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <svg v-else-if="isRunningStep(i)" class="tl__ring" viewBox="0 0 24 24">
                  <circle cx="12" cy="12" r="9" fill="none" stroke="#F5671D" stroke-width="3"
                          stroke-linecap="round" stroke-dasharray="42 15"/>
                </svg>
              </view>
              <view class="tl__body">
                <text class="tl__t">{{ st.title || st.text || '处理中' }}</text>
                <text class="tl__s">{{ isRunningStep(i) ? '进行中…' : '已完成' }}</text>
              </view>
            </view>
          </view>

          <view v-if="answer" class="card card--answer">
            <MarkdownView :content="answer" />
          </view>

          <view v-if="running" class="typing">
            <text class="typing__t">正在思考</text>
            <view class="typing__dots"><i></i><i></i><i></i></view>
          </view>
          <view v-if="errorMsg" class="err">{{ errorMsg }}</view>
        </view>
      </view>

      <view id="agent-bottom" class="bottom-anchor"></view>
    </scroll-view>

    <!-- 模型浮层：贴着输入框上方展开（不用底部抽屉） -->
    <view v-if="showModelMenu" class="model-mask" @click="showModelMenu = false"></view>
    <view v-if="showModelMenu" class="model-menu">
      <view
        v-for="(n,i) in modelNames"
        :key="i"
        class="model-menu__item"
        @click="pickModel(i)"
      >
        <text class="model-menu__t">{{ n }}</text>
        <text v-if="i === modelIndex" class="model-menu__ok">✓</text>
      </view>
    </view>

    <!-- 底部输入条（参考豆包：上方输入，下方工具 + 模型选择 + 发送） -->
    <view class="composer">
      <input v-model="goal" class="composer__input" placeholder="发消息或按住空格说话..." :disabled="running" />
      <view class="composer__row">
        <view class="composer__plus" @click="onPickFiles">＋</view>
        <view class="composer__gap"></view>
        <view class="composer__model" @click="showModelMenu = !showModelMenu">
          <text class="composer__model-t">{{ modelNames[modelIndex] }}</text>
          <text class="composer__chev">›</text>
        </view>
        <view v-if="running" class="composer__stop" @click="onCancel">
          <view class="stop-square"></view>
        </view>
        <view v-else class="composer__send" @click="onStart">
          <text class="send-arrow">↑</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { refreshTabBarStyle } from '@/utils/theme'
import MarkdownView from '@/components/MarkdownView.vue'
import { agentRun, uploadAgentFile, cancelAgentRun } from '@/api/agent'
import { listUserAiModels } from '@/api/aiModel'
import { pickFiles } from '@/utils/filePicker'
import SseRenderjs from '@/components/sse-renderjs/sse-renderjs.vue'

// 主题是在设置页（非 tabBar 页）切换的，回到本页时同步一次底栏配色
onShow(() => {
  refreshTabBarStyle()
})

const goal = ref('')
const attachments = ref([])
const started = ref(false)
const running = ref(false)

/*
 * 机器人三态：同一张图 + 三种动效，不再切换三张不同渲染的图
 * （不同图的颗粒/光照对不上，轮换会跳戏）。
 */
const ROBOT_HEAD = '/static/ref/robot-head.png'
const ROBOT_BASE = '/static/ref/robot-base.png'
const STATE_CLASSES = ['is-thinking', 'is-idea']
const stateIdx = ref(0)
let stateTimer = null
const robotStateClass = computed(() => (running.value ? STATE_CLASSES[stateIdx.value] : ''))
const STATE_BADGES = ['question', 'bulb']
const stateBadge = computed(() => (running.value ? STATE_BADGES[stateIdx.value] : ''))
watch(running, (v) => {
  if (stateTimer) { clearInterval(stateTimer); stateTimer = null }
  if (v) {
    stateIdx.value = 0
    stateTimer = setInterval(() => { stateIdx.value = (stateIdx.value + 1) % STATE_CLASSES.length }, 1100)
  } else {
    stateIdx.value = 0
  }
})
const steps = ref([])
const answer = ref('')
/** 是否正在逐字输出（MarkdownView 在此期间走纯文本分支，避免每个字重解析 markdown） */
const typing = ref(false)
const errorMsg = ref('')
const scrollAnchor = ref('')
const runId = ref('')

/** 用户自选模型（BYOK）；留空 = 用系统默认 */
const modelIndex = ref(0)
const modelNames = ref(['系统默认'])
const modelCodes = ref([''])

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

/**
 * 选附件：先上传拿 URL（智能体的 SSE 走 JSON body，带不了 multipart）。
 *
 * <p>⚠️ <b>App 端只能选图片</b>：文档/音频走的是 {@code uni.chooseFile} /
 * {@code uni.chooseMessageFile}，这两个在 App 端**不被支持**
 * （uni-app 官方只在 H5 与小程序提供；App 端要么上插件市场，要么用 plus.io 自绘）。
 * 所以这里按端分流：App 端用全平台都支持的 {@code uni.chooseImage}，
 * H5 端才能选文档/音频。
 */
const onPickFiles = async () => {
  const remain = Math.max(1, 10 - attachments.value.length)
  // #ifdef APP-PLUS
  const res = await pickFiles({
    count: Math.min(remain, 9),      // chooseImage 单次最多 9 张
    fileType: 'image',
    sourceType: ['album', 'camera'],
    fileLabel: '图片'
  })
  // #endif
  // #ifndef APP-PLUS
  const res = await pickFiles({
    count: remain,
    fileType: 'file',
    sourceType: ['album', 'camera'],
    fileLabel: '附件'
  })
  // #endif
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
        name: up.data.fileName || item.fileName || item.name || '附件',
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

const showModelMenu = ref(false)
const pickModel = (i) => { modelIndex.value = i; showModelMenu.value = false }

const isRunningStep = (i) => running.value && i === steps.value.length - 1
const isDoneStep = (i) => !isRunningStep(i)

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
  // 用 scroll-into-view + 锚点元素（App 端 scroll-top 设大值不可靠）
  scrollAnchor.value = ''
  await nextTick()
  scrollAnchor.value = 'agent-bottom'
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
          if (kind === '运行') {
            // 首帧：拿到 runId（取消接口的入参）
            runId.value = body
          } else if (kind === '思考') {
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
          appendAnswer(text)
        },
        onDone: () => {
          flushAnswer()
          running.value = false
        },
        onError: (err) => {
          flushAnswer()
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

/** 取消本次运行 */
const onCancel = async () => {
  if (!runId.value || !running.value) return
  try {
    await cancelAgentRun(runId.value)
    uni.showToast({ title: '已发送取消请求', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '取消失败', icon: 'none' })
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
  runId.value = ''
  typing.value = false
  chunkTarget = 'answer'
}
</script>

<style lang="scss" scoped>
$ref-orange: #F5671D;
$ref-ink: var(--text-primary);
$ref-sub: var(--text-secondary);

.page {
  position: relative;
  height: 100vh;
  /* #ifdef H5 */
  height: calc(100vh - var(--window-bottom, 0px));
  /* #endif */
  background: var(--bg-page);
  display: flex;
  flex-direction: column;
  padding-top: var(--status-bar-height, 0px);
}

.topbar {
  position: relative;
  height: 116rpx;
  padding: 0 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
  flex-shrink: 0;
}
.topbar__text { display: flex; flex-direction: column; }
.topbar__title { font-size: 34rpx; font-weight: 700; color: $ref-ink; }
.topbar__sub { font-size: 22rpx; color: $ref-sub; margin-top: 4rpx; }

/* 欢迎态 */
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 56rpx 0;
}
.welcome__robot { width: 340rpx; height: 340rpx; }
.welcome__t1 { margin-top: 16rpx; font-size: 38rpx; font-weight: 700; color: $ref-ink; }
.welcome__t2 { margin-top: 10rpx; font-size: 28rpx; color: $ref-sub; }
.sugs__label {
  margin-top: 44rpx;
  font-size: 24rpx;
  color: $ref-sub;
  align-self: flex-start;
}
.sugs { margin-top: 16rpx; width: 100%; display: flex; flex-direction: column; gap: 18rpx; }
.sug {
  background: var(--bg-card);
  border-radius: 999rpx;
  padding: 22rpx 28rpx;
  font-size: 26rpx;
  text-align: left;
  color: $ref-ink;
}
.sug:active { opacity: .9; }

/* 对话态 */
.chat { flex: 1; min-height: 0; padding: 8rpx 32rpx 24rpx; }
.msg { display: flex; margin-bottom: 22rpx; }
.msg--me { justify-content: flex-end; }
.msg--ai { display: block; }

.bubble--me {
  max-width: 76%;
  background: #FBE3D5;
  color: $ref-ink;
  font-size: 27rpx;
  line-height: 1.6;
  padding: 20rpx 24rpx;
  border-radius: 24rpx 24rpx 6rpx 24rpx;
}
.files { display: flex; flex-direction: column; gap: 10rpx; align-items: flex-end; }
.file {
  background: var(--bg-card); border-radius: 14rpx; padding: 12rpx 16rpx;
  display: flex; align-items: center; gap: 12rpx; font-size: 24rpx;
}
.file__x { color: $ref-sub; }

.ai-avatar-wrap {
  position: relative;
  width: 84rpx;
  height: 84rpx;
  flex-shrink: 0;
}
.topbar__avatar {
  display: flex;
  align-items: center;
}
.ai-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: var(--bg-card);
  display: block;
  padding: 6rpx;
  box-sizing: border-box;
}
.ai-badge {
  position: absolute;
  right: -6rpx;
  top: -6rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: var(--bg-card);
  border: 2rpx solid $ref-orange;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2rpx 6rpx rgba(0,0,0,.08);
}
.ai-badge__q { font-size: 22rpx; font-weight: 700; color: $ref-orange; line-height: 1; }
.ai-badge__ico { width: 22rpx; height: 22rpx; }
/* 三态：同一张图，只改动效，保证颗粒一致 */
.ai-avatar-wrap.is-thinking { animation: botThink 2.2s ease-in-out infinite; }
.ai-avatar-wrap.is-idea     { animation: botIdea 1.1s ease-out infinite; }
@keyframes botThink {
  0%, 100% { transform: scale(1);    opacity: .85; }
  50%      { transform: scale(1.08); opacity: 1; }
}
@keyframes botIdea {
  0%   { transform: scale(1)    rotate(0deg); }
  25%  { transform: scale(1.16) rotate(-6deg); }
  50%  { transform: scale(1.04) rotate(4deg); }
  100% { transform: scale(1)    rotate(0deg); }
}
.ai-body { width: 100%; display: flex; flex-direction: column; gap: 16rpx; }

.card { background: var(--bg-card); border-radius: 24rpx; padding: 24rpx; }
.card__title { font-size: 26rpx; font-weight: 700; color: $ref-ink; margin-bottom: 16rpx; }
.card--answer { font-size: 27rpx; color: $ref-ink; line-height: 1.7; }

.tl { display: flex; gap: 16rpx; padding: 12rpx 0; align-items: flex-start; }
.tl__dot {
  width: 44rpx; height: 44rpx; border-radius: 50%;
  background: $ref-orange;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.tl__ico { width: 26rpx; height: 26rpx; }
.tl--run .tl__dot { background: var(--bg-card); border: 3rpx solid $ref-orange; }
.tl--wait .tl__dot { background: #EFEFF3; }
.tl__ring,
.tl__spin {
  width: 30rpx; height: 30rpx;
  flex-shrink: 0;
  animation: spin .9s linear infinite;
  transform-origin: 50% 50%;
}
@keyframes spin { to { transform: rotate(360deg); } }
.tl__body { flex: 1; min-width: 0; }
.tl__t { display: block; font-size: 26rpx; font-weight: 600; color: $ref-ink; }
.tl__s { display: block; margin-top: 4rpx; font-size: 22rpx; color: $ref-sub; }
.tl--run .tl__s { color: $ref-orange; }
.tl__empty { display: flex; align-items: center; gap: 12rpx; padding: 8rpx 0; }
.tl__empty-t { font-size: 25rpx; color: $ref-sub; }

.typing { display: flex; align-items: center; gap: 10rpx; padding-left: 6rpx; }
.typing__t { font-size: 24rpx; color: $ref-sub; }
.typing__dots { display: flex; gap: 6rpx; }
.typing__dots i {
  width: 8rpx; height: 8rpx; border-radius: 50%; background: #C9C9D2;
  animation: blink 1.2s infinite;
}
.typing__dots i:nth-child(2) { animation-delay: .2s; }
.typing__dots i:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,60%,100% { opacity: .3 } 30% { opacity: 1 } }

.err { font-size: 24rpx; color: #C0392B; }

/* 输入条（两行：输入 + 工具栏） */
.composer {
  flex-shrink: 0;
  margin: 0 24rpx 16rpx;
  background: var(--bg-card);
  border-radius: 32rpx;
  padding: 20rpx 24rpx 16rpx;
}
.composer__input {
  width: 100%;
  height: 56rpx;
  font-size: 27rpx;
  color: $ref-ink;
}
.composer__row {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}
.composer__plus {
  width: 56rpx; height: 56rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 34rpx; color: #4A4A54;
  background: var(--bg-gray);
}
.composer__gap { flex: 1; }
.composer__model {
  display: flex;
  align-items: center;
  gap: 4rpx;
  padding: 0 10rpx;
  max-width: 220rpx;
}
.composer__model-t {
  font-size: 24rpx;
  color: $ref-ink;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.composer__chev { font-size: 24rpx; color: $ref-sub; }
.composer__send {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  background: $ref-orange; color: #fff;
  display: flex; align-items: center; justify-content: center;
  margin-left: 12rpx;
}
.send-arrow { font-size: 30rpx; font-weight: 700; line-height: 1; }
.composer__stop {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  background: #E14B3B;
  display: flex; align-items: center; justify-content: center;
  margin-left: 12rpx;
}
.stop-square { width: 22rpx; height: 22rpx; border-radius: 4rpx; background: var(--bg-card); }

.model-mask {
  position: absolute; left: 0; right: 0; top: 0; bottom: 0; z-index: 20;
}
.model-menu {
  position: absolute;
  right: 32rpx;
  bottom: 200rpx;
  z-index: 21;
  min-width: 260rpx;
  max-width: 420rpx;
  background: var(--bg-card);
  border-radius: 20rpx;
  padding: 6rpx 0;
  box-shadow: 0 8rpx 28rpx rgba(0, 0, 0, .12);
}
.model-menu__item {
  display: flex; align-items: center;
  padding: 16rpx 22rpx;
  font-size: 25rpx; color: $ref-ink;
}
.model-menu__item:active { background: #F6F6F9; }
.model-menu__t { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-menu__ok { color: $ref-ink; font-size: 24rpx; margin-left: 12rpx; }

.bottom-anchor { height: 1rpx; }
</style>
