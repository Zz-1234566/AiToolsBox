<template>
  <view class="wf-page">
    <!-- 头部 -->
    <view class="wf-head">
      <text class="wf-head__title">工作流</text>
      <view class="wf-create" @click="goCreate">
        <text>＋ 创建工作流</text>
      </view>
    </view>

    <!-- 列表 -->
    <view v-if="loading" class="redesign-empty"><text class="redesign-empty__text">加载中…</text></view>
    <view v-else-if="workflows.length === 0" class="redesign-empty">
      <view class="redesign-empty__icon"><text class="redesign-empty__emoji">🔗</text></view>
      <text class="redesign-empty__text">还没有创建工作流</text>
      <view class="wf-create" @click="goCreate"><text>去创建</text></view>
    </view>

    <block v-else>
      <view v-for="wf in workflows" :key="wf.workflowId" class="wf-card">
        <view class="wf-card__top">
          <view class="tool-avatar" :class="'tool-avatar--' + firstIcon(wf)">
            <text class="ta-emoji">{{ firstEmoji(wf) }}</text>
          </view>
          <view class="wf-card__main">
            <text class="wf-card__title">{{ wf.name }}</text>
            <text class="wf-card__desc">{{ wf.description || '无描述' }}</text>
            <view class="wf-card__meta">
              <text class="wf-meta">{{ wf.nodeCount }} 个节点</text>
              <text class="wf-meta">{{ wf.maxDepth }} 层</text>
            </view>
            <text class="wf-card__time">创建：{{ wf.createTime }}</text>
          </view>
        </view>
        <view class="wf-card__acts">
          <view class="wf-act wf-act--primary" @click="openRun(wf)">
            <svg class="wf-act__icon" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
            <text>运行</text>
          </view>
          <view class="wf-act__sep"></view>
          <view class="wf-act" @click="viewRuns(wf)">
            <svg class="wf-act__icon" viewBox="0 0 24 24"><path d="M13 3a9 9 0 00-9 9H1l3.89 3.89.07.14L9 12H6a7 7 0 117 7 6.96 6.96 0 01-4.95-2.05l-1.42 1.42A9 9 0 1013 3zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8z"/></svg>
            <text>历史</text>
          </view>
          <view class="wf-act__sep"></view>
          <view class="wf-act" @click="goEdit(wf)">
            <svg class="wf-act__icon" viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>
            <text>编辑</text>
          </view>
          <view class="wf-act__sep"></view>
          <view class="wf-act wf-act--danger" @click="remove(wf)">
            <svg class="wf-act__icon" viewBox="0 0 24 24"><path d="M6 19a2 2 0 002 2h8a2 2 0 002-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
            <text>删除</text>
          </view>
        </view>
      </view>
    </block>

    <!-- ============ 运行弹窗：填源节点输入 ============ -->
    <view v-if="runVisible" class="mask" @click="runVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet__title">运行：{{ runTarget && runTarget.name }}</text>
        <text class="sheet__desc">请为以下起始节点提供输入</text>
        <scroll-view scroll-y class="sheet__body">
          <view v-for="n in sourceNodes" :key="n.nodeId" class="run-node">
            <text class="run-node__name">{{ n.name || n.toolName }}<text class="run-node__tool">（{{ n.toolName }}）</text></text>
            <textarea v-if="isTextInput(n)" class="run-node__text" v-model="runInputs[n.nodeId].text"
                      placeholder="请输入文本内容" placeholder-class="wfe-ph" :maxlength="5000" />
            <view v-else class="run-node__file">
              <view class="file-pick" @click="pickFile(n)">选择文件</view>
              <text class="file-name">{{ runInputs[n.nodeId].files.length ? runInputs[n.nodeId].files.length + ' 个文件' : '未选择' }}</text>
            </view>
          </view>
        </scroll-view>
        <view class="btn-solid" :class="{ 'btn-solid--disabled': running }" @click="running ? null : doRun()">
          {{ running ? '运行中…' : '开始运行' }}
        </view>
      </view>
    </view>

    <!-- ============ 结果弹窗 ============ -->
    <view v-if="resultVisible" class="mask" @click="resultVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet__title">运行结果</text>
        <text class="sheet__desc">
          {{ runResult && runResult.statusLabel }} · 成功 {{ runResult && runResult.successCount }} / 失败 {{ runResult && runResult.failCount }} · {{ runResult && runResult.duration }}ms
        </text>
        <scroll-view scroll-y class="sheet__body">
          <view v-for="(nr, nodeId) in (runResult && runResult.nodeResults) || {}" :key="nodeId" class="res-node">
            <view class="res-node__head">
              <text class="res-node__id">{{ nodeId }}</text>
              <text class="res-node__st" :class="nr.status === 2 ? 'st-ok' : 'st-fail'">{{ nr.status === 2 ? '成功' : '失败' }}</text>
            </view>
            <text v-if="nr.errorMsg" class="res-node__err">{{ nr.errorMsg }}</text>
            <view v-for="(out, oi) in nr.outputs || []" :key="oi" class="res-out">
              <text class="res-out__idx">输出 {{ oi + 1 }}</text>
              <view class="res-out__body">
                <MarkdownView :source="out" :streaming="false" />
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- ============ 历史弹窗 ============ -->
    <view v-if="runsVisible" class="mask" @click="runsVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet__title">运行历史</text>
        <scroll-view scroll-y class="sheet__body">
          <view v-if="runs.length === 0" class="redesign-empty"><text class="redesign-empty__text">暂无运行记录</text></view>
          <view v-for="r in runs" :key="r.runId" class="run-item">
            <view class="run-item__head">
              <text :class="r.status === 2 ? 'st-ok' : (r.status === 3 ? 'st-part' : 'st-fail')">{{ r.statusLabel }}</text>
              <text class="run-item__time">{{ r.createTime }}</text>
            </view>
            <text class="run-item__meta">成功 {{ r.successCount }} / 失败 {{ r.failCount }} · {{ r.duration }}ms</text>
            <text class="run-item__act" @click="openRunDetail(r.runId)">查看详情</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MarkdownView from '@/components/MarkdownView.vue'
import { toolListApi } from '@/api/prompt'
import { uploadFile } from '@/api/request'
import {
  workflowListApi, workflowDetailApi, workflowDeleteApi,
  workflowRunApi, workflowRunsApi, workflowRunDetailApi
} from '@/api/workflow'

const workflows = ref([])
const loading = ref(false)
const tools = ref([])

const runVisible = ref(false)
const running = ref(false)
const runTarget = ref(null)
const runInputs = reactive({})

const resultVisible = ref(false)
const runResult = ref(null)

const runsVisible = ref(false)
const runs = ref([])

const toolByCode = (code) => tools.value.find(t => t.toolCode === code)

/** 卡片头像：取第一个节点的工具类型 */
const firstNode = (wf) => (wf.nodes && wf.nodes[0]) || null
const iconTypeOf = (code) => {
  const c = code || ''
  if (c.includes('ocr') || c.includes('recognize')) return 'ocr'
  if (c.includes('image') || c.includes('photo') || c.includes('qr')) return 'image'
  if (c.includes('doc') || c.includes('file-reader')) return 'doc'
  if (c.includes('audio') || c.includes('transcribe')) return 'audio'
  return 'text'
}
const emojiOf = (type) => ({ ocr: '🖨', image: '🖼️', doc: '📄', audio: '🎤', text: '📝' }[type] || '📝')
const firstIcon = (wf) => iconTypeOf(firstNode(wf) && firstNode(wf).nodeRef)
const firstEmoji = (wf) => emojiOf(firstIcon(wf))

const loadTools = async () => {
  try {
    const res = await toolListApi()
    tools.value = (res && res.data) || []
  } catch (e) { /* ignore */ }
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await workflowListApi()
    workflows.value = (res && res.data) || []
  } catch (e) { /* request.js 已提示 */ } finally {
    loading.value = false
  }
}

onShow(async () => {
  if (!tools.value.length) await loadTools()
  await loadList()
})

const goCreate = () => uni.navigateTo({ url: '/pages/workflow-edit' })
const goEdit = (wf) => uni.navigateTo({ url: `/pages/workflow-edit?workflowId=${wf.workflowId}` })

const remove = (wf) => {
  uni.showModal({
    title: '删除工作流',
    content: `确定删除「${wf.name}」？`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        await workflowDeleteApi(wf.workflowId)
        uni.showToast({ title: '已删除', icon: 'success' })
        await loadList()
      } catch (e) { /* ignore */ }
    }
  })
}

/* ==================== 运行 ==================== */
const sourceNodes = computed(() => {
  if (!runTarget.value) return []
  return (runTarget.value.nodes || []).filter(n => !n.deps || !n.deps.length)
})

const isTextInput = (n) => {
  const t = toolByCode(n.nodeRef)
  if (!t || !t.inputType) return true
  return t.inputType.split(',').map(s => s.trim()).includes('text')
}

const openRun = async (wf) => {
  try {
    const res = await workflowDetailApi(wf.workflowId)
    runTarget.value = res.data
    Object.keys(runInputs).forEach(k => delete runInputs[k])
    ;(res.data.nodes || []).forEach(n => {
      if (!n.deps || !n.deps.length) runInputs[n.nodeId] = { text: '', files: [] }
    })
    runVisible.value = true
  } catch (e) { /* ignore */ }
}

const pickFile = (n) => {
  const t = toolByCode(n.nodeRef)
  const isAudio = t && t.inputType && t.inputType.includes('audio')
  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = false
  input.accept = isAudio ? '.mp3,.wav,.m4a,.aac,.flac,.ogg,.amr' : '.pdf,.docx,.txt'
  input.onchange = async () => {
    const f = (input.files || [])[0]
    if (f) await uploadFiles(n, [f])
  }
  input.click()
  // #endif
  // #ifndef H5
  uni.chooseMessageFile({
    count: 1,
    type: isAudio ? 'audio' : 'file',
    success: async (res) => {
      const f = (res.tempFiles || [])[0]
      if (f) await uploadFiles(n, [f.path || f])
    }
  })
  // #endif
}

const uploadFiles = async (n, list) => {
  if (!list.length) return
  uni.showLoading({ title: '上传中…' })
  try {
    const uploaded = []
    for (const item of list) {
      const up = await uploadFile('/api/file/upload', item, 'file', { prefix: 'file' })
      const url = up && up.data && up.data.fileUrl
      if (url) uploaded.push(url)
    }
    runInputs[n.nodeId].files = uploaded
    uni.showToast({ title: `已上传 ${uploaded.length} 个`, icon: 'success' })
  } catch (e) {
    uni.showToast({ title: '上传失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

const doRun = async () => {
  const inputs = {}
  for (const n of sourceNodes.value) {
    const v = runInputs[n.nodeId]
    if (!v) continue
    if (isTextInput(n)) {
      if (v.text && v.text.trim()) inputs[n.nodeId] = [v.text]
    } else if (v.files && v.files.length) {
      inputs[n.nodeId] = v.files.slice()
    }
  }
  running.value = true
  uni.showLoading({ title: '运行中，请稍候…' })
  try {
    const res = await workflowRunApi(runTarget.value.workflowId, inputs)
    runResult.value = res.data
    runVisible.value = false
    resultVisible.value = true
    await loadList()
  } catch (e) { /* request.js 已提示 */ } finally {
    running.value = false
    uni.hideLoading()
  }
}

/* ==================== 历史 ==================== */
const viewRuns = async (wf) => {
  try {
    const res = await workflowRunsApi(wf.workflowId, 20)
    runs.value = (res && res.data) || []
    runsVisible.value = true
  } catch (e) { /* ignore */ }
}

const openRunDetail = async (runId) => {
  try {
    const res = await workflowRunDetailApi(runId)
    runResult.value = res.data
    runsVisible.value = false
    resultVisible.value = true
  } catch (e) { /* ignore */ }
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.wf-page {
  min-height: 100vh;
  background: #F9FAFB;
  padding-bottom: 40rpx;
}

.wf-head {
  display: flex;
  align-items: center;
  padding: 32rpx 32rpx 24rpx;
}
.wf-head__title {
  flex: 1;
  font-size: 48rpx;
  font-weight: 700;
  color: #111827;
}
.wf-create {
  height: 72rpx;
  padding: 0 28rpx;
  border-radius: 9999rpx;
  background: #3B82F6;
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
}

.wf-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx;
  margin: 0 32rpx 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.wf-card__top { display: flex; gap: 24rpx; }
.wf-card__main { flex: 1; min-width: 0; }
.wf-card__title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}
.wf-card__desc {
  display: block;
  font-size: 26rpx;
  color: #4B5563;
  margin-top: 8rpx;
}
.wf-card__meta { display: flex; gap: 32rpx; margin-top: 12rpx; }
.wf-meta { font-size: 22rpx; color: #9CA3AF; }
.wf-card__time {
  display: block;
  font-size: 22rpx;
  color: #D1D5DB;
  margin-top: 8rpx;
}

.wf-card__acts {
  display: flex;
  align-items: center;
  border-top: 2rpx solid #F3F4F6;
  margin-top: 24rpx;
  padding-top: 24rpx;
}
.wf-act {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 24rpx;
  color: #4B5563;
}
.wf-act__icon {
  width: 30rpx;
  height: 30rpx;
  fill: currentColor;
  flex-shrink: 0;
}
.wf-act--primary { color: #3B82F6; font-weight: 500; }
.wf-act--danger { color: #EF4444; }
.wf-act__sep { width: 2rpx; height: 28rpx; background: #F3F4F6; }

/* 弹层 */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 1000;
}
.sheet {
  width: 100%;
  max-height: 85vh;
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.sheet__title { font-size: 34rpx; font-weight: 700; color: #111827; }
.sheet__desc {
  font-size: 24rpx;
  color: #4B5563;
  margin: 16rpx 0 8rpx;
  background: #F8FAFF;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
}
.sheet__body { flex: 1; margin-top: 16rpx; max-height: 60vh; }

.run-node {
  padding: 24rpx;
  background: #FCFDFF;
  border: 2rpx solid #E5E7EB;
  border-radius: 20rpx;
  margin-bottom: 24rpx;
}
.run-node__name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 16rpx;
}
.run-node__tool { font-size: 22rpx; color: #9CA3AF; font-weight: 400; }
.run-node__text {
  width: 100%;
  min-height: 180rpx;
  border: 2rpx solid #E5E7EB;
  border-radius: 16rpx;
  padding: 20rpx;
  font-size: 26rpx;
  box-sizing: border-box;
  background: #fff;
}
.run-node__file { display: flex; align-items: center; gap: 20rpx; }
.file-pick {
  padding: 16rpx 36rpx;
  background: #EFF6FF;
  color: #2563EB;
  border-radius: 9999rpx;
  font-size: 26rpx;
  font-weight: 500;
}
.file-name { font-size: 24rpx; color: #9CA3AF; }

.res-node { border-bottom: 2rpx solid #F3F4F6; padding: 24rpx 0; }
.res-node__head { display: flex; align-items: center; justify-content: space-between; }
.res-node__id {
  font-size: 26rpx;
  font-weight: 600;
  color: #374151;
  background: #F3F4F6;
  padding: 6rpx 20rpx;
  border-radius: 9999rpx;
}
.res-node__st { font-size: 24rpx; font-weight: 500; }
.res-node__err {
  display: block;
  font-size: 24rpx;
  color: #EF4444;
  margin-top: 12rpx;
  background: #FEF2F2;
  padding: 16rpx;
  border-radius: 12rpx;
}
.res-out { margin-top: 16rpx; }
.res-out__idx { font-size: 22rpx; color: #9CA3AF; }
.res-out__body {
  background: #F9FAFB;
  border-radius: 16rpx;
  padding: 20rpx;
  margin-top: 8rpx;
  border: 2rpx solid #F3F4F6;
}

.run-item {
  padding: 24rpx;
  border: 2rpx solid #F3F4F6;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  background: #FCFDFF;
}
.run-item__head { display: flex; justify-content: space-between; align-items: center; }
.run-item__time { font-size: 22rpx; color: #9CA3AF; }
.run-item__meta { display: block; font-size: 24rpx; color: #4B5563; margin-top: 12rpx; }
.run-item__act {
  display: block;
  font-size: 24rpx;
  color: #3B82F6;
  margin-top: 12rpx;
  text-align: right;
}

.st-ok { color: #10B981; }
.st-part { color: #F59E0B; }
.st-fail { color: #EF4444; }

.safe-bottom { height: 40rpx; }
</style>
