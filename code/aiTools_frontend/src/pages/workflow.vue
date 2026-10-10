<template>
  <view class="wf-page">
    <!-- 头部 -->
    <view class="wf-head">
      <text class="wf-head__title">工作流</text>
      <view class="wf-create" @click="goCreate">
        <text>＋ 创建工作流</text>
      </view>
    </view>

    <!-- 列表：loading 期间**不再卸载列表**，只盖一层遮罩（方案丙）。
         原来 `v-if="loading"` 把整份列表卸载 → 文档高度从 2021px 塌到 814px，
         此时最大滚动只有 124px，restoreScroll 里的 pageScrollTo(1330)
         会被浏览器钳到当时的可滚动上限，于是「恢复」永远停在很靠上的位置。
         保留列表节点 = 恢复期间文档高度不变，滚动位置与时机解耦。 -->
    <view v-if="workflows.length === 0 && !loading" class="redesign-empty">
      <view class="redesign-empty__icon"><text class="redesign-empty__emoji">🔗</text></view>
      <text class="redesign-empty__text">还没有创建工作流</text>
      <view class="wf-create" @click="goCreate"><text>去创建</text></view>
    </view>

    <block v-if="workflows.length > 0">
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

    <!-- 加载遮罩：只盖不卸载，保证列表节点（及其文档高度）在刷新期间始终存在 -->
    <view v-if="loading" class="wf-loading-mask">
      <text class="wf-loading-mask__text">加载中…</text>
    </view>

    <!-- ============ 运行弹窗：填源节点输入 ============ -->
    <view v-if="runVisible" class="mask" @click="runVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet__title">运行：{{ runTarget && runTarget.name }}</text>
        <text class="sheet__desc">请为以下起始节点提供输入</text>

        <scroll-view scroll-y class="sheet__body">
          <view v-for="n in sourceNodes" :key="n.nodeId" class="run-node">
            <view class="run-node__head">
              <!-- 节点图标：后端按工具类型给 icon 标识（file-text / mic / meeting …），前端映射到统一图标 -->
              <view class="run-node__icon"><svg viewBox="0 0 24 24" class="run-node__icon-svg"><path :d="nodeIconPath(n.icon || n.nodeRef)" /></svg></view>
              <view class="run-node__headtext">
                <text class="run-node__name">{{ n.toolName || n.name }}</text>
                <text v-if="n.description" class="run-node__desc">{{ n.description }}</text>
              </view>
            </view>
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

        <!-- 操作按钮：固定在弹层底部，不被内容挤压 -->
        <view class="sheet__footer">
          <view class="btn-solid" @click="doRun">开始运行</view>
        </view>
      </view>
    </view>

    <!-- ============ 历史弹窗 ============ -->
    <view v-if="runsVisible" class="mask" @click="runsVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet__title">运行历史</text>
        <scroll-view scroll-y class="sheet__body">
          <!-- 统计三格：跑过多少次 / 成功率 / 平均耗时 -->
          <view v-if="runs.length" class="run-stats">
            <view class="run-stat">
              <view class="run-stat__v"><text>{{ runs.length }}</text><text class="run-stat__u">次</text></view>
              <text class="run-stat__l">近期运行</text>
            </view>
            <view class="run-stat run-stat--ok">
              <view class="run-stat__v"><text>{{ successRate }}</text><text class="run-stat__u">%</text></view>
              <text class="run-stat__l">成功率</text>
            </view>
            <view class="run-stat">
              <view class="run-stat__v"><text>{{ avgDuration }}</text></view>
              <text class="run-stat__l">平均耗时</text>
            </view>
          </view>

          <!-- 状态筛选：全部 / 成功 / 失败 -->
          <view v-if="runs.length" class="run-seg">
            <view v-for="s in RUN_FILTERS" :key="s.key" class="run-seg__i"
                  :class="{ 'run-seg__i--on': runFilter === s.key }"
                  @click="runFilter = s.key">
              <text>{{ s.text }}</text><text class="run-seg__c">{{ countByFilter(s.key) }}</text>
            </view>
          </view>

          <view v-if="runs.length === 0" class="redesign-empty"><text class="redesign-empty__text">暂无运行记录</text></view>
          <view v-for="r in visibleRuns" :key="r.runId" class="run-item">
            <view class="run-item__head">
              <text class="run-item__name">{{ runTarget && runTarget.name }}</text>
              <text class="run-pill" :class="runPillCls(r)">
                <text v-if="r.status === 1" class="run-item__dot"></text>
                {{ r.statusLabel }}
              </text>
            </view>
            <text class="run-item__meta">
              成功 {{ r.successCount }} / 失败 {{ r.failCount }} · {{ durationText(r.duration) }}
            </text>
            <text class="run-item__time">{{ r.createTime }}</text>
            <text v-if="runFirstErr(r)" class="run-item__err">{{ runFirstErr(r) }}</text>
            <!-- 迷你轨道：不点开也能看出断在哪一步 -->
            <view class="run-mini">
              <block v-for="(s, i) in miniStates(r)" :key="i">
                <view class="run-mini__p" :class="'run-mini__p--' + s"></view>
                <view v-if="i < miniStates(r).length - 1" class="run-mini__l"
                      :class="'run-mini__l--' + miniLinkState(r, i)"></view>
              </block>
              <text class="run-mini__t" :class="{ 'run-mini__t--stop': r.failCount > 0 }">{{ miniLabel(r) }}</text>
            </view>
            <text class="run-item__act" @click="openRunDetail(r.runId)">查看详情</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { onShow, onHide, onUnload, onPageScroll } from '@dcloudio/uni-app'
import { toolListApi } from '@/api/prompt'
import { uploadFile } from '@/api/request'
import {
  workflowListApi, workflowDetailApi, workflowDeleteApi, workflowRunsApi
} from '@/api/workflow'
import { setWorkflowRunTarget } from '@/utils/workflowRunContext'
import {
  nodeIconPath, fileDisplayName, openFile, durationText
} from '@/utils/workflowView'

const workflows = ref([])
const loading = ref(false)
const tools = ref([])

const runVisible = ref(false)
const runTarget = ref(null)
const runInputs = reactive({})

const runsVisible = ref(false)
const runs = ref([])
/** 历史列表状态筛选：all / ok / fail（后端 status 2=成功 3=部分失败 4=失败） */
const runFilter = ref('all')

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

/* ==================== 滚动位置记忆（返回后恢复） ====================
 * 背景：本页列表较长，用户滚到中间某个工作流 → 点「运行/编辑」跳走 → 返回时页面回顶部，
 *      得重新滚回去，体验很糟。
 * 方案选择（为什么不加 <scroll-view>）：
 *   本页没有 sticky 头部、没有下拉刷新、弹层是 position:fixed，
 *   技术上包一层 <scroll-view> 也能做，但必须给固定高度（如 100vh），
 *   而固定高度容器在 H5 下会与浏览器原生滚动/返回栈打架，且嵌套
 *   sheet 里的 <scroll-view class="sheet__body"> 需要额外处理滚动穿透，
 *   收益不抵风险。改用 uni-app 官方 onPageScroll + uni.pageScrollTo，
 *   保持原生页面滚动，零结构改动。
 */

/** 离开本页时记住的滚动位置（px）。仅对 navigateTo 出去的页面生效，返回时恢复。 */
let savedScrollTop = 0
/** 本次离开是不是「跳到二级页」（true）还是「切 tab / 退到后台」（false）。只有前者才恢复。 */
let shouldRestore = false

/** 记录当前滚动位置。onPageScroll 高频触发，这里只做一次赋值，无副作用。 */
onPageScroll((e) => {
  savedScrollTop = e.scrollTop || 0
})

/**
 * 离开本页前打标记 + 记位置。
 * onHide 在 navigateTo 跳二级页和切后台时都会触发，用当前页栈深度区分：
 * 深度 > 1 说明是 push 了一个新页（本页被盖住，返回后会走 onShow）→ 值得恢复；
 * 深度 === 1 说明是切 tab 走了，用户回来期望看到列表顶部 → 不恢复。
 */
const markLeave = () => {
  const stack = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  shouldRestore = stack.length > 1
  if (!shouldRestore) savedScrollTop = 0
}

onHide(markLeave)
onUnload(() => {
  // 页面被销毁（少见）：清标记，避免下次进来误恢复
  shouldRestore = false
})

/**
 * 恢复滚动位置。
 *
 * 现在列表在 loading 期间不会被卸载（方案丙），文档高度在返回全程保持，
 * 所以这里不再依赖「等多久」这种脆弱时序；仍保留一次 nextTick 让 uni 的
 * 异步渲染落定，避免极端情况下（首帧尚未插入）pageScrollTo 打空。
 */
const restoreScroll = async () => {
  const top = savedScrollTop
  if (!top) return
  await nextTick()
  if (typeof uni !== 'undefined' && uni.pageScrollTo) {
    uni.pageScrollTo({ scrollTop: top, duration: 0 })
  }
}

/**
 * 列表数据是否可能已变更（新建/编辑/删除工作流后置 true）。
 * onShow 只在「确实可能变了」时才重拉（方案甲）：
 * 从运行页 / 详情页返回时数据没变，没必要重拉，也就不用进 loading 态，
 * 更不会有列表被短暂替换导致的观感抖动。
 */
let listDirty = true

onShow(async () => {
  // 记住「本次进入是否需要恢复」，在下面 loadList 之前先取，避免异步过程里被覆盖
  const needRestore = shouldRestore
  shouldRestore = false

  if (!tools.value.length) await loadTools()
  // 首次进入必定要拉；之后只有脏了才拉
  if (listDirty || !workflows.value.length) {
    await loadList()
    listDirty = false
  }
  // 从运行页返回时（该跑完了），历史弹层若还开着要重拉，否则看不到刚产生的记录
  if (runsVisible.value && runTarget.value) {
    try {
      const res = await workflowRunsApi(runTarget.value.workflowId, 20)
      runs.value = (res && res.data) || []
    } catch (e) { /* ignore */ }
  }

  // 列表就绪后再恢复滚动；切 tab 进来（needRestore=false）保持顶部
  if (needRestore) await restoreScroll()
});

const goCreate = () => {
  // 创建页返回时列表可能多了一条 → 置脏，下一次 onShow 必定重拉
  listDirty = true
  uni.navigateTo({ url: '/pages/workflow-edit' })
}
const goEdit = (wf) => {
  listDirty = true
  uni.navigateTo({ url: `/pages/workflow-edit?workflowId=${wf.workflowId}` })
}

const remove = (wf) => {
  uni.showModal({
    title: '删除工作流',
    content: `确定删除「${wf.name}」？`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        await workflowDeleteApi(wf.workflowId)
        uni.showToast({ title: '已删除', icon: 'success' })
        listDirty = true
        await loadList()
        listDirty = false
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

/**
 * 「开始运行」：本页只负责收集各源节点输入，收集完跳独立运行页。
 * 真正的调用（同步 / SSE）与实时过程展示都在 workflow-run.vue。
 * inputs 不走 query（文件 URL + 文本长度不可控），走单例传递，见 utils/workflowRunContext.js。
 */
const doRun = () => {
  const inputs = {}
  /** 原始文件名：运行页「输入快照」要显示用户认得的原名，而不是上传后的 URL/UUID */
  const inputFileNames = {}
  for (const n of sourceNodes.value) {
    const v = runInputs[n.nodeId]
    if (!v) continue
    if (isTextInput(n)) {
      if (v.text && v.text.trim()) inputs[n.nodeId] = [v.text]
    } else if (v.files && v.files.length) {
      // 兼容 {name,url} 与纯 url 字符串
      inputs[n.nodeId] = v.files.map(f => (typeof f === 'string' ? f : (f.url || f.fileUrl || '')))
      // 与 inputs 同序同长：后端按下标把原始名对齐回这条输入
      inputFileNames[n.nodeId] = v.files.map(f => (typeof f === 'string' ? fileDisplayName(f) : (f.name || fileDisplayName(f.url || f.fileUrl || ''))))
    }
  }
  if (!Object.keys(inputs).length) {
    uni.showToast({ title: '请先为起始节点填写输入', icon: 'none' })
    return
  }
  setWorkflowRunTarget({
    workflowId: runTarget.value.workflowId,
    name: runTarget.value.name,
    description: runTarget.value.description,
    inputs,
    // ⚠️ 字段名必须与后端 WorkflowRunRequest.inputFileNames 严格一致。
    // 不一致时 Jackson 会「静默忽略」未知字段：不报错，但该值永远是 null，
    // 表现为历史回放里文件名一直是 COS 的 uuid 名，且没有任何错误提示。
    inputFileNames
  })
  runVisible.value = false
  // 运行页不修改工作流定义本身（只有运行记录变了，列表卡片不展示记录数），
  // 因此这里**不置脏**：返回时跳过重拉，滚动恢复不被任何 loading 态打断。
  uni.navigateTo({ url: `/pages/workflow-run?mode=run&workflowId=${runTarget.value.workflowId}` })
}

/* ==================== 历史 ==================== */
const viewRuns = async (wf) => {
  try {
    const res = await workflowRunsApi(wf.workflowId, 20)
    runs.value = (res && res.data) || []
    runFilter.value = 'all'
    runsVisible.value = true
  } catch (e) { /* ignore */ }
}

/**
 * 「查看详情」：跳独立运行页的 detail 模式。
 * 节点真名（避免退化成 n1/n2）的补拉逻辑已搬到 workflow-run.vue：
 * 运行页拿到 runDetail.workflowId 后自己 workflowDetailApi 补一次，本页不再需要。
 */
const openRunDetail = (runId) => {
  runsVisible.value = false
  uni.navigateTo({ url: `/pages/workflow-run?mode=detail&runId=${runId}` })
}

/* ==================== 历史列表：统计 / 筛选 / 迷你轨道 ==================== */
const RUN_FILTERS = [
  { key: 'all', text: '全部' },
  { key: 'ok', text: '成功' },
  { key: 'fail', text: '失败' }
]

/** 后端 status：1 运行中 2 成功 3 部分失败 4 失败 */
const isOkRun = (r) => r.status === 2 || r.status === 3
const countByFilter = (k) => (k === 'ok' ? runs.value.filter(isOkRun).length : k === 'fail' ? runs.value.filter(r => !isOkRun(r)).length : runs.value.length)
const visibleRuns = computed(() => runs.value.filter(r => runFilter.value === 'all' || (runFilter.value === 'ok' ? isOkRun(r) : !isOkRun(r))))

const successRate = computed(() => {
  if (!runs.value.length) return 0
  return Math.round((runs.value.filter(isOkRun).length / runs.value.length) * 100)
})
const avgDuration = computed(() => {
  if (!runs.value.length) return '0秒'
  return durationText(Math.round(runs.value.reduce((a, r) => a + (r.duration || 0), 0) / runs.value.length))
})

const runPillCls = (r) => (r.status === 2 || r.status === 3 ? 'run-pill--ok' : r.status === 1 ? 'run-pill--run' : 'run-pill--fail')

/** 迷你轨道节点数：取该次运行 nodeResults 的节点总数（无结果时退回 1，避免空轨道） */
const miniCount = (r) => {
  const n = r.nodeResults ? Object.keys(r.nodeResults).length : 0
  return n || 1
}
/** 迷你轨道状态：done / fail / blk（failCount 之后一律按「断点后未执行」画灰虚线） */
const miniStates = (r) => {
  const total = miniCount(r)
  const out = []
  const nr = r.nodeResults || {}
  const ids = Object.keys(nr)
  for (let i = 0; i < total; i++) {
    const id = ids[i]
    const node = id ? nr[id] : null
    if (node && node.status === 2) out.push('done')
    else if (node && node.status === 4 && node.errorMsg !== '上游节点失败，本节点未执行') out.push('fail')
    else if (r.status === 1 && out.length === doneCountOf(r)) out.push('run')
    else out.push('blk')
  }
  return out
}
const doneCountOf = (r) => {
  const nr = r.nodeResults || {}
  return Object.keys(nr).filter(k => nr[k].status === 2).length
}
const miniLinkState = (r, i) => {
  const st = miniStates(r)
  if (st[i] === 'fail') return 'fail'
  if (st[i] === 'done' && st[i + 1] === 'run') return 'run'
  if (st[i] === 'done' && st[i + 1] === 'done') return 'done'
  if (st[i] === 'done') return 'blk'
  return 'idle'
}
const miniLabel = (r) => {
  const st = miniStates(r)
  const failAt = st.indexOf('fail')
  if (failAt >= 0) return `断在第 ${failAt + 1} 步`
  if (r.status === 1) {
    const runAt = st.indexOf('run')
    return runAt >= 0 ? `第 ${runAt + 1} 步进行中` : '进行中'
  }
  return `${st.filter(x => x === 'done').length}/${st.length} 节点`
}
/** 失败记录在列表里直接摊出断点原因（node_results 里第一个真失败的 errorMsg） */
const runFirstErr = (r) => {
  const nr = r.nodeResults || {}
  const hit = Object.keys(nr).find(k => nr[k].status === 4 && nr[k].errorMsg && nr[k].errorMsg !== '上游节点失败，本节点未执行')
  if (!hit) return ''
  return `${hit} · ${nr[hit].errorMsg}`
}

</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';
@import '@/styles/animations.scss';

.wf-page {
  min-height: 100vh;
  background: #F9FAFB;
  padding-bottom: 40rpx;
  /* 遮罩定位基准：让 wf-loading-mask 能覆盖整页 */
  position: relative;
}

/* 加载遮罩（方案丙）：绝对定位覆盖，不参与文档流，
   因此「盖住列表」不会改变文档高度 —— 滚动恢复不再受刷新时机影响。 */
.wf-loading-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(249, 250, 251, 0.72);
}
.wf-loading-mask__text { font-size: 26rpx; color: var(--text-tertiary, #9CA3AF); }

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
}
.run-node__tool { font-size: 22rpx; color: #9CA3AF; font-weight: 400; }
/* 节点卡头：图标 + 名称 + 工具描述 */
.run-node__head {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
}
.run-node__icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: #EFF6FF;
  color: #2563EB;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.run-node__icon-svg { width: 36rpx; height: 36rpx; fill: currentColor; }
.run-node__headtext { flex: 1; min-width: 0; }
.run-node__desc {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #9CA3AF;
  line-height: 1.4;
}
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

/* 历史记录项：迷你轨道 + 状态 pill（对齐设计稿历史运行页） */
.run-stats { display: flex; gap: 16rpx; margin-bottom: 24rpx; }
.run-stat {
  flex: 1;
  background: #fff;
  border-radius: 24rpx;
  padding: 22rpx 20rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  border: 2rpx solid rgba(17, 24, 39, 0.04);
  text-align: center;
}
.run-stat__v {
  font-size: 34rpx;
  font-weight: 700;
  line-height: 44rpx;
  color: #111827;
  font-variant-numeric: tabular-nums;
}
.run-stat--ok .run-stat__v { color: #047857; }
.run-stat__u { font-size: 22rpx; font-weight: 600; color: #9CA3AF; margin-left: 2rpx; }
.run-stat__l { display: block; font-size: 21rpx; color: #9CA3AF; margin-top: 6rpx; }

.run-seg {
  display: flex;
  gap: 4rpx;
  padding: 6rpx;
  background: #F3F4F6;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
}
.run-seg__i {
  flex: 1;
  height: 60rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 25rpx;
  color: #4B5563;
}
.run-seg__i--on { background: #fff; color: #3B82F6; font-weight: 600; box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06); }
.run-seg__c { font-size: 21rpx; font-weight: 600; opacity: 0.6; }

.run-item {
  padding: 24rpx;
  border: 2rpx solid #F3F4F6;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  background: #FCFDFF;
  position: relative;
}
.run-item__head { display: flex; align-items: center; gap: 16rpx; }
.run-item__name {
  flex: 1;
  min-width: 0;
  font-size: 27rpx;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.run-pill {
  height: 40rpx;
  padding: 0 14rpx;
  border-radius: 8rpx;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  font-weight: 600;
  flex-shrink: 0;
}
.run-pill--ok { background: #D1FAE5; color: #047857; }
.run-pill--run { background: #EFF6FF; color: #3B82F6; }
.run-pill--fail { background: #FEE2E2; color: #DC2626; }
.run-item__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #3B82F6;
  animation: breathe 1.5s ease-in-out infinite;
}
.run-item__time { display: block; font-size: 22rpx; color: #9CA3AF; margin-top: 6rpx; }
.run-item__meta {
  display: block;
  font-size: 24rpx;
  color: #111827;
  font-weight: 500;
  margin-top: 8rpx;
}
.run-item__err {
  display: block;
  margin-top: 14rpx;
  padding: 10rpx 14rpx;
  border-radius: 8rpx;
  background: #FEE2E2;
  font-size: 21rpx;
  line-height: 30rpx;
  color: #B91C1C;
  word-break: break-all;
}
.run-item__act {
  display: block;
  font-size: 24rpx;
  color: #3B82F6;
  margin-top: 12rpx;
  text-align: right;
}

/* 迷你轨道：五点进度，不点开也能看出断在哪一步 */
.run-mini { display: flex; align-items: center; margin-top: 18rpx; }
.run-mini__p {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: #F3F4F6;
}
.run-mini__p--done { background: #10B981; }
.run-mini__p--fail { background: #EF4444; }
.run-mini__p--run { background: #3B82F6; }
.run-mini__l { flex: 1; height: 3rpx; background: #F3F4F6; }
.run-mini__l--done { background: #10B981; }
.run-mini__l--fail { background: #EF4444; }
.run-mini__l--run { background: linear-gradient(90deg, #10B981, #3B82F6); }
.run-mini__l--blk { background: repeating-linear-gradient(90deg, #D1D5DB 0 4rpx, transparent 4rpx 10rpx); }
.run-mini__t {
  margin-left: auto;
  padding-left: 16rpx;
  font-size: 20rpx;
  color: #9CA3AF;
  white-space: nowrap;
}
.run-mini__t--stop { color: #DC2626; font-weight: 600; }

.safe-bottom { height: 40rpx; }
</style>
