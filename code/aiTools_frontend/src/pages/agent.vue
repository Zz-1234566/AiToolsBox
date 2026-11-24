<template>
  <view class="page">
    <!-- #ifdef APP-PLUS -->
    <sse-renderjs />
    <!-- #endif -->

    <view class="topbar">
      <view class="topbar__left">
        <view class="topbar__menu" @click="openDrawer">
          <svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
        </view>
        <view class="topbar__text">
        <text class="topbar__title">智小T</text>
          <text class="topbar__sub">把复杂任务交给小T，自动调用工具完成</text>
        </view>
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
      <image v-if="!isDarkMode" class="welcome__robot" :src="ROBOT_ANIM_LIGHT" mode="aspectFill" />
      <text class="welcome__t1">你好，我是智小T</text>
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
      <!-- 历史消息回放（切换会话时加载） -->
      <block v-for="(hm, hi) in historyMessages" :key="'h' + hi">
        <view v-if="hm.role === 'user'" class="msg msg--me">
          <view class="me-col">
            <view class="bubble bubble--me">{{ hm.content }}</view>
            <view v-if="histFiles(hm).length" class="files">
              <view v-for="(f, fi) in histFiles(hm)" :key="'f' + fi" class="file">
                <view class="file__ico">
                  <template v-if="fileKind(f.name) === 'doc'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/></svg></template>
                  <template v-else-if="fileKind(f.name) === 'image'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.4"/><path d="M21 16l-5-5-9 9"/></svg></template>
                  <template v-else-if="fileKind(f.name) === 'audio'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V6l10-2v12"/><circle cx="6" cy="18" r="3"/><circle cx="16" cy="16" r="3"/></svg></template>
                  <template v-else><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/></svg></template>
                </view>
                <text class="file__n">{{ f.name }}</text>
              </view>
            </view>
          </view>
        </view>
        <view v-else class="msg msg--ai">
          <view class="ai-body">
            <!-- 历史任务规划（默认收起，点标题展开） -->
            <view v-if="histSteps(hm).length" class="card">
              <view class="card__title card__title--toggle" @click="histOpen[hm.seq] = !histOpen[hm.seq]">
                <text>任务规划</text>
                <view class="card__meta">
                  <text class="card__count">{{ histSteps(hm).length }} 步</text>
                  <svg class="card__chev" :class="{ 'card__chev--collapsed': !histOpen[hm.seq] }" viewBox="0 0 24 24"
                       fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M6 15l6-6 6 6"/>
                  </svg>
                </view>
              </view>
              <view v-show="histOpen[hm.seq]" v-for="(st, si) in histSteps(hm)" :key="si" class="tl">
                <view class="tl__dot">
                  <svg class="tl__ico" viewBox="0 0 24 24">
                    <path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="#fff" stroke-width="3"
                          stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </view>
                <view class="tl__body">
                  <text class="tl__t tl__t--hist">{{ histStepTitle(st) }}</text>
                  <text class="tl__s">{{ st.costMs ? '耗时 ' + fmtMs(st.costMs) : '已完成' }}</text>
                </view>
              </view>
            </view>

            <view class="card card--answer">
              <MarkdownView :source="hm.content" />
              <view class="ans-foot">
                <text class="ans-disclaimer">本回答由AI生成，内容仅供参考，请仔细甄别</text>
                <view class="ans-acts">
                  <view class="ans-act" @click="copyHistory(hm)">
                    <svg viewBox="0 0 24 24"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 012-2h8"/></svg>
                  </view>
                  <view v-if="hi === historyMessages.length - 1" class="ans-act ans-act--right" @click="regenerateFrom(hi)">
                    <svg viewBox="0 0 24 24"><path d="M20 11a8 8 0 10-2.3 5.7"/><path d="M20 4v7h-7"/></svg>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>
      </block>

      <view v-if="sentGoal" class="msg msg--me">
        <view class="bubble bubble--me">{{ sentGoal }}</view>
      </view>

      <view v-if="attachments.length" class="msg msg--me">
        <view class="files">
          <view v-for="(a,i) in attachments" :key="a.url" class="file">
            <view class="file__ico">
            <template v-if="fileKind(a.name) === 'doc'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/></svg></template>
            <template v-else-if="fileKind(a.name) === 'image'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.4"/><path d="M21 16l-5-5-9 9"/></svg></template>
            <template v-else-if="fileKind(a.name) === 'audio'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V6l10-2v12"/><circle cx="6" cy="18" r="3"/><circle cx="16" cy="16" r="3"/></svg></template>
            <template v-else><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/></svg></template>
            </view>
            <text class="file__n">{{ a.name }}</text>
          </view>
        </view>
      </view>

      <view class="msg msg--ai">
        <view class="ai-body">
          <view v-if="steps.length || running" class="card">
            <view class="card__title card__title--toggle" @click="planCollapsed = !planCollapsed">
              <text>任务规划</text>
              <view v-if="steps.length" class="card__meta">
                <text class="card__count">{{ steps.length }} 步</text>
                <text v-if="totalElapsed" class="card__count card__count--total">· {{ totalElapsed }}</text>
                <svg class="card__chev" :class="{ 'card__chev--collapsed': planCollapsed }" viewBox="0 0 24 24"
                     fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M6 15l6-6 6 6"/>
                </svg>
              </view>
            </view>
            <view v-show="!planCollapsed" v-if="!steps.length" class="tl__empty">
              <svg class="tl__spin" viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="9" fill="none" stroke="#F5671D" stroke-width="3"
                        stroke-linecap="round" stroke-dasharray="42 15"/>
              </svg>
              <text class="tl__empty-t">正在思考…</text>
            </view>
            <view v-show="!planCollapsed" v-for="(st,i) in steps" :key="i" class="tl" :class="{ 'tl--run': isRunningStep(i), 'tl--wait': !isRunningStep(i) && !isDoneStep(i) }">
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
                <text class="tl__t" :class="{ 'tl__t--live': isRunningStep(i) }">{{ st.title || st.text || '处理中' }}</text>
                <text class="tl__s">{{ stepStatusText(st) }}</text>
              </view>
            </view>
          </view>

          <!-- 答案 + 免责声明 + 操作，同属一张卡片 -->
          <view v-if="answer" class="card card--answer">
            <MarkdownView :source="answer" :streaming="running" />
            <view v-if="!running" class="ans-foot">
              <text class="ans-disclaimer">本回答由AI生成，内容仅供参考，请仔细甄别</text>
              <view class="ans-acts">
                <view class="ans-act" @click="copyAnswer">
                  <svg viewBox="0 0 24 24"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 012-2h8"/></svg>
                </view>
                <view class="ans-act ans-act--right" @click="regenerate">
                  <svg viewBox="0 0 24 24"><path d="M20 11a8 8 0 10-2.3 5.7"/><path d="M20 4v7h-7"/></svg>
                </view>
              </view>
            </view>
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
    <view v-if="showCtxMenu" class="model-mask" @click="showCtxMenu = false"></view>
    <view v-if="showCtxMenu" class="ctx-menu">
      <text class="ctx-menu__t">上下文 {{ ctxTokens > 0 ? ctxPercent + '%' : '—' }} / {{ ctxWindowLabel }}</text>
      <text v-if="ctxTokens > 0" class="ctx-menu__s">已用 {{ ctxTokens }} tokens</text>
    </view>

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

    <!-- 已上传附件（欢迎态：展示在输入条上方；发送后由对话气泡展示） -->
    <view v-if="!started && attachments.length" class="attach-bar">
      <view v-for="(a,i) in attachments" :key="a.url" class="attach-chip">
        <view class="attach-chip__ico">
          <template v-if="fileKind(a.name) === 'doc'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/></svg></template>
          <template v-else-if="fileKind(a.name) === 'image'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.4"/><path d="M21 16l-5-5-9 9"/></svg></template>
          <template v-else-if="fileKind(a.name) === 'audio'"><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V6l10-2v12"/><circle cx="6" cy="18" r="3"/><circle cx="16" cy="16" r="3"/></svg></template>
          <template v-else><svg class="attach-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/></svg></template>
        </view>
        <text class="attach-chip__n">{{ a.name }}</text>
        <text class="attach-chip__x" @click="removeAttachment(i)">×</text>
      </view>
    </view>

    <!-- 会话抽屉 -->
    <view v-if="drawerOpen" class="drawer-mask" @click="drawerOpen = false"></view>
    <view class="drawer" :class="{ 'drawer--open': drawerOpen }">
      <view class="drawer__head">
        <text class="drawer__title">对话</text>
        <view class="drawer__close" @click="drawerOpen = false">×</view>
      </view>
      <view class="drawer__new" @click="newSession">
        <text class="drawer__new-ic">＋</text>
        <text>新建对话</text>
      </view>
      <scroll-view scroll-y class="drawer__list">
        <view
          v-for="s in sessions"
          :key="s.sessionId"
          class="drawer__item"
          :class="{ 'drawer__item--on': s.sessionId === sessionId }"
          @click="openSession(s)"
        >
          <text class="drawer__item-t">{{ s.title }}</text>
          <text class="drawer__item-time">{{ s.lastActiveTime }}</text>
        </view>
        <view v-if="!sessions.length" class="drawer__empty">还没有对话记录</view>
      </scroll-view>
      <view class="drawer__foot" @click="goProfile">
        <text>我的偏好</text>
        <text class="drawer__foot-chev">›</text>
      </view>
    </view>

    <!-- 底部输入条（参考豆包：上方引用块 + 输入，下方工具 + 模型选择 + 发送） -->
    <view class="composer">
      <view v-if="quotedText" class="quote-bar">
        <text class="quote-bar__txt">{{ quotedText }}</text>
        <text class="quote-bar__x" @click="quotedText = ''">×</text>
      </view>
      <input v-model="goal" class="composer__input" placeholder="发消息或按住空格说话..." :disabled="running" />
      <view class="composer__row">
        <view class="composer__plus" @click="onPickFiles">＋</view>
        <view class="composer__gap"></view>
        <view class="composer__ring" @click="showCtxMenu = !showCtxMenu">
          <svg class="composer__ctx-ring" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="9" fill="none" stroke="var(--border-color)" stroke-width="3"/>
            <circle cx="12" cy="12" r="9" fill="none" stroke="var(--brand-primary)" stroke-width="3"
                    stroke-linecap="round" transform="rotate(-90 12 12)"
                    :stroke-dasharray="ctxDash" />
          </svg>
        </view>
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
import { refreshTabBarStyle, isDarkMode } from '@/utils/theme'
import MarkdownView from '@/components/MarkdownView.vue'
import { agentRun, uploadAgentFile, cancelAgentRun, listAgentSessions, createAgentSession, listAgentMessages } from '@/api/agent'
import { listUserAiModels } from '@/api/aiModel'
import { pickFiles } from '@/utils/filePicker'
import { copyRaw } from '@/utils/clipboard'
import SseRenderjs from '@/components/sse-renderjs/sse-renderjs.vue'

// 主题是在设置页（非 tabBar 页）切换的，回到本页时同步一次底栏配色
onShow(() => {
  refreshTabBarStyle()
})

const goal = ref('')
/** 已发送的用户消息（输入框发送后会被清空，气泡仍要保留原文） */
const sentGoal = ref('')
/** 引用块：显示在输入框上方，随消息一起发出，可点 × 移除 */
const quotedText = ref('')

/* ==================== 会话 ==================== */
/** 当前会话ID（空 = 尚未开始，首次发送时由后端创建） */
const sessionId = ref('')
/** 最近会话列表 */
const sessions = ref([])
/** 抽屉开关 */
const drawerOpen = ref(false)
/** 历史消息（切换会话时回放） */
const historyMessages = ref([])
/** 历史任务规划展开状态（按消息 seq 记录） */
const histOpen = ref({})

/** 解析历史消息的 meta */
const parseMeta = (hm) => {
  try { return JSON.parse(hm.meta || '{}') } catch (e) { return {} }
}
/** 历史消息里的步骤 */
const histSteps = (hm) => {
  const m = parseMeta(hm)
  return Array.isArray(m.steps) ? m.steps : []
}
/** 历史消息里的附件 */
const histFiles = (hm) => {
  const m = parseMeta(hm)
  return Array.isArray(m.files) ? m.files : []
}

/** 历史回答的复制 / 引用 / 重新生成 */
const copyHistory = (hm) => copyRaw(hm.content || '', '回答')

const quoteHistory = (hm) => {
  const text = String(hm.content || '').replace(/\s+/g, ' ').trim()
  if (!text) return
  quotedText.value = text.length > 300 ? text.slice(0, 300) + '…' : text
}

/** 用历史里对应的问题重跑（仅最后一条回答可用） */
const regenerateFrom = (idx) => {
  if (running.value) return
  let question = ''
  for (let i = idx - 1; i >= 0; i--) {
    if (historyMessages.value[i].role === 'user') { question = historyMessages.value[i].content || ''; break }
  }
  if (!question) {
    uni.showToast({ title: '找不到对应的问题', icon: 'none' })
    return
  }
  sentGoal.value = question
  historyMessages.value = []
  histOpen.value = {}
  runAgent()
}

/** 步骤标题：工具名优先，其次思考文本 */
const histStepTitle = (st) => (st && (st.tool || st.thought)) || '步骤'

/** 上下文占用（后端每轮通过 [上下文:tokens:window] 下发） */
const ctxTokens = ref(0)
/** 上下文浮层开关（点击圆环展开，与模型选择一致） */
const showCtxMenu = ref(false)
const ctxWindow = ref(1000000)
const ctxPercent = computed(() => {
  if (!ctxTokens.value || !ctxWindow.value) return 0
  const p = (ctxTokens.value * 100) / ctxWindow.value
  return p >= 1 ? Math.round(p * 10) / 10 : Math.round(p * 100) / 100
})
/** 圆环进度：dasharray = 可见弧长 空格 周长 */
const RING_CIRCUMFERENCE = 2 * Math.PI * 9
const ctxDash = computed(() => {
  const p = Math.max(0, Math.min(100, ctxPercent.value || 0))
  const arc = (RING_CIRCUMFERENCE * p) / 100
  return arc.toFixed(2) + ' ' + RING_CIRCUMFERENCE.toFixed(2)
})
const ctxWindowLabel = computed(() => {
  const w = ctxWindow.value || 0
  if (w >= 1000000) return (w / 1000000) + 'M'
  if (w >= 1000) return Math.round(w / 1000) + 'k'
  return String(w)
})
const attachments = ref([])

/** 附件类型（决定图标）：doc / image / audio / file */
const fileKind = (name) => {
  const ext = String(name || '').split('.').pop().toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext)) return 'image'
  if (['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg', 'amr', 'opus'].includes(ext)) return 'audio'
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(ext)) return 'doc'
  return 'file'
}
const started = ref(false)
const running = ref(false)

/*
 * 机器人三态：同一张图 + 三种动效，不再切换三张不同渲染的图
 * （不同图的颗粒/光照对不上，轮换会跳戏）。
 */
const ROBOT_HEAD = '/static/ref/robot-head.png'
const ROBOT_BASE = '/static/ref/robot-base.png'
/** 动态形象：仅亮色主题使用（背景已映射为页面底色）；深色主题不展示插画 */
const ROBOT_ANIM_LIGHT = '/static/ref/robot-anim-light.webp'
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

const isRunningStep = (i) => !!(steps.value[i] && steps.value[i].running)
const isDoneStep = (i) => !isRunningStep(i)

const onModelChange = (e) => {
  modelIndex.value = Number(e.detail.value) || 0
}

/** 当前普通 chunk 该落到哪里：工具卡片的结果区 / 答案区 */
let chunkTarget = 'answer'

/** 任务规划是否折叠：运行中展开，完成后自动收起，避免长思考链把答案顶到很下面 */
const planCollapsed = ref(false)

/** 实时计时用的心跳（只有运行中才走，避免无谓渲染） */
const nowTick = ref(Date.now())
let tickTimer = null
const startTick = () => {
  stopTick()
  tickTimer = setInterval(() => { nowTick.value = Date.now() }, 100)
}
const stopTick = () => {
  if (tickTimer) { clearInterval(tickTimer); tickTimer = null }
}

/** 本次任务开始/结束时间（用于总耗时） */
const runStartAt = ref(0)
const runEndAt = ref(0)

/** 毫秒 → 友好时长 */
const fmtMs = (ms) => {
  const n = Math.max(0, Math.round(ms || 0))
  return n >= 1000 ? (n / 1000).toFixed(1) + 's' : n + 'ms'
}

/** 步骤副标题：思考=已完成；工具=执行中（实时）/ 耗时 xxx */
const stepStatusText = (st) => {
  const label = st.type === 'thought' ? '思考中 ' : '执行中 '
  if (st.running) return label + fmtMs(nowTick.value - (st.startAt || nowTick.value))
  if (st.costMs != null) return '耗时 ' + fmtMs(st.costMs)
  return '已完成'
}

/** 总耗时（运行中实时，结束后定格） */
const totalElapsed = computed(() => {
  if (!runStartAt.value) return ''
  if (!running.value && runEndAt.value) return '总耗时 ' + fmtMs(runEndAt.value - runStartAt.value)
  if (!running.value) return ''
  return '总耗时 ' + fmtMs(nowTick.value - runStartAt.value)
})

const pushStep = (step) => {
  steps.value.push(step)
  scrollToBottom()
}

/** 追加流式答案分片 */
const appendAnswer = (text) => {
  if (!text) return
  answer.value += text
  scrollToBottom()
}

/** 流结束：收尾（保证最后一段已落地并滚到底） */
const flushAnswer = () => {
  scrollToBottom()
}

const scrollToBottom = async () => {
  await nextTick()
  // 用 scroll-into-view + 锚点元素（App 端 scroll-top 设大值不可靠）
  scrollAnchor.value = ''
  await nextTick()
  scrollAnchor.value = 'agent-bottom'
}

/** 统一收尾：停心跳、定格总耗时、折叠计划、清理进行中状态 */
const finishRun = () => {
  running.value = false
  steps.value.forEach((st) => { if (st.running) st.running = false })
  if (!runEndAt.value) runEndAt.value = Date.now()
  stopTick()
  if (steps.value.length) planCollapsed.value = true
  // 本轮结束后从服务端同步历史：既保证步骤可回放，也避免下一轮开始时把上一轮顶掉
  syncHistoryAfterRun()
}

/** 运行结束后拉取整段会话消息作为历史，然后清空本轮临时状态 */
const syncHistoryAfterRun = async () => {
  if (!sessionId.value) return
  try {
    const res = await listAgentMessages(sessionId.value, 200)
    const list = (res && res.data) || []
    if (list.length && list[list.length - 1].role === 'assistant') {
      historyMessages.value = list
      // 已经落到历史里了，清掉本轮缓存避免重复渲染
      sentGoal.value = ''
      answer.value = ''
      steps.value = []
      attachments.value = []
      planCollapsed.value = false
    }
  } catch (e) { /* 同步失败就保留本地渲染，不影响使用 */ }
}

/* ==================== 会话操作 ==================== */

const loadSessions = async () => {
  try {
    const res = await listAgentSessions(30)
    sessions.value = (res && res.data) || []
  } catch (e) { /* 未登录/失败：侧栏留空 */ }
}

const goProfile = () => {
  drawerOpen.value = false
  uni.navigateTo({ url: '/pages/agent-profile' })
}

const openDrawer = () => {
  drawerOpen.value = true
  loadSessions()
}

/** 新建对话：清空当前会话与所有对话态 */
const newSession = () => {
  if (running.value) return
  sessionId.value = ''
  historyMessages.value = []
  ctxTokens.value = 0
  drawerOpen.value = false
  reset()
}

/** 打开历史会话：加载消息并进入对话态 */
const openSession = async (s) => {
  if (running.value) return
  drawerOpen.value = false
  sessionId.value = s.sessionId
  drawerOpen.value = false
  histOpen.value = {}
  try {
    const res = await listAgentMessages(s.sessionId, 200)
    historyMessages.value = (res && res.data) || []
  } catch (e) {
    historyMessages.value = []
  }
  // 进入对话态，但清空「本轮」状态（历史消息单独渲染）
  started.value = true
  sentGoal.value = ''
  goal.value = ''
  quotedText.value = ''
  answer.value = ''
  steps.value = []
  errorMsg.value = ''
  scrollToBottom()
}

const onStart = async () => {
  const typed = goal.value.trim()
  if (running.value || (!typed && !quotedText.value)) return
  // 引用块随消息一起发出（拼在用户问题前面，便于模型理解上下文）
  sentGoal.value = quotedText.value
    ? (quotedText.value + (typed ? '\n\n' + typed : ''))
    : typed
  goal.value = ''
  quotedText.value = ''
  return runAgent()
}

/** 用同一条用户消息重跑（重新生成） */
const regenerate = async () => {
  if (running.value || !sentGoal.value) return
  return runAgent()
}

/** 复制最终答案（保留 Markdown 原文） */
const copyAnswer = () => {
  copyRaw(answer.value, '回答')
}

/** 引用答案：在输入框上方生成引用块，随下一条消息一起发送 */
const quoteAnswer = () => {
  const text = String(answer.value || '').replace(/\s+/g, ' ').trim()
  if (!text) return
  quotedText.value = text.length > 300 ? text.slice(0, 300) + '…' : text
}

/** 真正发起一次运行（首次发送与重新生成共用） */
const runAgent = async () => {
  started.value = true
  running.value = true
  errorMsg.value = ''
  answer.value = ''
  steps.value = []
  planCollapsed.value = false
  runStartAt.value = Date.now()
  runEndAt.value = 0
  nowTick.value = Date.now()
  startTick()
  chunkTarget = 'answer'

  try {
    agentRun(
      {
        sessionId: sessionId.value || undefined,
        goal: sentGoal.value,
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
          } else if (kind === '会话') {
            // 后端创建的会话ID：记录下来并刷新侧栏
            if (body && body !== sessionId.value) {
              sessionId.value = body
              historyMessages.value = []
            }
            loadSessions()
          } else if (kind === '上下文') {
            const parts = String(body).split(':')
            const tokens = Number(parts[0]) || 0
            const win = Number(parts[1]) || 0
            ctxTokens.value = tokens
            if (win > 0) ctxWindow.value = win
          } else if (kind === '思考开始') {
            // 模型开始思考：插入进行中的步骤，本地计时
            pushStep({ type: 'thought', text: '正在思考…', running: true, startAt: Date.now() })
          } else if (kind === '思考') {
            chunkTarget = 'answer'
            const last = steps.value[steps.value.length - 1]
            if (last && last.type === 'thought' && last.running) {
              last.text = body
            } else {
              pushStep({ type: 'thought', text: body, running: true, startAt: Date.now() })
            }
          } else if (kind === '思考耗时') {
            // 本步思考结束：写入最终耗时
            const last = steps.value[steps.value.length - 1]
            if (last && last.running) {
              last.running = false
              last.costMs = Number(body) || (Date.now() - (last.startAt || Date.now()))
            }
          } else if (kind === '工具开始') {
            // 工具刚开跑：立刻插入一条进行中的步骤，前端本地计时
            pushStep({ type: 'tool', title: body, result: '', running: true, startAt: Date.now() })
          } else if (kind === '工具') {
            chunkTarget = 'tool'
            // 工具执行结束：把开始帧插入的那条步骤收尾，并写入最终耗时
            const m = /耗时\s*(\d+)\s*ms/.exec(body)
            const last = steps.value[steps.value.length - 1]
            if (last && last.type === 'tool' && last.running) {
              last.running = false
              last.costMs = m ? Number(m[1]) : (Date.now() - (last.startAt || Date.now()))
              last.title = body.replace(/\s*耗时\s*\d+\s*ms\s*$/, '')
            } else {
              pushStep({ type: 'tool', title: body, result: '', running: false, costMs: m ? Number(m[1]) : null })
            }
          } else if (kind === '工具结果结束') {
            chunkTarget = 'answer'
          } else if (kind === '答案') {
            chunkTarget = 'answer'
          } else if (kind === '完成') {
            finishRun()
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
          finishRun()
        },
        onError: (err) => {
          flushAnswer()
          finishRun()
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
  sentGoal.value = ''
  quotedText.value = ''
  attachments.value = []
  runStartAt.value = 0
  runEndAt.value = 0
  stopTick()
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
  height: 136rpx;
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
.welcome--plain { padding-top: 120rpx; }
.welcome__robot {
  /* 亮色主题：动画背景已映射为页面底色，无需卡片；
     深色主题：使用透明 PNG 机器人 */
  width: 360rpx;
  height: 270rpx;
  background: transparent;
}
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
.me-col { display: flex; flex-direction: column; align-items: flex-end; }
.msg--ai { display: block; }

.bubble--me {
  max-width: 76%;
  /* 底色固定浅暖色，文字必须固定深色 —— 用 token 会在深色主题下变成浅字压浅底 */
  background: #FBE3D5;
  color: #3D2B20;
  font-size: 27rpx;
  line-height: 1.6;
  padding: 20rpx 24rpx;
  border-radius: 24rpx 24rpx 6rpx 24rpx;
}
.attach-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 0 32rpx 14rpx;
  flex-shrink: 0;
}
.attach-chip {
  display: flex;
  align-items: center;
  gap: 10rpx;
  max-width: 100%;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: var(--bg-gray);
}
.attach-chip__n {
  font-size: 22rpx;
  color: var(--text-secondary);
  max-width: 380rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.attach-chip__x {
  font-size: 26rpx;
  line-height: 1;
  color: var(--text-tertiary);
}
.attach-chip__ico {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--brand-primary);
}
.attach-ico {
  width: 26rpx;
  height: 26rpx;
}

.files { display: flex; flex-direction: column; gap: 10rpx; align-items: flex-end; }
.file {
  background: var(--bg-card); border-radius: 14rpx; padding: 12rpx 16rpx;
  display: flex; align-items: center; gap: 12rpx; font-size: 24rpx;
}
.file__x { color: $ref-sub; }
.file__ico { display: flex; align-items: center; flex-shrink: 0; color: var(--brand-primary); }

.ai-avatar-wrap {
  position: relative;
  width: 104rpx;
  height: 104rpx;
  flex-shrink: 0;
}
.topbar__avatar {
  display: flex;
  align-items: center;
}
.ai-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  /* 固定浅色底：机器人白色身体 + 深色面罩/键盘在深色主题下都能看清 */
  background: #F4F4F6;
  display: block;
  padding: 4rpx;
  box-sizing: border-box;
}
.ai-badge {
  position: absolute;
  right: -4rpx;
  top: -4rpx;
  width: 42rpx;
  height: 42rpx;
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
.card__title--toggle { display: flex; align-items: center; justify-content: space-between; }
.card__meta { display: flex; align-items: center; gap: 8rpx; }
.card__count { font-size: 22rpx; font-weight: 400; color: $ref-sub; }
.card__count--total { color: $ref-orange; }
.card__chev { width: 28rpx; height: 28rpx; color: $ref-sub; transition: transform 0.2s ease; }
.card__chev--collapsed { transform: rotate(180deg); }
.card--answer { font-size: 27rpx; color: $ref-ink; line-height: 1.7; }
.quote-bar {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  margin-bottom: 14rpx;
  padding: 12rpx 16rpx;
  border-radius: 12rpx;
  background: var(--bg-gray);
  border-left: 6rpx solid var(--brand-primary);
}
.quote-bar__txt {
  flex: 1;
  font-size: 22rpx;
  line-height: 1.5;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.quote-bar__x {
  flex-shrink: 0;
  font-size: 26rpx;
  line-height: 1.2;
  color: var(--text-tertiary);
}
.ans-foot {
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid var(--border-color);
}
.ans-disclaimer {
  display: block;
  font-size: 22rpx;
  line-height: 1.5;
  color: var(--text-tertiary);
}
.ans-acts { display: flex; gap: 16rpx; margin-top: 16rpx; }
.ans-act {
  width: 68rpx;
  height: 68rpx;
  border-radius: 18rpx;
  background: var(--bg-gray);
  display: flex;
  align-items: center;
  justify-content: center;
}
.ans-act:active { opacity: 0.65; }
/* 重新生成固定在最右侧 */
.ans-act--right { margin-left: auto; }
.ans-act svg {
  width: 34rpx;
  height: 34rpx;
  flex-shrink: 0;
  fill: none;
  stroke: var(--brand-primary);
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

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
/* 执行中的步骤：文字亮度呼吸（1 → 0.35 → 1），提示「正在处理」 */
@keyframes step-breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
.tl__t--live {
  animation: step-breathe 2s ease-in-out infinite;
}
/* 历史里的思考文本可能较长，最多展示两行 */
.tl__t--hist {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

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
  height: 64rpx;
  font-size: 29rpx;
  color: $ref-ink;
}
.composer__row {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}
.composer__plus {
  width: 64rpx; height: 64rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 38rpx; color: #4A4A54;
  background: var(--bg-gray);
}
.composer__ring {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 6rpx 0 4rpx;
  flex-shrink: 0;
}
.composer__ctx-ring {
  width: 30rpx;
  height: 30rpx;
  flex-shrink: 0;
}
.composer__gap { flex: 1; }
.composer__model {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 0 12rpx;
  max-width: 260rpx;
}
.composer__model-t {
  font-size: 27rpx;
  color: $ref-ink;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.composer__chev { font-size: 28rpx; color: $ref-sub; }
.composer__send {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: $ref-orange; color: #fff;
  display: flex; align-items: center; justify-content: center;
  margin-left: 12rpx;
}
.send-arrow { font-size: 34rpx; font-weight: 700; line-height: 1; }
.composer__stop {
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: #E14B3B;
  display: flex; align-items: center; justify-content: center;
  margin-left: 12rpx;
}
.stop-square { width: 26rpx; height: 26rpx; border-radius: 5rpx; background: var(--bg-card); }

.ctx-menu {
  position: absolute;
  right: 250rpx;
  bottom: 200rpx;
  z-index: 21;
  min-width: 260rpx;
  background: var(--bg-card);
  border-radius: 20rpx;
  padding: 18rpx 24rpx;
  box-shadow: 0 8rpx 28rpx rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.ctx-menu__t { font-size: 24rpx; color: var(--text-primary); }
.ctx-menu__s { font-size: 20rpx; color: var(--text-tertiary); }
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

/* ==================== 会话抽屉 ==================== */
.topbar__left { display: flex; align-items: center; gap: 14rpx; min-width: 0; }
.topbar__menu {
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14rpx;
}
.topbar__menu:active { background: var(--bg-gray); }
.topbar__menu svg {
  width: 40rpx;
  height: 40rpx;
  fill: none;
  stroke: var(--text-primary);
  stroke-width: 1.8;
  stroke-linecap: round;
}

.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 40;
}
.drawer {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 560rpx;
  background: var(--bg-card);
  z-index: 41;
  transform: translateX(-100%);
  transition: transform 0.25s ease;
  display: flex;
  flex-direction: column;
  box-shadow: 6rpx 0 24rpx rgba(0, 0, 0, 0.12);
}
.drawer--open { transform: translateX(0); }
.drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 40rpx 32rpx 16rpx;
}
.drawer__title { font-size: 30rpx; font-weight: 700; color: var(--text-primary); }
.drawer__close { font-size: 40rpx; line-height: 1; color: var(--text-tertiary); }
.drawer__new {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin: 8rpx 24rpx 16rpx;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background: var(--bg-gray);
  color: var(--brand-primary);
  font-size: 26rpx;
  font-weight: 600;
}
.drawer__new-ic { font-size: 30rpx; line-height: 1; }
.drawer__list { flex: 1; padding: 0 16rpx 32rpx; }
.drawer__item { padding: 20rpx 16rpx; border-radius: 14rpx; margin-bottom: 8rpx; }
.drawer__item--on { background: var(--bg-gray); }
.drawer__item-t {
  display: block;
  font-size: 26rpx;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.drawer__item-time { display: block; margin-top: 6rpx; font-size: 20rpx; color: var(--text-tertiary); }
.drawer__empty { padding: 48rpx 16rpx; text-align: center; font-size: 24rpx; color: var(--text-tertiary); }
.drawer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 24rpx 32rpx;
  padding: 22rpx 24rpx;
  border-radius: 16rpx;
  background: var(--bg-gray);
  font-size: 26rpx;
  color: var(--text-primary);
}
.drawer__foot-chev { color: var(--text-tertiary); }
</style>
