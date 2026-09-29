<template>
  <view class="page page--no-tabbar">
    <!-- 顶栏 -->
    <view class="topbar">
      <text class="topbar__title">工作流</text>
      <view class="topbar__spacer"></view>
      <view v-if="!editing" class="btn-create" @click="startCreate">
        <text class="btn-create__label">创建工作流</text>
      </view>
      <view v-else class="btn-create" @click="cancelEdit">
        <text class="btn-create__label">取消</text>
      </view>
    </view>

    <scroll-view scroll-y class="page-content">
      <!-- ============ 编辑态：节点编排 ============ -->
      <block v-if="editing">
        <view class="card">
          <input class="input-name" v-model="form.name" placeholder="工作流名称，如：会议纪要生成流" :maxlength="64" />
          <input class="input-desc" v-model="form.description" placeholder="描述（可选）" :maxlength="255" />

          <view class="section-label">节点（按依赖顺序自动分层，最多 5 层）</view>

          <view v-for="(node, idx) in form.nodes" :key="idx" class="node-edit">
            <view class="node-edit__head">
              <text class="node-edit__idx">{{ idx + 1 }}</text>
              <picker class="node-edit__picker" :range="toolNames" :value="toolIndexOf(node.nodeRef)"
                      @change="(e) => onToolChange(idx, e)">
                <view class="picker-value">{{ toolName(node.nodeRef) || '选择工具' }}</view>
              </picker>
              <view class="node-edit__del" @click="removeNode(idx)">删除</view>
            </view>
            <view class="node-edit__types" v-if="toolByCode(node.nodeRef)">
              <text class="type-tag">输入：{{ toolByCode(node.nodeRef).inputType || '—' }}</text>
              <text class="type-tag">输出：{{ toolByCode(node.nodeRef).outputType || '—' }}</text>
            </view>
            <!-- 依赖选择：多选上游节点 -->
            <view class="node-edit__deps">
              <text class="deps-label">上游依赖（不选=源节点，运行时需填输入）</text>
              <view class="deps-list">
                <view v-for="(other, oi) in form.nodes" :key="oi"
                      v-if="oi !== idx"
                      class="dep-chip" :class="{ 'dep-chip--on': node.deps.includes(nodeIdOf(oi)) }"
                      @click="toggleDep(idx, oi)">
                  {{ nodeIdOf(oi) }}
                </view>
              </view>
            </view>
          </view>

          <view class="btn-add-node" @click="addNode">+ 添加节点</view>
          <view class="edit-hint" v-if="form.nodes.length">
            当前 {{ form.nodes.length }} 个节点，预计 {{ previewDepth }} 层
          </view>

          <view class="btn btn--primary" :disabled="saving" @click="save">保存工作流</view>
        </view>
      </block>

      <!-- ============ 列表态 ============ -->
      <block v-else>
        <view v-if="listLoading" class="empty">加载中…</view>
        <view v-else-if="workflows.length === 0" class="empty">
          还没有工作流，点右上角「创建工作流」开始
        </view>
        <view v-for="wf in workflows" :key="wf.workflowId" class="card wf-card">
          <view class="wf-card__head">
            <text class="wf-card__title">{{ wf.name }}</text>
            <text v-if="wf.lastRunStatus !== null && wf.lastRunStatus !== undefined"
                  class="wf-card__status" :class="'st-' + wf.lastRunStatus">
              {{ runStatusLabel(wf.lastRunStatus) }}
            </text>
          </view>
          <view class="wf-card__desc">{{ wf.description || '无描述' }}</view>
          <view class="wf-card__meta">{{ wf.nodeCount }} 个节点 · {{ wf.maxDepth }} 层 · {{ wf.createTime }}</view>
          <view class="wf-card__actions">
            <view class="btn btn--primary btn--sm" @click="openRun(wf)">运行</view>
            <view class="btn btn--ghost btn--sm" @click="viewRuns(wf)">历史</view>
            <view class="btn btn--ghost btn--sm" @click="startEdit(wf)">编辑</view>
            <view class="btn btn--ghost btn--sm" @click="remove(wf)">删除</view>
          </view>
        </view>
      </block>
    </scroll-view>

    <!-- ============ 运行弹窗：填源节点输入 ============ -->
    <view v-if="runVisible" class="mask" @click="runVisible = false">
      <view class="sheet" @click.stop>
        <view class="sheet__title">运行：{{ runTarget && runTarget.name }}</view>
        <view class="sheet__desc">请为以下起始节点提供输入（文件已上传或文本已填写）</view>
        <scroll-view scroll-y class="sheet__body">
          <view v-for="n in sourceNodes" :key="n.nodeId" class="run-node">
            <view class="run-node__name">{{ n.name }} <text class="run-node__tool">({{ toolName(n.nodeRef) }})</text></view>
            <!-- 文本类：textarea -->
            <textarea v-if="isTextInput(n)" class="run-node__text" v-model="runInputs[n.nodeId].text"
                      placeholder="请输入文本内容" :maxlength="5000" />
            <!-- 文件类：选择文件 -->
            <view v-else class="run-node__file">
              <view class="file-pick" @click="pickFile(n)">选择文件</view>
              <text class="file-name">{{ runInputs[n.nodeId].files.length ? runInputs[n.nodeId].files.length + ' 个文件' : '未选择' }}</text>
            </view>
          </view>
        </scroll-view>
        <view class="btn btn--primary" :disabled="running" @click="doRun">
          {{ running ? '运行中…' : '开始运行' }}
        </view>
      </view>
    </view>

    <!-- ============ 结果弹窗 ============ -->
    <view v-if="resultVisible" class="mask" @click="resultVisible = false">
      <view class="sheet" @click.stop>
        <view class="sheet__title">运行结果</view>
        <view class="sheet__desc">
          <text :class="'st-' + (runResult && runResult.status)">{{ runResult && runResult.statusLabel }}</text>
          · 成功 {{ runResult && runResult.successCount }} / 失败 {{ runResult && runResult.failCount }}
          · {{ runResult && runResult.duration }}ms · {{ runResult && runResult.maxDepth }} 层
        </view>
        <scroll-view scroll-y class="sheet__body">
          <view v-for="(nr, nodeId) in (runResult && runResult.nodeResults) || {}" :key="nodeId" class="res-node">
            <view class="res-node__head">
              <text class="res-node__id">{{ nodeId }}</text>
              <text class="res-node__st" :class="'st-' + nr.status">{{ nr.status === 2 ? '成功' : '失败' }}</text>
            </view>
            <view v-if="nr.errorMsg" class="res-node__err">{{ nr.errorMsg }}</view>
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
        <view class="sheet__title">运行历史</view>
        <scroll-view scroll-y class="sheet__body">
          <view v-if="runs.length === 0" class="empty">暂无运行记录</view>
          <view v-for="r in runs" :key="r.runId" class="run-item">
            <view class="run-item__head">
              <text :class="'st-' + r.status">{{ r.statusLabel }}</text>
              <text class="run-item__time">{{ r.createTime }}</text>
            </view>
            <view class="run-item__meta">成功 {{ r.successCount }} / 失败 {{ r.failCount }} · {{ r.duration }}ms</view>
            <view class="run-item__act" @click="openRunDetail(r.runId)">查看详情</view>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="safe-area-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MarkdownView from '@/components/MarkdownView.vue'
import { toolListApi } from '@/api/prompt'
import { uploadFile } from '@/api/request'
import {
  workflowSaveApi, workflowListApi, workflowDetailApi, workflowDeleteApi,
  workflowRunApi, workflowRunsApi, workflowRunDetailApi
} from '@/api/workflow'

// ==================== 状态 ====================
const workflows = ref([])
const listLoading = ref(false)
const tools = ref([])          // [{ toolCode, toolName, inputType, outputType }]

const editing = ref(false)
const saving = ref(false)
const form = reactive({ workflowId: '', name: '', description: '', nodes: [] })

const runVisible = ref(false)
const running = ref(false)
const runTarget = ref(null)
const runInputs = reactive({})   // nodeId -> { text, files: [] }

const resultVisible = ref(false)
const runResult = ref(null)

const runsVisible = ref(false)
const runs = ref([])

// ==================== 工具辅助 ====================
const toolNames = computed(() => tools.value.map(t => t.toolName))
const toolByCode = (code) => tools.value.find(t => t.toolCode === code)
const toolName = (code) => { const t = toolByCode(code); return t ? t.toolName : '' }
const toolIndexOf = (code) => { const i = tools.value.findIndex(t => t.toolCode === code); return i < 0 ? 0 : i }

const onToolChange = (nodeIdx, e) => {
  const t = tools.value[Number(e.detail.value)]
  if (t) form.nodes[nodeIdx].nodeRef = t.toolCode
}

// ==================== 加载 ====================
const loadTools = async () => {
  try {
    const res = await toolListApi()
    tools.value = (res && res.data) || []
  } catch (e) { /* request.js 已 toast */ }
}

const loadList = async () => {
  listLoading.value = true
  try {
    const res = await workflowListApi()
    workflows.value = (res && res.data) || []
  } catch (e) { /* ignore */ } finally {
    listLoading.value = false
  }
}

// ==================== 编辑 ====================
const nodeIdOf = (idx) => 'n' + (idx + 1)

const startCreate = () => {
  form.workflowId = ''
  form.name = ''
  form.description = ''
  form.nodes = []
  editing.value = true
  addNode()
}

const startEdit = async (wf) => {
  try {
    const res = await workflowDetailApi(wf.workflowId)
    const d = res.data
    form.workflowId = d.workflowId
    form.name = d.name
    form.description = d.description || ''
    form.nodes = (d.nodes || []).map(n => ({
      nodeId: n.nodeId, nodeRef: n.nodeRef, name: n.name || '', deps: n.deps || [], params: n.params || {}
    }))
    editing.value = true
  } catch (e) { /* ignore */ }
}

const cancelEdit = () => { editing.value = false }

const addNode = () => {
  if (form.nodes.length >= 50) {
    uni.showToast({ title: '节点数量过多', icon: 'none' })
    return
  }
  form.nodes.push({ nodeId: nodeIdOf(form.nodes.length), nodeRef: '', name: '', deps: [], params: {} })
}

const removeNode = (idx) => {
  const removed = nodeIdOf(idx)
  form.nodes.splice(idx, 1)
  // 重排 nodeId + 清掉被删节点的依赖
  const map = {}
  form.nodes.forEach((n, i) => { map[n.nodeId] = nodeIdOf(i) })
  form.nodes.forEach((n, i) => {
    n.nodeId = nodeIdOf(i)
    n.deps = (n.deps || []).filter(d => d !== removed && map[d] !== undefined).map(d => map[d])
  })
}

const toggleDep = (nodeIdx, otherIdx) => {
  const dep = nodeIdOf(otherIdx)
  const deps = form.nodes[nodeIdx].deps || []
  const pos = deps.indexOf(dep)
  if (pos >= 0) deps.splice(pos, 1)
  else deps.push(dep)
}

/** 前端预演层数（后端保存时会再严格校验一次） */
const previewDepth = computed(() => {
  const nodes = form.nodes
  if (!nodes.length) return 0
  const level = {}
  nodes.forEach(n => { if (!n.deps || !n.deps.length) level[n.nodeId] = 1 })
  let progressed = true
  while (progressed) {
    progressed = false
    nodes.forEach(n => {
      if (level[n.nodeId]) return
      const ds = n.deps || []
      if (ds.length && ds.every(d => level[d])) {
        level[n.nodeId] = Math.max(...ds.map(d => level[d])) + 1
        progressed = true
      }
    })
  }
  const vals = Object.values(level)
  return vals.length ? Math.max(...vals) : 0
})

const save = async () => {
  if (!form.name.trim()) { uni.showToast({ title: '请填写工作流名称', icon: 'none' }); return }
  const bad = form.nodes.find(n => !n.nodeRef)
  if (bad) { uni.showToast({ title: '有节点未选择工具', icon: 'none' }); return }
  saving.value = true
  try {
    await workflowSaveApi({
      workflowId: form.workflowId || undefined,
      name: form.name,
      description: form.description,
      nodes: form.nodes
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
    editing.value = false
    await loadList()
  } catch (e) { /* request.js 已 toast */ } finally {
    saving.value = false
  }
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
        await loadList()
      } catch (e) { /* ignore */ }
    }
  })
}

// ==================== 运行 ====================
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
    // 重置输入
    Object.keys(runInputs).forEach(k => delete runInputs[k])
    ;(res.data.nodes || []).forEach(n => {
      if (!n.deps || !n.deps.length) runInputs[n.nodeId] = { text: '', files: [] }
    })
    runVisible.value = true
  } catch (e) { /* ignore */ }
}

/** 选择文件并上传，返回可访问的 URL（后端按 URL 读取） */
const pickFile = (n) => {
  // H5：原生 input（uni.chooseFile 在 H5 不可靠）
  // #ifdef H5
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = true
  input.accept = '.pdf,.docx,.txt,.mp3,.wav,.m4a,.jpg,.jpeg,.png'
  input.onchange = async () => {
    const files = Array.from(input.files || [])
    await uploadFiles(n, files)
  }
  input.click()
  // #endif
  // #ifndef H5
  uni.chooseMessageFile({
    count: 10,
    success: async (res) => {
      const paths = (res.tempFiles || []).map(f => f.path)
      await uploadFiles(n, paths)
    }
  })
  // #endif
}

/** 上传文件列表（H5 传 File 对象，App 传路径），成功后存 URL 列表 */
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
  } catch (e) { /* request.js 已 toast */ } finally {
    running.value = false
    uni.hideLoading()
  }
}

// ==================== 历史 ====================
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

/** 运行状态中文（后端 WorkflowRunStatusEnum 同步；此处仅做列表快照兜底） */
const runStatusLabel = (code) => {
  return ({ 0: '待运行', 1: '运行中', 2: '全部成功', 3: '部分失败', 4: '全部失败', 5: '已取消' })[code] || ''
}

// ==================== 生命周期 ====================
onShow(async () => {
  if (!tools.value.length) await loadTools()
  await loadList()
})
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #F9FAFB; }
.topbar {
  display: flex; align-items: center; height: 88rpx; padding: 0 24rpx;
  background: #fff; border-bottom: 1rpx solid #F3F4F6;
}
.topbar__title { font-size: 36rpx; font-weight: 600; color: #111827; }
.topbar__spacer { flex: 1; }
.btn-create {
  padding: 8rpx 20rpx; background: linear-gradient(135deg, #3B82F6 0%, #6366F1 100%);
  border-radius: 32rpx;
}
.btn-create__label { color: #fff; font-size: 24rpx; }
.page-content { padding: 24rpx; height: calc(100vh - 88rpx); }

.card {
  background: #fff; border-radius: 24rpx; padding: 32rpx; margin-bottom: 24rpx;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.empty { text-align: center; color: #9CA3AF; font-size: 26rpx; padding: 80rpx 0; }

.input-name, .input-desc {
  width: 100%; font-size: 28rpx; padding: 20rpx 0; border-bottom: 1rpx solid #F3F4F6;
  color: #111827;
}
.section-label { font-size: 24rpx; color: #6B7280; margin: 24rpx 0 12rpx; }

.node-edit { border: 1rpx solid #E5E7EB; border-radius: 16rpx; padding: 20rpx; margin-bottom: 16rpx; }
.node-edit__head { display: flex; align-items: center; margin-bottom: 12rpx; }
.node-edit__idx {
  width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center;
  background: #EFF6FF; color: #3B82F6; border-radius: 50%; font-size: 22rpx; margin-right: 12rpx;
}
.node-edit__picker { flex: 1; }
.picker-value { font-size: 26rpx; color: #111827; }
.node-edit__del { color: #EF4444; font-size: 24rpx; margin-left: 12rpx; }
.node-edit__types { display: flex; gap: 12rpx; margin-bottom: 12rpx; }
.type-tag { font-size: 20rpx; color: #6B7280; background: #F3F4F6; padding: 4rpx 12rpx; border-radius: 8rpx; }
.node-edit__deps { margin-top: 8rpx; }
.deps-label { font-size: 22rpx; color: #9CA3AF; }
.deps-list { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 8rpx; }
.dep-chip {
  padding: 6rpx 16rpx; border-radius: 24rpx; font-size: 22rpx;
  background: #F3F4F6; color: #6B7280;
}
.dep-chip--on { background: #DBEAFE; color: #2563EB; }

.btn-add-node {
  text-align: center; padding: 20rpx; border: 1rpx dashed #D1D5DB; border-radius: 16rpx;
  color: #3B82F6; font-size: 26rpx; margin-top: 8rpx;
}
.edit-hint { font-size: 22rpx; color: #9CA3AF; margin-top: 12rpx; text-align: center; }

.btn {
  display: flex; align-items: center; justify-content: center;
  height: 88rpx; border-radius: 44rpx; font-size: 30rpx; margin-top: 32rpx;
}
.btn--primary { background: linear-gradient(135deg, #3B82F6 0%, #6366F1 100%); color: #fff; }
.btn--ghost { background: #F3F4F6; color: #374151; }
.btn--sm { height: 64rpx; font-size: 24rpx; margin-top: 0; flex: 1; border-radius: 32rpx; }

.wf-card__head { display: flex; align-items: center; justify-content: space-between; }
.wf-card__title { font-size: 30rpx; font-weight: 600; color: #111827; }
.wf-card__status { font-size: 22rpx; }
.wf-card__desc { font-size: 24rpx; color: #6B7280; margin-top: 8rpx; }
.wf-card__meta { font-size: 22rpx; color: #9CA3AF; margin-top: 8rpx; }
.wf-card__actions { display: flex; gap: 12rpx; margin-top: 24rpx; }

.mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: flex-end; z-index: 999;
}
.sheet {
  width: 100%; max-height: 85vh; background: #fff;
  border-radius: 24rpx 24rpx 0 0; padding: 32rpx; display: flex; flex-direction: column;
}
.sheet__title { font-size: 32rpx; font-weight: 600; color: #111827; }
.sheet__desc { font-size: 24rpx; color: #6B7280; margin: 12rpx 0; }
.sheet__body { flex: 1; overflow-y: auto; }

.run-node { margin-bottom: 24rpx; }
.run-node__name { font-size: 26rpx; font-weight: 500; color: #111827; margin-bottom: 12rpx; }
.run-node__tool { font-size: 22rpx; color: #9CA3AF; }
.run-node__text {
  width: 100%; min-height: 160rpx; border: 1rpx solid #E5E7EB; border-radius: 12rpx;
  padding: 16rpx; font-size: 26rpx; box-sizing: border-box;
}
.run-node__file { display: flex; align-items: center; gap: 16rpx; }
.file-pick {
  padding: 12rpx 28rpx; background: #EFF6FF; color: #3B82F6;
  border-radius: 24rpx; font-size: 24rpx;
}
.file-name { font-size: 22rpx; color: #9CA3AF; }

.res-node { border-bottom: 1rpx solid #F3F4F6; padding: 20rpx 0; }
.res-node__head { display: flex; align-items: center; justify-content: space-between; }
.res-node__id { font-size: 26rpx; font-weight: 500; color: #374151; }
.res-node__st { font-size: 22rpx; }
.res-node__err { font-size: 22rpx; color: #EF4444; margin-top: 8rpx; }
.res-out { margin-top: 12rpx; }
.res-out__idx { font-size: 22rpx; color: #9CA3AF; }
.res-out__body { background: #F9FAFB; border-radius: 12rpx; padding: 16rpx; margin-top: 8rpx; }

.run-item { padding: 20rpx 0; border-bottom: 1rpx solid #F3F4F6; }
.run-item__head { display: flex; justify-content: space-between; }
.run-item__time { font-size: 22rpx; color: #9CA3AF; }
.run-item__meta { font-size: 22rpx; color: #6B7280; margin-top: 8rpx; }
.run-item__act { font-size: 24rpx; color: #3B82F6; margin-top: 8rpx; }

.st-2 { color: #10B981; }
.st-3 { color: #F59E0B; }
.st-4 { color: #EF4444; }
.st-0, .st-1 { color: #6B7280; }

.safe-area-bottom { height: 60rpx; }
</style>
