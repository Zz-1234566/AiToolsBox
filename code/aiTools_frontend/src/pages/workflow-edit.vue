<template>
  <view class="wfedit-page">
    <!-- 顶栏 -->
    <view class="wfe-topbar">
      <view class="wfe-back" @click="goBack"><text class="wfe-back__icon">‹</text></view>
      <text class="wfe-topbar__title">{{ isNew ? '创建工作流' : '编辑工作流' }}</text>
      <view class="wfe-topbar__spacer"></view>
    </view>

    <!-- 名称 -->
    <view class="wfe-field">
      <text class="wfe-label wfe-label--req">工作流名称</text>
      <input class="wfe-input" v-model="form.name" placeholder="请输入工作流名称" placeholder-class="wfe-ph" :maxlength="64" />
    </view>

    <!-- 描述 -->
    <view class="wfe-field">
      <text class="wfe-label">工作流描述</text>
      <textarea class="wfe-textarea" v-model="form.description" placeholder="请输入工作流描述（可选）" placeholder-class="wfe-ph" :maxlength="200" />
      <text class="wfe-counter">{{ (form.description || '').length }}/200</text>
    </view>

    <!-- 节点区 -->
    <view class="wfe-section">
      <text class="wfe-section__title">节点</text>
      <text class="wfe-section__hint">（按依赖顺序自动分层，最多 5 层）</text>
    </view>

    <view class="wfe-nodes">
      <view v-for="(node, idx) in form.nodes" :key="idx" class="wfe-node">
        <view class="wfe-node__idx">{{ idx + 1 }}</view>
        <view class="wfe-node__body">
          <view class="wfe-node__titlerow">
            <!-- 工具选择 -->
            <picker class="wfe-node__picker" :range="toolNames" :value="toolIndexOf(node.nodeRef)" @change="(e) => onToolChange(idx, e)">
              <view class="wfe-node__name">{{ toolName(node.nodeRef) || '选择工具' }}</view>
            </picker>
            <text class="wfe-node__del" @click="removeNode(idx)">✕</text>
          </view>

          <!-- 输入/输出类型 -->
          <view v-if="toolByCode(node.nodeRef)" class="wfe-node__types">
            <text class="wfe-type">输入: <text class="wfe-type__val">{{ typeLabel(toolByCode(node.nodeRef).inputType) }}</text></text>
            <text class="wfe-type">输出: <text class="wfe-type__val">{{ typeLabel(toolByCode(node.nodeRef).outputType) }}</text></text>
          </view>

          <!-- AI 节点：提示词（点击打开抽屉选择，对齐工具详情页交互） -->
          <view v-if="needsPrompt(node.nodeRef)" class="wfe-node__prompts">
            <view class="wfe-prompt">
              <view class="wfe-prompt__head">
                <text class="wfe-prompt__label">格式提示词</text>
                <view class="wfe-prompt__pick" @click="openPromptPicker(idx, 'format')">选择提示词</view>
              </view>
              <view class="wfe-prompt__box" @click="openPromptPicker(idx, 'format')">
                <text class="wfe-prompt__text" :class="{ 'wfe-prompt__text--ph': !pickedPromptName(node, 'format') }">
                  {{ pickedPromptName(node, 'format') ? '已选：' + pickedPromptName(node, 'format') : '未选择，将使用系统默认提示词' }}
                </text>
              </view>
            </view>

            <view class="wfe-prompt">
              <view class="wfe-prompt__head">
                <text class="wfe-prompt__label">生成提示词</text>
                <view class="wfe-prompt__pick" @click="openPromptPicker(idx, 'generate')">选择提示词</view>
              </view>
              <view class="wfe-prompt__box" @click="openPromptPicker(idx, 'generate')">
                <text class="wfe-prompt__text" :class="{ 'wfe-prompt__text--ph': !pickedPromptName(node, 'generate') }">
                  {{ pickedPromptName(node, 'generate') ? '已选：' + pickedPromptName(node, 'generate') : '未选择，将使用系统默认提示词' }}
                </text>
              </view>
            </view>
          </view>

          <!-- 起始节点 or 输入来源 -->
          <view class="wfe-node__dep">
            <block v-if="!node.deps || node.deps.length === 0">
              <text class="wfe-dep__start">起始节点 · 运行时由你提供{{ inputLabelOf(node) }}</text>
            </block>
            <block v-else>
              <text class="wfe-dep__label">{{ inputLabelOf(node) }}来自：</text>
              <picker class="wfe-dep__picker" :range="depOptions(idx)" :value="depIndexOf(idx)" @change="(e) => onDepChange(idx, e)">
                <view class="wfe-dep__select">{{ depDisplay(idx) }} ▾</view>
              </picker>
            </block>
          </view>
        </view>
      </view>

      <view class="wfe-add" @click="addNode">
        <text>＋ 添加节点</text>
      </view>
    </view>

    <!-- 统计 + 保存 -->
    <view class="wfe-foot">
      <text>节点数：<text class="wfe-foot__val">{{ form.nodes.length }}</text></text>
      <text>预计层数：<text class="wfe-foot__val">{{ previewDepth }}</text></text>
    </view>
    <view class="wfe-save">
      <view class="btn-solid" :class="{ 'btn-solid--disabled': saving }" @click="saving ? null : save()">
        {{ saving ? '保存中...' : '保存工作流' }}
      </view>
    </view>

    <!-- 提示词选择抽屉（复用工具详情页组件） -->
    <PromptPickerDrawer
      v-model:show="showPromptPicker"
      :tool-name="pickerToolName"
      :tool-desc="pickerToolDesc"
      :system-prompts="systemPromptList"
      :user-prompts="userPromptList"
      @confirm="onPromptConfirm"
    />
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { toolListApi, systemPromptListApi, promptListApi } from '@/api/prompt'
import { workflowSaveApi, workflowDetailApi } from '@/api/workflow'
import { TOOLS } from '@/config/tools'
import PromptPickerDrawer from '@/components/PromptPickerDrawer.vue'

const tools = ref([])
const saving = ref(false)
const editingId = ref('')
const form = reactive({ name: '', description: '', nodes: [] })
/** 工具编码 → 系统默认提示词 { format, generate }，用于判断该工具是否需要提示词 */
const defaultPrompts = reactive({})
/** 已选提示词的展示名（内存）：键 `${nodeId}_${format|generate}`，不落库 */
const pickedNames = ref({})

const isNew = computed(() => !editingId.value)

const toolNames = computed(() => tools.value.map(t => t.toolName))
const toolByCode = (code) => tools.value.find(t => t.toolCode === code)
const toolName = (code) => { const t = toolByCode(code); return t ? t.toolName : '' }
const toolIndexOf = (code) => { const i = tools.value.findIndex(t => t.toolCode === code); return i < 0 ? 0 : i }

const TYPE_MAP = { text: '文本', audio: '音频', document: '文档', image: '图片', file: '文件', none: '无' }
const typeLabel = (s) => {
  if (!s) return '—'
  return s.split(',').map(x => TYPE_MAP[x.trim()] || x.trim()).join(' / ')
}

/**
 * 节点的输入语义标签（该节点接收什么）。
 * 取自 tools.js 的 inputLabel；无配置时按输入类型兜底。
 */
const inputLabelOf = (node) => {
  const t = TOOLS[node.nodeRef]
  if (t && t.inputLabel) return t.inputLabel
  const it = toolByCode(node.nodeRef)?.inputType || ''
  if (it.includes('audio')) return '录音'
  if (it.includes('image')) return '图片'
  if (it.includes('document') || it.includes('file')) return '文件'
  return '内容'
}

const nodeIdOf = (idx) => 'n' + (idx + 1)

/**
 * 该工具是否需要用户配置提示词。
 * 判断依据：该系统是否**确实配置了提示词**（format 或 generate 有内容）。
 * 纯转换工具（文档转文本 / 录音转写）由系统内部提供提示词或不调 AI，不应显示提示词框。
 */
const needsPrompt = (code) => {
  const d = defaultPrompts[code]
  return !!(d && (d.format || d.generate))
}

/** 懒加载某工具的系统默认提示词 */
const ensureDefaultPrompts = async (code) => {
  if (!code || defaultPrompts[code] !== undefined) return
  try {
    const res = await systemPromptListApi(code)
    const list = (res && res.data) || []
    const fmt = list.find(p => p.promptUse === 'format')
    const gen = list.find(p => p.promptUse === 'generate')
    defaultPrompts[code] = { format: fmt ? fmt.promptText : '', generate: gen ? gen.promptText : '' }
  } catch (e) {
    defaultPrompts[code] = { format: '', generate: '' }
  }
}

/** ========== 提示词抽屉（复用工具详情页 PromptPickerDrawer） ========== */
const showPromptPicker = ref(false)
const pickerNodeIdx = ref(-1)        // 当前编辑的节点下标
const pickerTarget = ref('format')   // format | generate
const systemPromptList = ref([])
const userPromptList = ref([])
const pickerToolName = computed(() => {
  const n = form.nodes[pickerNodeIdx.value]
  return n ? toolName(n.nodeRef) : ''
})
const pickerToolDesc = computed(() => {
  const n = form.nodes[pickerNodeIdx.value]
  const t = n ? toolByCode(n.nodeRef) : null
  return t ? (t.toolDesc || '') : ''
})

/** 打开抽屉：按节点工具 + 用途加载系统/用户提示词 */
const openPromptPicker = async (nodeIdx, target) => {
  pickerNodeIdx.value = nodeIdx
  pickerTarget.value = target
  const code = form.nodes[nodeIdx].nodeRef
  try {
    const [userRes, sysRes] = await Promise.all([
      promptListApi(code),
      systemPromptListApi(code)
    ])
    userPromptList.value = ((userRes && userRes.data) || []).filter(p => p.promptUse === target)
    systemPromptList.value = ((sysRes && sysRes.data) || []).filter(p => p.promptUse === target)
  } catch (e) {
    userPromptList.value = []
    systemPromptList.value = []
  }
  showPromptPicker.value = true
}

/**
 * 抽屉确认：记录「选中的提示词 id」（C 方案：只存 id，跟随提示词更新）。
 * 同时清掉可能残留的纯文本，避免文本优先把 id 覆盖掉。
 * 提示词名称只存内存（pickedNames），不写入 params，避免污染落库数据。
 */
const onPromptConfirm = (item) => {
  if (!item) return
  const node = form.nodes[pickerNodeIdx.value]
  if (!node) return
  if (!node.params) node.params = {}
  if (pickerTarget.value === 'format') {
    node.params.promptIdFormat = String(item.id)
    delete node.params.promptFormat
  } else {
    node.params.promptIdGenerate = String(item.id)
    delete node.params.promptGenerate
  }
  // 名称仅用于界面回显（按 nodeId + 用途 记在内存，不落库）
  pickedNames.value[`${node.nodeId}_${pickerTarget.value}`] = item.promptName || ''
}

/** 节点某用途已选提示词的展示名（内存） */
const pickedPromptName = (node, use) => pickedNames.value[`${node.nodeId}_${use}`] || ''

/** 上游候选：除自己外的所有节点 */
const depOptions = (idx) => form.nodes.map((n, i) => i === idx ? null : `${nodeIdOf(i)} · ${toolName(n.nodeRef) || '未选工具'}`).filter(Boolean)
const depIndexOf = (idx) => {
  const deps = form.nodes[idx].deps || []
  if (!deps.length) return 0
  const opts = depOptions(idx)
  const want = `${deps[0]} · ${toolName(form.nodes[Number(deps[0].slice(1)) - 1]?.nodeRef) || '未选工具'}`
  const pos = opts.indexOf(want)
  return pos < 0 ? 0 : pos
}
const depDisplay = (idx) => {
  const deps = form.nodes[idx].deps || []
  if (!deps.length) return '请选择'
  const opts = depOptions(idx)
  return opts[depIndexOf(idx)] || '请选择'
}

const onToolChange = (nodeIdx, e) => {
  const t = tools.value[Number(e.detail.value)]
  if (!t) return
  const node = form.nodes[nodeIdx]
  const changed = node.nodeRef !== t.toolCode
  node.nodeRef = t.toolCode
  // 换工具时清掉旧的提示词参数，避免张冠李戴
  if (changed) node.params = {}
  ensureDefaultPrompts(t.toolCode)
}
const onDepChange = (nodeIdx, e) => {
  const opts = depOptions(nodeIdx)
  const chosen = opts[Number(e.detail.value)]
  if (!chosen) return
  const depId = chosen.split(' · ')[0]
  form.nodes[nodeIdx].deps = depId ? [depId] : []
}

/** 预计层数（与后端 validateAndPlan 同规则，后端保存时仍会严格校验） */
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
  const map = {}
  form.nodes.forEach((n, i) => { map[n.nodeId] = nodeIdOf(i) })
  form.nodes.forEach((n, i) => {
    n.nodeId = nodeIdOf(i)
    n.deps = (n.deps || []).filter(d => d !== removed && map[d] !== undefined).map(d => map[d])
  })
}

const save = async () => {
  if (!form.name.trim()) { uni.showToast({ title: '请填写工作流名称', icon: 'none' }); return }
  const bad = form.nodes.find(n => !n.nodeRef)
  if (bad) { uni.showToast({ title: '有节点未选择工具', icon: 'none' }); return }
  saving.value = true
  try {
    await workflowSaveApi({
      workflowId: editingId.value || undefined,
      name: form.name,
      description: form.description,
      nodes: form.nodes
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    // request.js 已统一提示
  } finally {
    saving.value = false
  }
}

const goBack = () => uni.navigateBack({ delta: 1 })

onLoad(async (opt) => {
  try {
    const res = await toolListApi()
    tools.value = (res && res.data) || []
  } catch (e) { /* ignore */ }

  if (opt && opt.workflowId) {
    editingId.value = opt.workflowId
    try {
      const res = await workflowDetailApi(opt.workflowId)
      const d = res.data
      form.name = d.name || ''
      form.description = d.description || ''
      form.nodes = (d.nodes || []).map(n => ({
        nodeId: n.nodeId, nodeRef: n.nodeRef, name: n.name || '', deps: n.deps || [], params: n.params || {}
      }))
      // 预加载各节点工具的默认提示词，使「格式/生成提示词」输入框正常显示
      await Promise.all(form.nodes.map(n => ensureDefaultPrompts(n.nodeRef)))
      // 回填已选提示词的展示名（按 promptId 反查）
      await Promise.all(form.nodes.map(async (n) => {
        const p = n.params || {}
        const ids = [['format', p.promptIdFormat], ['generate', p.promptIdGenerate]]
        for (const [use, id] of ids) {
          if (!id) continue
          try {
            const res = await systemPromptListApi(n.nodeRef)
            const found = ((res && res.data) || []).find(x => String(x.id) === String(id))
            if (found) pickedNames.value[`${n.nodeId}_${use}`] = found.promptName || ''
          } catch (e) { /* ignore */ }
        }
      }))
    } catch (e) { /* ignore */ }
  } else {
    addNode()
  }
})
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.wfedit-page {
  min-height: 100vh;
  background: #F9FAFB;
  padding-bottom: 48rpx;
}

.wfe-topbar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: #fff;
}
.wfe-back { width: 64rpx; }
.wfe-back__icon { font-size: 56rpx; color: #111827; line-height: 1; }
.wfe-topbar__title {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: #111827;
}
.wfe-topbar__spacer { width: 64rpx; }

.wfe-field { padding: 24rpx 32rpx 0; }
.wfe-label {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 16rpx;
}
.wfe-label--req::after { content: ' *'; color: #EF4444; }
.wfe-input {
  width: 100%;
  height: 96rpx;
  border: 2rpx solid #E5E7EB;
  border-radius: 24rpx;
  padding: 0 32rpx;
  font-size: 28rpx;
  background: #fff;
  box-sizing: border-box;
}
.wfe-textarea {
  width: 100%;
  min-height: 176rpx;
  border: 2rpx solid #E5E7EB;
  border-radius: 24rpx;
  padding: 24rpx 32rpx;
  font-size: 28rpx;
  background: #fff;
  box-sizing: border-box;
}
.wfe-counter {
  display: block;
  text-align: right;
  font-size: 22rpx;
  color: #D1D5DB;
  margin-top: 8rpx;
}
.wfe-ph { color: #D1D5DB; }

.wfe-section {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  padding: 32rpx 32rpx 16rpx;
}
.wfe-section__title { font-size: 32rpx; font-weight: 700; color: #111827; }
.wfe-section__hint { font-size: 22rpx; color: #9CA3AF; }

.wfe-nodes { padding: 0 32rpx; }
.wfe-node {
  display: flex;
  gap: 24rpx;
  background: #fff;
  border: 2rpx solid #F3F4F6;
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}
.wfe-node__idx {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: #EFF6FF;
  color: #3B82F6;
  font-size: 26rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.wfe-node__body { flex: 1; min-width: 0; }
.wfe-node__titlerow { display: flex; align-items: center; gap: 16rpx; }
.wfe-node__picker { flex: 1; }
.wfe-node__name {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  padding: 8rpx 0;
}
.wfe-node__del {
  color: #EF4444;
  font-size: 32rpx;
  padding: 0 8rpx;
  flex-shrink: 0;
}
.wfe-node__types { display: flex; gap: 24rpx; margin-top: 12rpx; }
.wfe-type { font-size: 22rpx; color: #9CA3AF; }
.wfe-type__val {
  background: #F9FAFB;
  border-radius: 8rpx;
  padding: 2rpx 12rpx;
  color: #4B5563;
}

.wfe-node__dep { display: flex; align-items: center; gap: 16rpx; margin-top: 20rpx; }

/* AI 节点：提示词区（点击打开抽屉） */
.wfe-node__prompts {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx dashed #E5E7EB;
}
.wfe-prompt { margin-bottom: 16rpx; }
.wfe-prompt__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.wfe-prompt__label {
  font-size: 24rpx;
  color: #4B5563;
}
.wfe-prompt__pick {
  font-size: 22rpx;
  color: #3B82F6;
  padding: 4rpx 16rpx;
  background: #EFF6FF;
  border-radius: 9999rpx;
}
.wfe-prompt__box {
  max-height: 160rpx;
  overflow: hidden;
  border: 2rpx solid #E5E7EB;
  border-radius: 16rpx;
  padding: 16rpx 20rpx;
  background: #fff;
}
.wfe-prompt__text {
  font-size: 24rpx;
  color: #111827;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.wfe-prompt__text--ph { color: #9CA3AF; }
.wfe-dep__label { font-size: 26rpx; color: #4B5563; }
.wfe-dep__select {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 24rpx;
  border: 2rpx solid #E5E7EB;
  border-radius: 16rpx;
  font-size: 26rpx;
  color: #111827;
  background: #fff;
}
.wfe-dep__start {
  display: inline-flex;
  align-items: center;
  height: 52rpx;
  padding: 0 24rpx;
  border-radius: 8rpx;
  background: #EFF6FF;
  color: #3B82F6;
  font-size: 22rpx;
}

.wfe-add {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border: 2rpx dashed #3B82F6;
  border-radius: 24rpx;
  color: #3B82F6;
  font-size: 28rpx;
  font-weight: 500;
  margin-bottom: 24rpx;
}

.wfe-foot {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 32rpx 16rpx;
  font-size: 26rpx;
  color: #9CA3AF;
}
.wfe-foot__val { color: #111827; font-weight: 600; }
.wfe-save { padding: 0 32rpx; }
</style>
