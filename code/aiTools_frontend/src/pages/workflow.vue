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
        <text v-if="!streamFrames.length && !streamRunning" class="sheet__desc">请为以下起始节点提供输入</text>

        <!-- 未运行时：输入区 -->
        <scroll-view v-if="!streamFrames.length && !streamRunning" scroll-y class="sheet__body">
          <view v-for="n in sourceNodes" :key="n.nodeId" class="run-node">
            <text class="run-node__name">{{ n.toolName || n.name }}</text>
            <textarea v-if="isTextInput(n)" class="run-node__text" v-model="runInputs[n.nodeId].text"
                      placeholder="请输入文本内容" placeholder-class="wfe-ph" :maxlength="5000" />
            <view v-else class="run-node__file">
              <view class="file-pick" @click="pickFile(n)">选择文件</view>
              <text v-if="!runInputs[n.nodeId].files.length" class="file-name">未选择</text>
              <!-- 已选文件：显示文件名，点击可下载/预览
                   files 元素可能是 {name,url} 对象（新）或纯 url 字符串（兼容） -->
              <view v-else class="run-files">
                <view v-for="(f, fi) in runInputs[n.nodeId].files" :key="fi" class="run-file-item"
                      @click.stop="openFile(f)">
                  <text class="run-file-item__icon">📎</text>
                  <text class="run-file-item__name">{{ fileDisplayName(f) }}</text>
                </view>
              </view>
            </view>
          </view>
        </scroll-view>

        <!-- 运行中/已完成：进度区（占满剩余空间，内部滚动） -->
        <scroll-view v-else scroll-y class="stream-panel" :scroll-top="streamScrollTop">
          <view class="stream-panel__head">
            <text class="stream-panel__title">执行进度</text>
            <text class="stream-panel__sub">{{ streamDoneCount }}/{{ streamFileTotal }} 个文件</text>
          </view>
          <view v-for="(fr, fi) in streamFrames" :key="fi" class="stream-file">
            <view class="stream-file__head">
              <!-- 标题：文件名（有则显示文件名，无则「文件 N」） -->
              <text class="stream-file__idx">{{ fr.fileName || ('文件 ' + (fi + 1)) }}</text>
              <text class="stream-file__st" :class="fr.ok ? 'st-ok' : (fr.failed ? 'st-fail' : 'st-run')">
                {{ fr.ok ? '✓ 完成' : (fr.failed ? '✗ 失败' : '处理中…') }}
              </text>
            </view>
            <!-- 每个节点：标题用「转写结果 / 会议纪要结果」等可读名 -->
            <view v-for="(nd, ni) in fr.nodes" :key="ni" class="stream-node">
              <text class="stream-node__name">{{ nd.title || nd.nodeRef || nd.nodeId }}</text>
              <text v-if="nd.errorMsg" class="stream-node__err">{{ nd.errorMsg }}</text>
              <view v-else-if="nd.text" class="stream-node__text">
                <MarkdownView :source="nd.text" :streaming="!nd.done" />
              </view>
              <text v-else-if="!nd.done" class="stream-node__wait">处理中…</text>
            </view>
          </view>
        </scroll-view>

        <!-- 操作按钮：固定在弹层底部，不被内容挤压 -->
        <view class="sheet__footer">
          <view class="btn-solid" :class="{ 'btn-solid--disabled': running }" @click="running ? null : doRun()">
            {{ running ? '运行中…' : (streamFrames.length ? '再次运行' : '开始运行') }}
          </view>
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
import { BASE_URL } from '@/config/env'
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

/* ===== 流式执行状态（SSE 边跑边展示）===== */
/** 每个文件的执行进度：[{ ok, nodes: [{nodeId, nodeRef, text, done, errorMsg}] }] */
const streamFrames = ref([])
const streamRunning = ref(false)
const streamFileTotal = ref(0)
const streamDoneCount = ref(0)
/** 流式面板滚动位置（自动滚到底部，让用户看到最新进度） */
const streamScrollTop = ref(0)

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
  // 多选：工作流节点是数组语义（N 个文件 → N 个独立输入），支持一次选多个
  input.multiple = true
  input.accept = isAudio ? '.mp3,.wav,.m4a,.aac,.flac,.ogg,.amr' : '.pdf,.docx,.txt'
  input.onchange = async () => {
    const list = Array.from(input.files || [])
    if (list.length) await uploadFiles(n, list)
  }
  input.click()
  // #endif
  // #ifndef H5
  uni.chooseMessageFile({
    count: 10,
    type: isAudio ? 'audio' : 'file',
    success: async (res) => {
      const list = (res.tempFiles || []).map(f => f.path || f)
      if (list.length) await uploadFiles(n, list)
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
      const res = await uploadFile('/api/file/upload', item, 'file', { prefix: 'file' })
      // 后端约定 code=200 成功；其余情况携带用户可读的 message（如"不支持的文件类型…"）
      const ok = res && res.code === 200
      if (!ok) {
        // 直接展示后端给的提示（后端已保证是可读文案，不含技术细节）
        uni.showToast({ title: (res && res.message) || '上传失败，请重试', icon: 'none', duration: 3000 })
        return
      }
      const url = res.data && res.data.fileUrl
      const name = (res.data && res.data.fileName) || (item && item.name) || fileDisplayName(url)
      if (url) uploaded.push({ name, url })
    }
    // 累加：支持重复点选/多次选择，避免后一次覆盖前一次（工作流节点是数组语义）
    const existing = (runInputs[n.nodeId].files || []).slice()
    runInputs[n.nodeId].files = existing.concat(uploaded)
    uni.showToast({ title: `已上传 ${uploaded.length} 个，共 ${runInputs[n.nodeId].files.length} 个`, icon: 'success' })
  } catch (e) {
    // 网络异常等：request 层已提示，此处兜底
    uni.showToast({ title: '上传失败，请检查网络后重试', icon: 'none' })
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
      // 兼容 {name,url} 与纯 url 字符串
      inputs[n.nodeId] = v.files.map(f => (typeof f === 'string' ? f : (f.url || f.fileUrl || '')))
    }
  }

  // 文件数 > 1 → SSE 流式（边跑边展示）；单文件/纯文本 → 原同步接口
  const maxFiles = Math.max(0, ...Object.values(inputs).map(a => a.length))
  if (maxFiles > 1) {
    return doRunStream(inputs, maxFiles)
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

/**
 * SSE 流式运行：逐帧接收，边跑边展示（每个文件完整跑完后立即可见）
 * 帧类型：run_start / file_start / node_start / chunk / node_done / file_done / all_done / error
 */
const doRunStream = (inputs, fileTotal) => {
  const token = uni.getStorageSync('token')
  const url = BASE_URL + `/api/workflow/${runTarget.value.workflowId}/run/stream`

  // 重置流式状态
  streamFrames.value = []
  streamFileTotal.value = fileTotal
  streamDoneCount.value = 0
  streamRunning.value = true
  running.value = true

  // 每个文件的显示名（按源节点输入顺序，取用户上传时的文件名）
  const fileNames = []
  const srcNode = sourceNodes.value[0]
  if (srcNode && runInputs[srcNode.nodeId] && runInputs[srcNode.nodeId].files) {
    runInputs[srcNode.nodeId].files.forEach(f => fileNames.push(fileDisplayName(f)))
  }

  const xhr = new XMLHttpRequest()
  xhr.open('POST', url, true)
  xhr.setRequestHeader('Content-Type', 'application/json')
  if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token)

  // SSE 增量解析（按空行分隔事件，事件内多行 data: 拼回）
  let consumed = 0
  let buffer = ''
  const handleFrame = (obj) => {
    const t = obj.type
    if (t === 'file_start') {
      streamFrames.value.push({
        fileName: fileNames[obj.fileIndex] || ('文件 ' + (obj.fileIndex + 1)),
        ok: false, failed: false, nodes: []
      })
    } else if (t === 'node_start') {
      const f = streamFrames.value[obj.fileIndex]
      if (f) {
        f.nodes.push({
          nodeId: obj.nodeId,
          nodeRef: obj.nodeRef,
          // 可读标题：优先用工具名（如「录音转写」「会议纪要」），后端未传则退回 nodeRef
          title: nodeTitleOf(obj.nodeRef, obj.title),
          text: '', done: false, errorMsg: null
        })
        autoScrollStream()
      }
    } else if (t === 'chunk') {
      const f = streamFrames.value[obj.fileIndex]
      const nd = f && f.nodes.find(x => x.nodeId === obj.nodeId)
      if (nd) nd.text = (nd.text || '') + (obj.text || '')
    } else if (t === 'node_done') {
      const f = streamFrames.value[obj.fileIndex]
      const nd = f && f.nodes.find(x => x.nodeId === obj.nodeId)
      if (nd) {
        nd.done = true
        if (!obj.ok) nd.errorMsg = obj.errMsg || '执行失败'
        // 非 text 节点通过 output 带回完整内容（text 节点已由 chunk 累积）
        if (obj.output) nd.text = (nd.text || '') + obj.output
      }
      autoScrollStream()
    } else if (t === 'file_done') {
      const f = streamFrames.value[obj.fileIndex]
      if (f) {
        f.ok = !!obj.ok
        f.failed = !obj.ok
      }
      streamDoneCount.value = Math.max(streamDoneCount.value, obj.fileIndex + 1)
      autoScrollStream()
    } else if (t === 'all_done') {
      streamRunning.value = false
      running.value = false
      streamFrames.value = streamFrames.value.slice()
      autoScrollStream()
    } else if (t === 'error') {
      streamRunning.value = false
      running.value = false
      uni.showToast({ title: obj.message || '运行失败', icon: 'none', duration: 3000 })
    }
  }

  /** 节点可读标题：用工具名（来自工具列表），查不到则退回 nodeRef */
  const nodeTitleOf = (nodeRef, backendTitle) => {
    if (backendTitle) return backendTitle
    const t = tools.value.find(x => x.toolCode === nodeRef)
    return (t && t.toolName) || nodeRef || '节点'
  }

  /** 内容增长后自动滚到底部（让用户始终看到最新进度） */
  const autoScrollStream = () => {
    // 用极大值触发滚动到底；下一帧再重置，避免 scroll-top 相同值不触发
    streamScrollTop.value = 999999
    setTimeout(() => { streamScrollTop.value = 0 }, 60)
  }

  const feed = (fullText, done) => {
    buffer += fullText.slice(consumed)
    consumed = fullText.length
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop()
    for (const ev of events) {
      const lines = ev.split(/\r?\n/).filter(l => l.startsWith('data:'))
      if (!lines.length) continue
      const payload = lines.map(l => l.slice(5).replace(/^ /, '')).join('\n')
      if (!payload || payload === '[DONE]') continue
      try { handleFrame(JSON.parse(payload)) } catch (e) { /* 忽略非 JSON 帧 */ }
    }
    if (done && buffer) {
      const lines = buffer.split(/\r?\n/).filter(l => l.startsWith('data:'))
      if (lines.length) {
        const payload = lines.map(l => l.slice(5).replace(/^ /, '')).join('\n')
        try { handleFrame(JSON.parse(payload)) } catch (e) { /* ignore */ }
      }
      buffer = ''
    }
  }

  xhr.onprogress = () => feed(xhr.responseText)
  xhr.onload = () => {
    if (xhr.status >= 400) {
      streamRunning.value = false
      running.value = false
      let msg = '请求失败（' + xhr.status + '）'
      try {
        const body = JSON.parse(xhr.responseText || '{}')
        if (body && body.message) msg = body.message
      } catch (e) { /* ignore */ }
      uni.showToast({ title: msg, icon: 'none', duration: 3000 })
      return
    }
    feed(xhr.responseText, true)
    streamRunning.value = false
    running.value = false
    loadList()
  }
  xhr.onerror = () => {
    streamRunning.value = false
    running.value = false
    uni.showToast({ title: '网络异常，运行中断', icon: 'none' })
  }
  xhr.send(JSON.stringify({ inputs }))
}

/** 文件显示名：兼容 {name,url} 与纯 url 字符串 */
const fileDisplayName = (f) => {
  if (!f) return '文件'
  if (typeof f === 'string') {
    const s = f.split('?')[0]
    const seg = s.split('/').pop() || '文件'
    const cut = seg.indexOf('_')   // 形如 8eb03bf1_文件名.pdf
    return cut > 0 && cut < 40 ? seg.slice(cut + 1) : seg
  }
  return f.name || f.fileName || '文件'
}

/** 点击文件：跳下载（新窗口打开签名 URL） */
const openFile = (f) => {
  const url = typeof f === 'string' ? f : (f && (f.url || f.fileUrl))
  if (!url) {
    uni.showToast({ title: '文件链接不可用', icon: 'none' })
    return
  }
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200) uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
    },
    fail: () => uni.showToast({ title: '下载失败', icon: 'none' })
  })
  // #endif
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

/* 已选文件列表（工作流节点上传后显示文件名，点击下载） */
.run-files { margin-top: 12rpx; }
.run-file-item {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 14rpx 16rpx;
  margin-top: 8rpx;
  background: #F9FAFB;
  border-radius: 12rpx;
}
.run-file-item__icon { font-size: 26rpx; }
.run-file-item__name {
  flex: 1;
  font-size: 24rpx;
  color: #2563EB;
  text-decoration: underline;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 流式执行进度面板：占满剩余空间并内部滚动（不再固定 max-height，避免挤压按钮） */
.stream-panel {
  flex: 1;
  min-height: 0;
  margin-top: 20rpx;
  padding: 20rpx;
  background: #F9FAFB;
  border-radius: 16rpx;
  box-sizing: border-box;
}

/* 底部操作按钮：固定在弹层底部，不随内容滚动/被挤压 */
.sheet__footer {
  flex-shrink: 0;
  padding-top: 20rpx;
}
.stream-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.stream-panel__title { font-size: 28rpx; font-weight: 600; color: #111827; }
.stream-panel__sub { font-size: 24rpx; color: #6B7280; }

.stream-file {
  background: #fff;
  border-radius: 14rpx;
  padding: 18rpx;
  margin-bottom: 14rpx;
}
.stream-file__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10rpx;
}
.stream-file__idx { font-size: 26rpx; font-weight: 600; color: #111827; }
.stream-file__st { font-size: 22rpx; }
.st-ok { color: #10B981; }
.st-fail { color: #EF4444; }
.st-run { color: #6B7280; }

.stream-node { margin-top: 12rpx; padding-left: 8rpx; border-left: 4rpx solid #E5E7EB; }
.stream-node__name { display: block; font-size: 22rpx; color: #6B7280; margin-bottom: 6rpx; }
.stream-node__err { display: block; font-size: 24rpx; color: #EF4444; }
.stream-node__wait { display: block; font-size: 24rpx; color: #9CA3AF; }
.stream-node__text { font-size: 26rpx; color: #374151; }

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
