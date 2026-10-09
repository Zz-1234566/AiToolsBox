<template>
  <view class="wfr-page">
    <!-- 顶栏：返回 + 工作流名 + 说明（navigationStyle:custom，必须自绘返回入口） -->
    <view class="wfr-topbar">
      <view class="wfr-topbar__back" hover-class="wfr-topbar__back--tap" @click="goBack">
        <WfIcon name="back" :size="24" :stroke-width="2.4" />
        <text class="wfr-topbar__backt">返回</text>
      </view>
      <view class="wfr-topbar__main">
        <text class="wfr-topbar__title">{{ wfName || '工作流运行' }}</text>
        <text v-if="wfDesc" class="wfr-topbar__sub">{{ wfDesc }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="wfr-body" :scroll-top="scrollTop">
      <view v-if="loading && !nodes.length" class="redesign-empty">
        <text class="redesign-empty__text">加载中…</text>
      </view>

      <view v-else-if="fatalError" class="redesign-empty">
        <view class="redesign-empty__icon"><text class="redesign-empty__emoji">📭</text></view>
        <text class="redesign-empty__text">{{ fatalError }}</text>
      </view>

      <block v-else>
        <!-- 运行级中断提示（取自 sys_workflow_run.error_msg / SSE error 帧） -->
        <view v-if="runAlert" class="wfr-alert">
          <view class="wfr-alert__ic"><WfIcon name="alert" :size="16" :stroke-width="2.1" /></view>
          <view class="wfr-alert__main">
            <text class="wfr-alert__t">工作流运行中断</text>
            <text class="wfr-alert__d">{{ runAlert }}</text>
          </view>
        </view>

        <!-- HUD 状态卡：四态（待运行 / 运行中 / 成功 / 中断） -->
        <view class="wfr-hud" :class="'wfr-hud--' + hudState">
          <view class="wfr-hud__row">
            <view class="wfr-hud__ring">
              <view v-if="hudState === 'run'" class="wfr-spin wfr-spin--lg"></view>
              <WfIcon v-else-if="hudState === 'ok'" name="check" :size="19" :stroke-width="3" />
              <WfIcon v-else-if="hudState === 'err'" name="close" :size="19" :stroke-width="2.6" />
              <WfIcon v-else name="play" :size="17" />
            </view>
            <view class="wfr-hud__main">
              <text class="wfr-hud__ttl">{{ hudTitle }}</text>
              <text class="wfr-hud__sub">{{ hudSub }}</text>
            </view>
            <view class="wfr-hud__right">
              <view class="wfr-hud__timer">
                <text class="wfr-hud__time">{{ hudTimer.v }}</text><text class="wfr-hud__unit">{{ hudTimer.u }}</text>
              </view>
              <text class="wfr-hud__sub">{{ doneCount }}/{{ nodes.length }} 节点</text>
            </view>
          </view>
          <view class="wfr-hud__bar">
            <view class="wfr-hud__fill" :style="{ width: progressPercent + '%' }"></view>
          </view>
        </view>

        <!-- 流程剖面条：一行看完所有节点位置 -->
        <scroll-view v-if="nodes.length" scroll-x class="wfr-pipe" :show-scrollbar="false">
          <view class="wfr-pipe__h">流程</view>
          <view class="wfr-pipe__t">
            <block v-for="(n, i) in nodes" :key="n.nodeId">
              <view class="wfr-pp" :class="'wfr-pp--' + n._state">
                <WfIcon v-if="n._state === 'done'" name="check" :size="11" :stroke-width="3" />
                <WfIcon v-else-if="n._state === 'fail' || n._state === 'blocked'" name="close" :size="11" :stroke-width="3" />
                <text v-else class="wfr-pp__n">{{ i + 1 }}</text>
              </view>
              <view v-if="i < nodes.length - 1" class="wfr-pl" :class="'wfr-pl--' + linkState(i)"></view>
            </block>
          </view>
        </scroll-view>

        <!-- 输入快照 -->
        <view v-if="snapRows.length" class="wfr-snap">
          <view class="wfr-snap__h">
            <WfIcon name="folder" :size="14" :stroke-width="2.1" />
            <text class="wfr-snap__t">输入</text>
            <text class="wfr-pill wfr-pill--idle">{{ inputFileCount }} 个</text>
          </view>
          <view v-for="row in snapRows" :key="row.nodeId" class="wfr-snap__grp">
            <text class="wfr-snap__node">{{ nodeTitleOf(row.nodeId) }}</text>
            <view v-for="(nm, i) in row.names" :key="i" class="wfr-snap__row" @click="onSnapshotClick(nm)">
              <view class="wfr-snap__ic"><text>{{ fileExt(nm) }}</text></view>
              <text class="wfr-snap__n">{{ nm }}</text>
            </view>
          </view>
        </view>

        <!-- 节点链：轨道 + 卡片 -->
        <view class="wfr-sec">
          <text class="wfr-sec__t">执行过程</text>
          <view class="wfr-sec__line"></view>
        </view>
        <view v-for="(n, i) in nodes" :key="n.nodeId" class="wfr-node" :class="'wfr-node--' + n._state">
          <view class="wfr-node__rail">
            <view class="wfr-node__ring">
              <view class="wfr-node__ic" :style="{ background: nodeIconGradient(n.icon || n.nodeRef) }">
                <WfIcon :name="nodeIconName(n.icon || n.nodeRef)" :size="20" :stroke-width="1.9" />
              </view>
            </view>
          </view>
          <view class="wfr-node__card">
            <view class="wfr-node__top">
              <text class="wfr-node__name">{{ n.name }}</text>
              <text class="wfr-pill" :class="'wfr-pill--' + n._state">
                <view v-if="n._state === 'run'" class="wfr-live"></view>
                {{ STATE_TEXT[n._state] }}
              </text>
            </view>

            <!-- 元信息行 -->
            <view v-if="n._state === 'done'" class="wfr-nmeta">
              <text class="wfr-nmeta__em">{{ secText(n.costMs) }}</text>
              <view class="wfr-dot"></view>
              <text class="wfr-nmeta__txt">{{ n.outSummary || '已完成' }}</text>
            </view>
            <view v-else-if="n._state === 'run'" class="wfr-nmeta">
              <view v-if="batchRun" class="wfr-spin wfr-spin--sm"></view>
              <text v-if="batchRun" class="wfr-nmeta__txt">等待执行结果 · 已用 <text class="wfr-nmeta__b">{{ secText(wallMs) }}</text></text>
              <block v-else>
                <view class="wfr-spin wfr-spin--sm"></view>
                <text class="wfr-nmeta__txt">正在处理 · 已用 <text class="wfr-nmeta__b">{{ secText(n._elapsed) }}</text></text>
              </block>
            </view>
            <view v-else-if="n._state === 'fail'" class="wfr-nmeta">
              <text v-if="n.costMs" class="wfr-nmeta__em">已用 {{ secText(n.costMs) }}</text>
              <text v-if="n.costMs" class="wfr-dot"></text>
              <text class="wfr-nmeta__txt wfr-nmeta__txt--err">{{ n.errorMsg || '执行失败' }}</text>
            </view>
            <view v-else-if="n._state === 'blocked'" class="wfr-nmeta">
              <text class="wfr-nmeta__txt">流程已在第 {{ failIndex + 1 }} 步中断，此节点未执行</text>
            </view>
            <view v-else class="wfr-nmeta">
              <text class="wfr-nmeta__txt">{{ n.description || '等待前序节点完成' }}</text>
            </view>

            <!-- 运行中：进度条 + 骨架屏（宽度按已收块数真实推进；同步模式无块可数，不画） -->
            <block v-if="n._state === 'run' && !batchRun">
              <view class="wfr-runprog">
                <view class="wfr-runprog__fill" :style="{ width: runProgressOf(n) + '%' }"></view>
              </view>
              <view v-if="!n._text" class="wfr-skels">
                <view class="wfr-skel wfr-skel--pulse"></view>
                <view class="wfr-skel wfr-skel--pulse wfr-skel--s"></view>
              </view>
            </block>
            <view v-else-if="n._state === 'run'" class="wfr-skels">
              <view class="wfr-skel wfr-skel--pulse"></view>
              <view class="wfr-skel wfr-skel--pulse wfr-skel--s"></view>
            </view>

            <!-- 流式内容 / 完整输出：运行中走 streaming 纯文本，完成后切 markdown 渲染 -->
            <view v-if="showText(n)" class="wfr-node__md">
              <!-- 复制走原始文本（showText 返回后端原始 outputs），不是渲染后的 HTML。
                   两个入口：MD 源码（含 # ** 语法，粘到 Notion/语雀）、
                   纯文本（剥掉语法，粘到微信/钉钉这类不支持 Markdown 的框）。 -->
              <view class="wfr-node__mdhead">
                <text class="wfr-node__mdt">输出</text>
                <view class="wfr-btn-xs" @click="copyRaw(showText(n), 'Markdown')">
                  <WfIcon name="copy" :size="12" :stroke-width="2" />
                  <text>复制MD</text>
                </view>
                <view class="wfr-btn-xs" @click="copyPlain(showText(n), '纯文本')">
                  <WfIcon name="copy" :size="12" :stroke-width="2" />
                  <text>复制纯文本</text>
                </view>
              </view>
              <MarkdownView :source="showText(n)" :streaming="n._state === 'run'" />
            </view>

            <!-- 节点级错误框 -->
            <view v-if="n._state === 'fail'" class="wfr-nerr">
              <view class="wfr-nerr__t">
                <WfIcon name="alert" :size="13" :stroke-width="2" />
                <text>{{ n.errorMsg || '执行失败' }}</text>
              </view>
              <view class="wfr-nerr__meta">
                <text class="wfr-nerr__code">{{ n.nodeId }}</text>
                <text class="wfr-nerr__src">node_results[{{ n.nodeId }}].errorMsg</text>
              </view>
              <view class="wfr-nerr__a" @click="copyErr(n)">
                <WfIcon name="copy" :size="12" :stroke-width="2" />
                <text>复制报错</text>
              </view>
            </view>
          </view>
          <view v-if="i < nodes.length - 1" class="wfr-link" :class="'wfr-link--' + linkState(i)"><view class="wfr-link__i"></view></view>
        </view>

        <!-- 产物 -->
        <view v-if="outputs.length" class="wfr-out">
          <view class="wfr-out__h">
            <text class="wfr-out__t">产物</text>
            <text class="wfr-out__c">{{ outputs.length }} 个</text>
            <text class="wfr-pill wfr-pill--done"><WfIcon name="check" :size="10" :stroke-width="3" />已就绪</text>
          </view>
          <view v-for="o in outputs" :key="o.id || o.fileUrl" class="wfr-outc">
            <view class="wfr-outc__thumb" :style="o.outputType === 2 && o.fileUrl ? { backgroundImage: 'url(' + o.fileUrl + ')' } : {}">
              <view v-if="!o.fileUrl" class="wfr-outc__thumbfallback">
                <WfIcon :name="outIcon(o)" :size="22" :stroke-width="1.9" />
              </view>
            </view>
            <view class="wfr-outc__meta">
              <view class="wfr-outc__mcol">
                <text class="wfr-outc__t">{{ o.fileName || '产物' }}</text>
                <text v-if="o.content" class="wfr-outc__c">{{ o.content }}</text>
              </view>
              <text class="wfr-outc__s">{{ outputTypeLabel(o.outputType) }}{{ o.fileSizeText ? ' · ' + o.fileSizeText : '' }}</text>
            </view>
            <view class="wfr-outc__acts">
              <!-- 文本类产物（outputType=1）没有 fileUrl，给「复制」而不是「下载」 -->
              <view v-if="o.outputType === 1 && o.content" class="wfr-act wfr-act--p" @click="copyRaw(o.content, '产物')">
                <WfIcon name="copy" :size="13" :stroke-width="2" />复制
              </view>
              <view v-if="o.outputType === 2 && o.fileUrl" class="wfr-act wfr-act--p" @click="previewOutput(o)">
                <WfIcon name="play" :size="13" />预览
              </view>
              <view v-if="o.fileUrl" class="wfr-act wfr-act--p" @click="openFile(o.fileUrl)">
                <WfIcon name="download" :size="13" :stroke-width="2.2" />下载
              </view>
            </view>
          </view>
        </view>

        <!-- 运行日志：全部来自真实收到的 SSE 事件 / 终态字段 -->
        <view v-if="logs.length" class="wfr-log">
          <view class="wfr-log__h">
            <WfIcon name="clock" :size="14" :stroke-width="2.1" />
            <text class="wfr-log__t">运行日志</text>
            <text class="wfr-log__c">{{ logs.length }} 条</text>
            <view class="wfr-log__copy" @click="copyRaw(logPlainText(), '日志')">
              <WfIcon name="copy" :size="12" :stroke-width="2" />
              <text>复制</text>
            </view>
          </view>
          <!-- 三列固定宽度 grid：时间 / 图标 / 消息，任意一行内容长短都不影响列起始位置 -->
          <view class="wfr-log__box">
            <view v-for="(l, i) in logs" :key="i" class="wfr-lg" :class="l.k ? 'wfr-lg--' + l.k : ''">
              <text class="wfr-lg__t">{{ l.t }}</text>
              <text class="wfr-lg__i">{{ l.i }}</text>
              <text class="wfr-lg__m">{{ l.m }}</text>
            </view>
          </view>
        </view>

        <view class="wfr-safe"></view>
      </block>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import MarkdownView from '@/components/MarkdownView.vue'
import WfIcon from '@/components/WfIcon.vue'
import { BASE_URL } from '@/config/env'
import { toolListApi } from '@/api/prompt'
import {
  workflowDetailApi, workflowRunApi, workflowRunDetailApi
} from '@/api/workflow'
import { takeWorkflowRunTarget } from '@/utils/workflowRunContext'
import {
  nodeNameOf, nodeIconName, nodeIconGradient, fileDisplayName, openFile,
  outputTypeLabel, previewOutput, parseInputSnapshot, durationParts, durationText
} from '@/utils/workflowView'
import { copyRaw, copyPlain } from '@/utils/clipboard'
import { safeBack } from '@/utils/pageTransition'

/* ==================== 入口参数 ==================== */
const mode = ref('run')            // run = 本次运行（SSE 实时） / detail = 历史回放
const workflowId = ref('')
const runId = ref('')
const loading = ref(false)
const fatalError = ref('')

const wfName = ref('')
const wfDesc = ref('')
const tools = ref([])

/** 节点链：按工作流定义的拓扑序（后端 WorkflowVO.nodes 已按依赖顺序返回） */
const nodes = ref([])
/** 运行结果（detail 模式来自 GET /api/workflow/runs/{runId}；run 模式由 all_done 帧补齐） */
const runResult = ref(null)
/** 运行级错误（error_msg / SSE error 帧） */
const runAlert = ref('')
/** 输入快照：run 模式用本次收集的 inputs；detail 模式用后端 input_snapshot */
const localInputs = ref(null)
const logs = ref([])
const outputs = ref([])

const scrollTop = ref(0)
const timer = ref(null)
/** 本次运行是否在途（run 模式）：同步接口无中间帧，靠它维持 HUD 的「运行中」态与走秒 */
const runningNow = ref(false)
/** 定时器心跳：elapsedMs 依赖它，保证运行中耗时每秒刷新 */
const tick = ref(0)
let runStartedAt = 0

const STATE_TEXT = {
  done: '已完成', run: '运行中', fail: '失败', blocked: '已阻断', idle: '等待中'
}

/* ==================== 节点状态推导 ==================== */

/**
 * 五态推导（waiting/running/success/failed/blocked），全部来自真实数据：
 *  - run 模式：SSE 的 node_start / node_done 帧写入 _live 状态
 *  - detail 模式：nodeResults[nid].status（2 成功 / 4 失败）
 *  - blocked：某个节点失败后，其下游节点被判为「未执行」
 *    （后端对未执行节点只写 errorMsg="上游节点失败，本节点未执行"，
 *      且 status 仍是 4；据此在 detail 模式区分「真失败」与「被阻断」）
 */
const ST = { IDLE: 'idle', RUN: 'run', DONE: 'done', FAIL: 'fail', BLK: 'blocked' }

/** 拓扑序 + 依赖映射（判断 blocked 需要 deps） */
const byId = computed(() => {
  const m = {}
  ;(nodes.value || []).forEach(n => { m[n.nodeId] = n })
  return m
})

/** 某节点是否在某失败节点的下游（BFS 沿 deps 反向传播） */
const isDownstreamOfFailure = (node, failIds) => {
  const seen = new Set()
  const stack = (node.deps || []).slice()
  while (stack.length) {
    const id = stack.pop()
    if (seen.has(id)) continue
    seen.add(id)
    if (failIds.has(id)) return true
    const up = byId.value[id]
    if (up) stack.push(...(up.deps || []))
  }
  return false
}

/** 后端对「上游失败所以本节点没跑」的节点写的固定文案（引擎层约定，两种运行方式一致） */
const SKIP_MSG = '上游节点失败，本节点未执行'

const deriveStates = () => {
  const nr = (runResult.value && runResult.value.nodeResults) || {}
  // 1) 先按各自数据源定出 done / fail / run / idle
  nodes.value.forEach((n) => {
    let s = ST.IDLE
    if (mode.value === 'run') {
      s = n._live || ST.IDLE
    } else {
      const st = nr[n.nodeId] && nr[n.nodeId].status
      if (st === 2) s = ST.DONE
      else if (st === 4) s = ST.FAIL
    }
    n._state = s
  })
  // 2) 真失败集合 = 「状态是失败」且「不是上游未执行」——两轮遍历，不依赖节点先后顺序
  const realFail = new Set()
  nodes.value.forEach((n) => {
    if (n._state !== ST.FAIL) return
    const msg = (nr[n.nodeId] && nr[n.nodeId].errorMsg) || n.errorMsg || ''
    if (msg !== SKIP_MSG) realFail.add(n.nodeId)
  })
  // 3) 上游确有真失败的「未执行」节点 → blocked（设计稿：失败即断，下游显式已阻断）
  if (realFail.size) {
    nodes.value.forEach((n) => {
      if (n._state !== ST.FAIL) return
      const msg = (nr[n.nodeId] && nr[n.nodeId].errorMsg) || n.errorMsg || ''
      if (msg === SKIP_MSG && isDownstreamOfFailure(n, realFail)) n._state = ST.BLK
    })
  }
}

/* ==================== 计算属性 ==================== */

const doneCount = computed(() => nodes.value.filter(n => n._state === ST.DONE).length)
const failIndex = computed(() => nodes.value.findIndex(n => n._state === ST.FAIL || n._state === ST.BLK))
const runIndex = computed(() => nodes.value.findIndex(n => n._state === ST.RUN))

/** 单文件/纯文本走同步接口：后端不推中间帧，只能整条链一起跑 */
const batchRun = ref(false)
/** 同步模式下的墙钟耗时（供 batchRun 节点显示「已用」） */
const wallMs = computed(() => {
  void tick.value
  return mode.value === 'run' && runningNow.value ? Date.now() - runStartedAt : 0
})

/** HUD 态：idle（未开始）/ run / ok / err */
const hasFail = computed(() => nodes.value.some(n => n._state === ST.FAIL || n._state === ST.BLK))
const hudState = computed(() => {
  if (mode.value === 'detail') {
    if (loading.value) return 'idle'
    return hasFail.value ? 'err' : 'ok'
  }
  if (runningNow.value) return 'run'
  if (hasFail.value) return 'err'
  if (doneCount.value > 0 || !loading.value) return 'ok'
  return 'idle'
})

/**
 * 累计耗时：运行中走真实墙钟（runStartedAt 起算），
 * 终态取各节点后端 costMs 之和（与历史回放同一口径）。
 */
const elapsedMs = computed(() => {
  if (mode.value === 'run' && runningNow.value) {
    void tick.value   // 建立对 tick 的依赖，定时器心跳时重算
    return Date.now() - runStartedAt
  }
  let sum = 0
  nodes.value.forEach((n) => { if (n.costMs) sum += n.costMs })
  return sum
})

const hudTitle = computed(() => ({
  idle: '待运行', run: '运行中', ok: '运行成功', err: '运行中断'
}[hudState.value]))

const hudSub = computed(() => {
  if (hudState.value === 'run') {
    // SSE 模式能定位到具体节点；同步模式没有中间帧，退化为「全部 N 个节点执行中」
    if (runIndex.value < 0) return `全部 ${nodes.value.length} 个节点执行中`
    const n = nodes.value[runIndex.value]
    return `第 ${runIndex.value + 1} 步 · ${n ? n.name : ''}`
  }
  if (hudState.value === 'err') {
    const n = nodes.value[failIndex.value]
    return n ? `第 ${failIndex.value + 1} 步 · ${n.name}` : '运行中断'
  }
  if (hudState.value === 'ok') return `全部 ${nodes.value.length} 个节点已完成`
  return `共 ${nodes.value.length} 个节点 · 未开始`
})

/** 计时器：运行中显示已用，终态显示总耗时（设计稿 hud-timer，等宽数字） */
const hudTimer = computed(() => {
  if (hudState.value === 'idle') return { v: '00:00', u: '' }
  // durationParts 对「数据缺失」返回 null；此时显示 '—' 而不是 0，避免误读成「瞬间完成」
  const parts = durationParts(elapsedMs.value)
  if (!parts) return { v: '—', u: '' }
  if (parts.length === 2) return { v: `${parts[0].v}${parts[1].v}`, u: '分秒' }
  return { v: parts[0].v, u: parts[0].u }
})

const progressPercent = computed(() => {
  const total = nodes.value.length
  if (!total) return 0
  if (hudState.value === 'ok') return 100
  if (hudState.value === 'idle') return 0
  if (hudState.value === 'err') {
    return Math.min(100, Math.round(((doneCount.value + 0.5) / total) * 100))
  }
  // 运行中：按已完成节点数 + 当前节点半格（SSE 模式才有 runIndex）
  const partial = runIndex.value >= 0 ? 0.5 : 0
  return Math.min(100, Math.round(((doneCount.value + partial) / total) * 100))
})

/** 输入快照行：run 模式取本次收集的 inputs，detail 模式取后端 input_snapshot */
const snapRows = computed(() => {
  if (localInputs.value) {
    return Object.keys(localInputs.value)
      .map((nodeId) => ({
        nodeId,
        names: (localInputs.value[nodeId] || []).map(fileDisplayName).filter(Boolean)
      }))
      .filter(r => r.names.length)
  }
  return parseInputSnapshot(runResult.value && runResult.value.inputSnapshot)
})
const inputFileCount = computed(() => snapRows.value.reduce((a, r) => a + r.names.length, 0))

/** 轨道/剖面连线状态（设计稿 linkState 语义） */
const linkState = (i) => {
  const a = nodes.value[i]._state
  const b = nodes.value[i + 1] && nodes.value[i + 1]._state
  if (a === ST.FAIL) return 'fail'
  if (a === ST.DONE && b === ST.RUN) return 'flow'
  if (a === ST.DONE) return 'done'
  if (b === ST.BLK) return 'blk'
  return 'idle'
}

/**
 * 节点耗时文案（节点 meta 行 / 日志共用）。
 * 数据缺失（costMs 为 null/undefined）返回空串，由调用方决定省略还是显示 '—'，
 * 绝不用 0 顶替 —— 0 是「瞬间完成」，缺数据是「没这个数」，两者含义不同。
 */
const secText = (ms) => durationText(ms, '')

/** 运行中节点的进度条：按已收到的块数相对上一次翻倍推进（不伪造终值） */
const runProgressOf = (n) => {
  const base = n._progBase || 0
  const chunks = n._chunkCount || 0
  return Math.min(92, base + Math.min(20, chunks * 4))
}

/** 节点展示文本：运行中用流式累积文本，完成后用 nodeResults.outputs */
const showText = (n) => {
  if (mode.value === 'run') {
    if (n._state === ST.RUN || n._state === ST.DONE) return n._text || ''
    return ''
  }
  const nr = (runResult.value && runResult.value.nodeResults) || {}
  const outs = (nr[n.nodeId] && nr[n.nodeId].outputs) || []
  if (!outs.length) return ''
  if (outs.length === 1) return outs[0] || ''
  return outs.map((o, i) => `**输出 ${i + 1}**\n\n${o || ''}`).join('\n\n')
}

/** 节点输出摘要（成功节点 meta 行右侧） */
const fillOutSummary = (n) => {
  if (n.outSummary) return
  const nr = (runResult.value && runResult.value.nodeResults) || {}
  const outs = (nr[n.nodeId] && nr[n.nodeId].outputs) || []
  if (outs.length) n.outSummary = outs.length === 1 ? firstLine(outs[0]) : `${outs.length} 个输出`
}

const firstLine = (s) => {
  const line = String(s || '').split('\n').map(x => x.replace(/^#+\s*/, '').trim()).filter(Boolean)[0] || ''
  return line.length > 40 ? line.slice(0, 40) + '…' : line
}

const fileExt = (name) => {
  const m = /\.([a-z0-9]+)$/i.exec(String(name || ''))
  return (m ? m[1] : 'file').toUpperCase().slice(0, 4)
}

const outIcon = (o) => ({
  1: 'summary', 2: 'image', 3: 'video', 4: 'mic', 5: 'file'
}[o.outputType] || 'file')

const onSnapshotClick = (name) => {
  const raw = snapshotUrlOf(name)
  if (raw) openFile(raw)
  else uni.showToast({ title: '输入内容为文本，不可预览', icon: 'none' })
}

/** 快照里按显示名回查原始 url（inputSnapshot 存的是路径） */
const snapshotUrlOf = (displayName) => {
  const src = localInputs.value || (function () {
    try { return JSON.parse((runResult.value && runResult.value.inputSnapshot) || '{}') } catch (e) { return {} }
  })()
  for (const k of Object.keys(src || {})) {
    const arr = Array.isArray(src[k]) ? src[k] : [src[k]]
    for (const x of arr) {
      const v = typeof x === 'string' ? x : (x && (x.url || x.fileName))
      if (v && fileDisplayName(v) === displayName && /^(https?:|\/)/.test(v)) return v
    }
  }
  return ''
}

const copyErr = (n) => {
  const txt = `[${wfName.value || '工作流'}] 节点 ${n.nodeId}（${n.name}）执行失败：${n.errorMsg || '未知原因'}`
  copyRaw(txt, '报错')
}

const autoScroll = () => {
  scrollTop.value = 999999
  setTimeout(() => { scrollTop.value = 0 }, 60)
}

/* ==================== 日志 ==================== */

/**
 * 日志条：{ t: 时间戳, i: 图标, m: 消息, k: 语义色 }。
 * 图标独立成列（不用消息里的 ✓/✕ 前缀），这样三列固定宽度、每行起始位置完全对齐。
 */
const nowText = () => {
  const d = new Date()
  const p = (x) => String(x).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/**
 * 追加一条日志。
 * @param {string} m 消息正文
 * @param {string} [k] 语义色：'' 普通 / run 运行中 / ok 成功 / err 失败
 * @param {string} [opt.t] 时间戳；不传用当前时间（历史回放会传真实 createTime）
 * @param {string} [opt.i] 图标列（▶ ✓ ✕ ○ ■），不传留空
 */
const pushLog = (m, k, opt) => {
  const o = opt || {}
  logs.value = logs.value.concat([{
    t: o.t || nowText(),
    i: o.i || '',
    m,
    k: k || ''
  }])
}

/** 日志区「复制全部」：拼纯文本（不含签名 URL） */
const logPlainText = () => logs.value
  .map(l => `[${l.t}] ${l.i ? l.i + ' ' : ''}${l.m}`)
  .join('\n')

/* ==================== 加载 ==================== */

const ctx = computed(() => ({ nodes: nodes.value, tools: tools.value }))
/** SSE 帧自带 nodeRef 时的三参版真名解析 */
const nodeNameWithRef = (nodeId, nr, nodeRef) => nodeNameOf(nodeId, nr, nodeRef, ctx.value)
/** 模板用的简版：只给 nodeId（走工作流详情 / 工具列表反查） */
const nodeTitleOf = (nodeId) => nodeNameOf(nodeId, null, null, ctx.value)

const loadTools = async () => {
  try {
    const res = await toolListApi()
    tools.value = (res && res.data) || []
  } catch (e) { /* ignore */ }
}

/** 拉工作流详情 → 建节点链（顺序即执行顺序，后端已按依赖拓扑返回） */
const loadWorkflow = async (id) => {
  const res = await workflowDetailApi(id)
  const wf = res.data
  wfName.value = (wf && wf.name) || ''
  wfDesc.value = (wf && wf.description) || ''
  nodes.value = (wf && wf.nodes) || []
}

const init = async () => {
  loading.value = true
  try {
    await loadTools()
    if (mode.value === 'run') {
      const target = takeWorkflowRunTarget()
      if (!target || !target.workflowId) {
        fatalError.value = '运行参数已失效，请返回工作流列表重新发起'
        return
      }
      workflowId.value = target.workflowId
      if (target.name) wfName.value = target.name
      if (target.description) wfDesc.value = target.description
      localInputs.value = target.inputs || {}
      await loadWorkflow(target.workflowId)
      deriveStates()
      if (target.inputs) {
        const total = Object.keys(target.inputs).length
        pushLog(`工作流待运行 · 共 ${nodes.value.length} 个节点`, '', { i: '○' })
        if (total) pushLog(`已准备 ${total} 个起始节点输入`, '', { i: '' })
      }
      startRun()
    } else {
      const res = await workflowRunDetailApi(runId.value)
      runResult.value = res.data
      runAlert.value = (res.data && res.data.errorMsg) || ''
      outputs.value = Array.isArray(res.data && res.data.outputs) ? res.data.outputs : []
      if (res.data && res.data.workflowName) wfName.value = res.data.workflowName
      workflowId.value = (res.data && res.data.workflowId) || ''
      // 补齐节点真名上下文：runDetail 带 workflowId，直接进详情时本地没有该工作流的
      // 节点真名，nodeNameOf 会退化成 n1/n2。这里按 workflowId 补拉一次详情；
      // 拉失败不阻断详情展示，只是名字回退成 nodeId。
      if (workflowId.value) {
        try { await loadWorkflow(workflowId.value) } catch (e) { /* 拿不到真名就显示 nodeId */ }
      }
      const nr = (res.data && res.data.nodeResults) || {}
      nodes.value.forEach(n => {
        const r = nr[n.nodeId]
        if (r) {
          n.costMs = r.costMs || 0
          n.errorMsg = r.errorMsg || ''
          if (r.nodeName) n.name = r.nodeName
        }
      })
      deriveStates()
      nodes.value.forEach(fillOutSummary)
      buildDetailLogs(res.data)
    }
  } catch (e) {
    fatalError.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  mode.value = (options && options.mode) || 'run'
  workflowId.value = (options && options.workflowId) || ''
  runId.value = (options && options.runId) || ''
  init()
})

onUnmounted(() => { stopTimer(); if (xhr) { try { xhr.abort() } catch (e) { /* ignore */ } } })

/**
 * 返回：复用项目的 safeBack。
 * 它内部先判 getCurrentPages().length > 1 再 navigateBack，栈底（用户直接输 URL /
 * H5 刷新本页）时兜底 reLaunch 到工作流列表，不会出现「点返回没反应」。
 */
const goBack = () => safeBack('/pages/workflow')

/* ==================== 计时器 ==================== */
/**
 * 心跳：只做两件事——推动 elapsedMs 依赖的 tick 重算、刷新正在跑的节点耗时。
 * 走秒全部来自真实墙钟（runStartedAt / 节点 _startedAt），不做任何进度伪造。
 */
const startTimer = () => {
  stopTimer()
  runStartedAt = Date.now()
  runningNow.value = true
  timer.value = setInterval(() => {
    const now = Date.now()
    tick.value = now
    nodes.value.forEach((n) => {
      if (n._live === ST.RUN && n._startedAt) n._elapsed = now - n._startedAt
    })
  }, 100)
}
const stopTimer = () => {
  if (timer.value) { clearInterval(timer.value); timer.value = null }
  runningNow.value = false
}

/* ==================== 运行（SSE / 同步）==================== */

/** 文件数 > 1 → SSE 流式（边跑边展示）；单文件/纯文本 → 同步接口 */
const shouldStream = (inputs) => {
  const max = Math.max(0, ...Object.values(inputs || {}).map(a => (a || []).length))
  return max > 1
}

let xhr = null

const startRun = () => {
  const inputs = localInputs.value || {}
  pushLog('工作流已启动', 'run', { i: '▶' })
  if (shouldStream(inputs)) startStream(inputs)
  else startSync(inputs)
}

/**
 * 同步运行：后端只有一次性结果，没有中间帧。
 * 期间把所有节点标成「运行中」，跑完再按 nodeResults 落终态
 * （文案上写「等待执行结果」而非假装正在生成内容，避免展示不存在的流式效果）。
 */
const startSync = async (inputs) => {
  batchRun.value = true
  nodes.value.forEach(n => { n._live = ST.RUN; n._startedAt = Date.now() })
  deriveStates()
  startTimer()
  try {
    const res = await workflowRunApi(workflowId.value, inputs)
    runResult.value = res.data
    outputs.value = Array.isArray(res.data && res.data.outputs) ? res.data.outputs : []
    runAlert.value = (res.data && res.data.errorMsg) || ''
    const nr = (res.data && res.data.nodeResults) || {}
    nodes.value.forEach((n) => {
      const r = nr[n.nodeId]
      n.costMs = (r && r.costMs) || 0
      n.errorMsg = (r && r.errorMsg) || ''
      n._startedAt = 0
      n._live = (!r) ? ST.IDLE : (r.status === 2 ? ST.DONE : ST.FAIL)
    })
    deriveStates()
    nodes.value.forEach(fillOutSummary)
    const k = res.data.failCount > 0 ? 'err' : 'ok'
    const totalSec = secText(res.data.duration)
    pushLog(`工作流${res.data.failCount > 0 ? '中断' : '结束'}${totalSec ? ` · 总耗时 ${totalSec}` : ''} · 成功 ${res.data.successCount} / 失败 ${res.data.failCount}`, k, { i: '■' })
  } catch (e) {
    runAlert.value = (e && e.message) || '运行失败'
    // 请求异常：全部节点回到未执行，不伪造失败节点
    nodes.value.forEach(n => { n._live = ST.IDLE; n._startedAt = 0 })
    deriveStates()
    pushLog(`运行失败 · ${runAlert.value}`, 'err', { i: '✕' })
  } finally {
    batchRun.value = false
    stopTimer()
  }
}

/**
 * SSE 流式运行：逐帧接收，边跑边展示。
 * 帧类型：run_start / file_start / node_start / chunk / node_done / file_done / all_done / error
 */
const startStream = (inputs) => {
  const token = uni.getStorageSync('token')
  const url = BASE_URL + `/api/workflow/${workflowId.value}/run/stream`

  // 每个文件的显示名（按源节点输入顺序，取用户上传时的文件名）
  const fileNames = []
  Object.keys(inputs).forEach((nodeId) => {
    ;(inputs[nodeId] || []).forEach(f => fileNames.push(fileDisplayName(f)))
  })

  let consumed = 0
  let buffer = ''

  const handleFrame = (obj) => {
    const t = obj.type
    if (t === 'run_start') {
      pushLog(`runId=${obj.runId}`, 'run', { i: '' })
    } else if (t === 'file_start') {
      const name = fileNames[obj.fileIndex] || ('文件 ' + (obj.fileIndex + 1))
      pushLog(`[${obj.fileIndex + 1}/${obj.fileTotal}] ${name}`, '', { i: '' })
    } else if (t === 'node_start') {
      const n = byId.value[obj.nodeId]
      if (n) {
        n._live = ST.RUN
        n._text = ''
        n._chunkCount = 0
        n._progBase = 0
        n._startedAt = Date.now()
        n._elapsed = 0
      }
      const label = n ? n.name : nodeNameWithRef(obj.nodeId, null, obj.nodeRef)
      pushLog(`[${obj.fileIndex + 1}] ${label} 进行中`, 'run', { i: '◌' })
      deriveStates()
      autoScroll()
    } else if (t === 'chunk') {
      const n = byId.value[obj.nodeId]
      if (n) {
        n._text = (n._text || '') + (obj.text || '')
        n._chunkCount = (n._chunkCount || 0) + 1
        // 首块到达后给一个起始基线，之后按块数推进（真实反映内容增长，不跳到 100%）
        if (n._chunkCount === 1) n._progBase = 24
      }
    } else if (t === 'node_done') {
      const n = byId.value[obj.nodeId]
      if (n) {
        // 收口耗时：本次 node_start 起到现在的真实毫秒（多文件时按文件累加到同一节点）
        if (n._startedAt) n.costMs = (n.costMs || 0) + (Date.now() - n._startedAt)
        n._startedAt = 0
        n._live = obj.ok ? ST.DONE : ST.FAIL
        n.errorMsg = obj.ok ? '' : (obj.errMsg || '执行失败')
        // 非 text 节点通过 output 带回完整内容（text 节点已由 chunk 累积）
        if (obj.output) n._text = (n._text || '') + obj.output
        n._progBase = 100
        const label = n.name
        const cost = secText(n.costMs)
        if (obj.ok) {
          pushLog(`${label} 完成${cost ? ` · ${cost}` : ''}`, 'ok', { i: '✓' })
        } else {
          const skipped = obj.errMsg === SKIP_MSG
          pushLog(`${label} ${skipped ? '未执行（上游失败）' : '失败 · ' + n.errorMsg}`, 'err', { i: skipped ? '○' : '✕' })
        }
      }
      deriveStates()
      autoScroll()
    } else if (t === 'file_done') {
      const name = fileNames[obj.fileIndex] || ('文件 ' + (obj.fileIndex + 1))
      pushLog(obj.ok ? `${name} 处理完成` : `${name} 处理失败`, obj.ok ? 'ok' : 'err', { i: obj.ok ? '✓' : '✕' })
    } else if (t === 'all_done') {
      stopTimer()
      // 收尾：把仍处于 run/idle 的节点定终态。
      // 后端在「上游失败」时也会给被跳过的节点发 node_done(ok=false, errMsg=SKIP_MSG)，
      // 这类节点已由 node_done 标成 fail，这里不再覆盖成 done，否则会把断点抹平。
      nodes.value.forEach(n => {
        if (n._live === ST.RUN) n._live = ST.DONE
        if (n._text && !n.outSummary) n.outSummary = firstLine(n._text)
      })
      runResult.value = {
        nodeResults: nodes.value.reduce((acc, n) => {
          acc[n.nodeId] = {
            status: n._live === ST.DONE ? 2 : 4,
            costMs: n.costMs || 0,
            errorMsg: n.errorMsg || '',
            outputs: n._text ? [n._text] : []
          }
          return acc
        }, {}),
        status: obj.status,
        successCount: obj.successCount,
        failCount: obj.failCount,
        duration: obj.duration
      }
      deriveStates()
      const k = obj.failCount > 0 ? 'err' : 'ok'
      const totalSec = secText(obj.duration)
      pushLog(`工作流${obj.failCount > 0 ? '中断' : '结束'}${totalSec ? ` · 总耗时 ${totalSec}` : ''} · 成功 ${obj.successCount} / 失败 ${obj.failCount}`, k, { i: '■' })
      autoScroll()
    } else if (t === 'error') {
      stopTimer()
      runAlert.value = obj.message || '运行失败'
      pushLog(runAlert.value, 'err', { i: '✕' })
    }
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

  xhr = new XMLHttpRequest()
  xhr.open('POST', url, true)
  xhr.setRequestHeader('Content-Type', 'application/json')
  if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token)

  xhr.onprogress = () => feed(xhr.responseText)
  xhr.onload = () => {
    if (xhr.status >= 400) {
      stopTimer()
      let msg = '请求失败（' + xhr.status + '）'
      try {
        const body = JSON.parse(xhr.responseText || '{}')
        if (body && body.message) msg = body.message
      } catch (e) { /* ignore */ }
      runAlert.value = msg
      pushLog(msg, 'err', { i: '✕' })
      return
    }
    feed(xhr.responseText, true)
    stopTimer()
  }
  xhr.onerror = () => {
    stopTimer()
    runAlert.value = '网络异常，运行中断'
    pushLog('网络异常，运行中断', 'err', { i: '✕' })
  }
  startTimer()
  xhr.send(JSON.stringify({ inputs }))
}

/* ==================== 历史回放日志 ==================== */

/**
 * detail 模式：日志由已落库的终态字段重建（后端不存原始 SSE 帧，不伪造时间线）。
 *
 * ⚠️ 时间戳来源只有一个 —— 后端的 createTime / finishedAt（"yyyy-MM-dd HH:mm:ss"）。
 * 早期版本这里把完整 createTime 截断成 slice(11) 后又拼进消息正文，
 * 导致时间在同一行出现两次（"16:51:50  10:13:22  ▶ ..."），其中 10:13:22 是
 * 被截断出来的日期时间尾巴、不是时长。现在：时间只在时间列，消息正文不再带时间。
 */
const buildDetailLogs = (data) => {
  const t = (data.createTime || '').slice(11) || '--:--:--'
  pushLog(`工作流已启动 · run_${String(data.runId || '').slice(0, 6)}`, 'run', { t, i: '▶' })
  const nr = data.nodeResults || {}
  nodes.value.forEach((n) => {
    const r = nr[n.nodeId]
    if (!r) return
    if (r.status === 2) {
      const cost = secText(r.costMs)
      pushLog(cost ? `${n.name} 完成 · ${cost}` : `${n.name} 完成`, 'ok', { t, i: '✓' })
    } else if (r.errorMsg === SKIP_MSG) {
      pushLog(`${n.name} 未执行（上游失败）`, '', { t, i: '○' })
    } else {
      pushLog(`${n.name} 失败 · ${r.errorMsg || '未知原因'}`, 'err', { t, i: '✕' })
    }
  })
  const total = secText(data.duration)
  pushLog(
    `${data.statusLabel || '结束'}${total ? ` · 总耗时 ${total}` : ''} · 成功 ${data.successCount} / 失败 ${data.failCount}`,
    data.failCount > 0 ? 'err' : 'ok',
    { t, i: '■' }
  )
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';
@import '@/styles/animations.scss';

/* 加载环：HUD 运行中大环 + 节点 meta 行小环 */
.wfr-spin {
  border-radius: 50%;
  border-style: solid;
  border-color: #DBEAFE;
  border-top-color: #3B82F6;
  animation: spin 0.8s linear infinite;
}
.wfr-spin--lg { width: 38rpx; height: 38rpx; border-width: 5rpx; }
.wfr-spin--sm { width: 20rpx; height: 20rpx; border-width: 3rpx; flex-shrink: 0; }

.wfr-page {
  min-height: 100vh;
  background: #F9FAFB;
  display: flex;
  flex-direction: column;
}

/* ===== 顶栏 ===== */
.wfr-topbar {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 32rpx;
  background: #fff;
  border-bottom: 2rpx solid #F3F4F6;
}
/* 返回入口：30×30 命中区起步，这里给 112rpx×64rpx + 「返回」文字，
   保证图标 + 文案双重可感知（原先只有 21px 纯图标，视觉上太弱易被忽略） */
.wfr-topbar__back {
  display: flex;
  align-items: center;
  gap: 6rpx;
  height: 64rpx;
  padding: 0 20rpx 0 8rpx;
  margin-left: -8rpx;
  border-radius: 9999rpx;
  color: #111827;
  background: #F3F4F6;
  flex-shrink: 0;
}
.wfr-topbar__back--tap { background: #E5E7EB; }
.wfr-topbar__backt { font-size: 26rpx; font-weight: 500; color: #111827; }
.wfr-topbar__main { flex: 1; min-width: 0; }
.wfr-topbar__title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.wfr-topbar__sub {
  display: block;
  font-size: 22rpx;
  color: #9CA3AF;
  margin-top: 2rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wfr-body { flex: 1; padding: 24rpx 32rpx 0; box-sizing: border-box; }
.wfr-safe { height: 60rpx; }

/* ===== 状态 pill ===== */
.wfr-pill {
  height: 40rpx;
  padding: 0 14rpx;
  border-radius: 8rpx;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
  flex-shrink: 0;
}
.wfr-pill--run { background: #EFF6FF; color: #3B82F6; }
.wfr-pill--done { background: #D1FAE5; color: #047857; }
.wfr-pill--fail { background: #FEE2E2; color: #DC2626; }
.wfr-pill--blocked, .wfr-pill--idle { background: #F3F4F6; color: #9CA3AF; }
.wfr-live {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #3B82F6;
  animation: breathe 1.5s ease-in-out infinite;
}

/* ===== HUD ===== */
.wfr-hud {
  background: #fff;
  border-radius: 32rpx;
  padding: 28rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  border: 2rpx solid rgba(17, 24, 39, 0.04);
  margin-bottom: 24rpx;
}
.wfr-hud__row { display: flex; align-items: center; gap: 22rpx; }
.wfr-hud__ring {
  width: 76rpx;
  height: 76rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.wfr-hud__main { flex: 1; min-width: 0; }
.wfr-hud__ttl { display: block; font-size: 29rpx; font-weight: 600; line-height: 40rpx; }
.wfr-hud__sub { display: block; font-size: 23rpx; color: #9CA3AF; margin-top: 2rpx; }
.wfr-hud__right { text-align: right; flex-shrink: 0; }
.wfr-hud__timer {
  font-family: "SF Mono", "Cascadia Mono", "Roboto Mono", monospace;
  font-variant-numeric: tabular-nums;
  font-size: 38rpx;
  font-weight: 600;
  line-height: 44rpx;
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
}
.wfr-hud__unit { font-size: 21rpx; font-weight: 500; color: #9CA3AF; margin-left: 4rpx; }
.wfr-hud__bar {
  height: 8rpx;
  border-radius: 4rpx;
  background: #F3F4F6;
  margin-top: 26rpx;
  overflow: hidden;
}
.wfr-hud__fill { height: 100%; border-radius: 4rpx; transition: width 0.3s ease; }

.wfr-hud--idle .wfr-hud__ring { background: #F9FAFB; color: #9CA3AF; }
.wfr-hud--idle .wfr-hud__fill { background: #D1D5DB; }
.wfr-hud--run .wfr-hud__ring { background: #EFF6FF; }
.wfr-hud--run .wfr-hud__ttl { color: #3B82F6; }
.wfr-hud--run .wfr-hud__fill { background: linear-gradient(90deg, #3B82F6, #6366F1); }
.wfr-hud--ok .wfr-hud__ring { background: #D1FAE5; color: #047857; }
.wfr-hud--ok .wfr-hud__ttl { color: #047857; }
.wfr-hud--ok .wfr-hud__fill { background: linear-gradient(90deg, #10B981, #34D399); }
.wfr-hud--err .wfr-hud__ring { background: #FEE2E2; color: #DC2626; }
.wfr-hud--err .wfr-hud__ttl { color: #DC2626; }
.wfr-hud--err .wfr-hud__fill { background: linear-gradient(90deg, #EF4444, #F87171); }

/* ===== 断点 alert ===== */
.wfr-alert {
  display: flex;
  gap: 18rpx;
  padding: 22rpx 24rpx;
  border-radius: 24rpx;
  background: #FEE2E2;
  margin-bottom: 24rpx;
  align-items: flex-start;
}
.wfr-alert__ic { color: #EF4444; margin-top: 2rpx; }
.wfr-alert__main { flex: 1; min-width: 0; }
.wfr-alert__t { display: block; font-size: 25rpx; font-weight: 600; color: #B91C1C; line-height: 36rpx; }
.wfr-alert__d { display: block; font-size: 23rpx; color: #991B1B; opacity: 0.86; margin-top: 6rpx; line-height: 34rpx; }

/* ===== 流程剖面条 ===== */
.wfr-pipe {
  display: flex;
  align-items: center;
  padding: 22rpx 26rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  border: 2rpx solid rgba(17, 24, 39, 0.04);
  margin-bottom: 36rpx;
  white-space: nowrap;
}
.wfr-pipe__h {
  font-size: 21rpx;
  font-weight: 600;
  color: #9CA3AF;
  writing-mode: vertical-rl;
  letter-spacing: 2rpx;
  margin-right: 22rpx;
  border-right: 2rpx solid #F3F4F6;
  padding-right: 18rpx;
  height: 68rpx;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.wfr-pipe__t { display: flex; align-items: center; }
.wfr-pp {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F3F4F6;
  color: #D1D5DB;
}
.wfr-pp__n { font-size: 19rpx; font-weight: 700; }
.wfr-pp--done { background: #10B981; color: #fff; }
.wfr-pp--run { background: #3B82F6; color: #fff; box-shadow: 0 0 0 8rpx #EFF6FF; }
.wfr-pp--fail, .wfr-pp--blocked { background: #EF4444; color: #fff; box-shadow: 0 0 0 8rpx #FEE2E2; }
.wfr-pl { flex: 1; height: 4rpx; background: #F3F4F6; border-radius: 2rpx; min-width: 16rpx; }
.wfr-pl--done { background: #10B981; }
.wfr-pl--fail { background: #EF4444; }
.wfr-pl--run { background: linear-gradient(90deg, #10B981, #3B82F6); }
.wfr-pl--blk { background: repeating-linear-gradient(90deg, #D1D5DB 0 6rpx, transparent 6rpx 12rpx); }

/* ===== 输入快照 ===== */
.wfr-snap {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  border: 2rpx solid rgba(17, 24, 39, 0.04);
  margin-bottom: 24rpx;
}
.wfr-snap__h { display: flex; align-items: center; gap: 14rpx; color: #111827; margin-bottom: 16rpx; }
.wfr-snap__t { font-size: 24rpx; font-weight: 600; }
.wfr-snap__h .wfr-pill { margin-left: auto; }
.wfr-snap__grp { margin-top: 8rpx; }
.wfr-snap__node { display: block; font-size: 21rpx; color: #9CA3AF; margin-bottom: 8rpx; }
.wfr-snap__row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 14rpx 0;
  border-top: 2rpx solid #F3F4F6;
}
.wfr-snap__ic {
  width: 52rpx;
  height: 52rpx;
  border-radius: 16rpx;
  background: #EFF6FF;
  color: #3B82F6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.wfr-snap__n {
  flex: 1;
  min-width: 0;
  font-size: 25rpx;
  color: #111827;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ===== 区块标题 ===== */
.wfr-sec { display: flex; align-items: center; gap: 14rpx; margin: 40rpx 0 24rpx; }
.wfr-sec__t { font-size: 26rpx; font-weight: 600; color: #111827; }
.wfr-sec__line { flex: 1; height: 2rpx; background: #F3F4F6; }

/* ===== 节点链（48px 轨道 + 1fr 卡片）===== */
.wfr-node {
  display: grid;
  grid-template-columns: 96rpx 1fr;
  column-gap: 20rpx;
  align-items: flex-start;
  position: relative;
}
.wfr-node__rail { display: flex; justify-content: center; padding-top: 24rpx; }
.wfr-node__ring { position: relative; width: 96rpx; height: 96rpx; display: flex; align-items: center; justify-content: center; }
.wfr-node__ic {
  position: relative;
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  z-index: 1;
}
.wfr-node__card {
  background: #fff;
  border-radius: 24rpx;
  padding: 22rpx 24rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  border: 2rpx solid rgba(17, 24, 39, 0.04);
  position: relative;
  overflow: hidden;
  min-width: 0;
}

/* 辉光：keyframes 会覆盖基础 opacity，所以 animation 只挂在 .is-run 上，
   否则所有节点都会一直呼吸发光（设计稿原注释） */
.wfr-node__ring::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: 32rpx;
  opacity: 0;
  background: conic-gradient(from 0deg,
    rgba(59, 130, 246, 0) 0deg, rgba(59, 130, 246, 0) 200deg,
    #3B82F6 285deg, #6366F1 360deg);
  animation: spin 1.1s linear infinite;
}
.wfr-node__ring::after {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: 32rpx;
  opacity: 0;
  box-shadow: 0 0 0 10rpx rgba(59, 130, 246, 0.14);
}
.wfr-node--run .wfr-node__ring::before { opacity: 1; }
.wfr-node--run .wfr-node__ring::after { opacity: 1; animation: breathe 1.8s ease-in-out infinite; }
.wfr-node--fail .wfr-node__ring::before { opacity: 1; animation: none; background: #EF4444; }
.wfr-node--fail .wfr-node__ic { box-shadow: 0 0 0 5rpx #fff, 0 0 0 10rpx rgba(239, 68, 68, 0.22); }
.wfr-node--blocked .wfr-node__ic { filter: grayscale(1); opacity: 0.45; box-shadow: none; }
.wfr-node--blocked .wfr-node__card {
  background: transparent;
  border: 2rpx dashed #D1D5DB;
  box-shadow: none;
}
.wfr-node--blocked .wfr-node__name { color: #9CA3AF; font-weight: 500; }
.wfr-node--run .wfr-node__card { border-color: rgba(59, 130, 246, 0.28); box-shadow: 0 4rpx 14rpx rgba(59, 130, 246, 0.10); }
.wfr-node--fail .wfr-node__card { border-color: rgba(239, 68, 68, 0.30); box-shadow: 0 4rpx 14rpx rgba(239, 68, 68, 0.09); }
.wfr-node--fail .wfr-node__card::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6rpx;
  background: #EF4444;
}

.wfr-node__top { display: flex; align-items: center; gap: 16rpx; }
.wfr-node__name {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.wfr-nmeta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 10rpx;
  flex-wrap: wrap;
}
.wfr-nmeta__em { font-style: normal; color: #4B5563; font-weight: 500; font-size: 23rpx; }
.wfr-nmeta__txt { font-size: 23rpx; color: #9CA3AF; }
.wfr-nmeta__txt--err { color: #DC2626; font-weight: 500; }
.wfr-nmeta__b { color: #3B82F6; font-weight: 600; }
.wfr-dot { width: 5rpx; height: 5rpx; border-radius: 50%; background: #D1D5DB; flex-shrink: 0; }

/* 运行中进度条 + 骨架屏 */
.wfr-runprog {
  height: 6rpx;
  border-radius: 3rpx;
  background: #EFF6FF;
  margin-top: 18rpx;
  overflow: hidden;
}
.wfr-runprog__fill { height: 100%; border-radius: 3rpx; background: #3B82F6; transition: width 0.3s ease; }
.wfr-skels { display: flex; gap: 16rpx; margin-top: 20rpx; }
.wfr-skel { flex: 1; height: 76rpx; border-radius: 16rpx; background: #F3F4F6; }
.wfr-skel--s { opacity: 0.7; }
.wfr-skel--pulse { animation: pulse 1.4s ease-in-out infinite; }

.wfr-node__md {
  margin-top: 18rpx;
  background: #F9FAFB;
  border-radius: 16rpx;
  padding: 20rpx;
  border: 2rpx solid #F3F4F6;
}
/* 输出区标题条：左侧「输出」标签，右侧复制按钮 */
.wfr-node__mdhead { display: flex; align-items: center; gap: 14rpx; margin-bottom: 12rpx; }
.wfr-node__mdt { flex: 1; font-size: 21rpx; font-weight: 600; color: #9CA3AF; }

/* 通用小按钮（设计稿 .btn-xs：52rpx 高、小圆角、轻描边） */
.wfr-btn-xs {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  height: 52rpx;
  padding: 0 20rpx;
  font-size: 23rpx;
  font-weight: 600;
  color: #4B5563;
  background: #fff;
  border: 1rpx solid #E5E7EB;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.wfr-btn-xs:active { background: #F3F4F6; }

/* 节点错误框 */
.wfr-nerr {
  margin-top: 18rpx;
  padding: 16rpx 18rpx;
  border-radius: 16rpx;
  background: #FEF2F2;
  border: 1rpx solid rgba(239, 68, 68, 0.16);
}
.wfr-nerr__t { display: flex; gap: 12rpx; align-items: flex-start; color: #B91C1C; font-size: 23rpx; line-height: 34rpx; }
.wfr-nerr__meta { display: flex; align-items: center; gap: 12rpx; margin-top: 14rpx; }
.wfr-nerr__code {
  font-family: "SF Mono", "Cascadia Mono", "Roboto Mono", monospace;
  font-size: 20rpx;
  background: rgba(220, 38, 38, 0.11);
  color: #B91C1C;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
}
.wfr-nerr__src {
  font-size: 20rpx;
  color: #991B1B;
  opacity: 0.7;
  font-family: "SF Mono", "Cascadia Mono", "Roboto Mono", monospace;
}
.wfr-nerr__a {
  margin-top: 16rpx;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  height: 52rpx;
  padding: 0 20rpx;
  font-size: 23rpx;
  font-weight: 600;
  color: #4B5563;
  background: #fff;
  border: 1rpx solid rgba(239, 68, 68, 0.22);
  border-radius: 8rpx;
}

/* 轨道连线：done 绿 / flow 蓝色彗星 / fail 红 / blk 虚线 */
.wfr-link { display: grid; grid-template-columns: 96rpx 1fr; column-gap: 20rpx; height: 36rpx; }
.wfr-link__i { grid-column: 1; justify-self: center; width: 4rpx; height: 100%; border-radius: 2rpx; position: relative; display: block; }
.wfr-link--idle .wfr-link__i { background: #E5E7EB; }
.wfr-link--done .wfr-link__i { background: linear-gradient(180deg, #10B981, #34D399); }
.wfr-link--fail .wfr-link__i { background: linear-gradient(180deg, #F87171, #EF4444); }
.wfr-link--blk .wfr-link__i { background: repeating-linear-gradient(180deg, #D1D5DB 0 8rpx, transparent 8rpx 18rpx); }
.wfr-link--flow .wfr-link__i { background: #BFDBFE; overflow: hidden; }
.wfr-link--flow .wfr-link__i::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  height: 46%;
  border-radius: 2rpx;
  background: linear-gradient(180deg, rgba(59, 130, 246, 0), #3B82F6 70%, #A5B4FC);
  animation: comet 1.25s linear infinite;
}

/* ===== 产物 ===== */
.wfr-out { margin-top: 32rpx; }
.wfr-out__h { display: flex; align-items: center; gap: 14rpx; margin-bottom: 20rpx; }
.wfr-out__t { font-size: 26rpx; font-weight: 600; color: #111827; }
.wfr-out__c { font-size: 23rpx; color: #9CA3AF; }
.wfr-out__h .wfr-pill { margin-left: auto; }
.wfr-outc {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  border: 2rpx solid rgba(17, 24, 39, 0.04);
}
.wfr-outc__thumb {
  height: 224rpx;
  border-radius: 16rpx;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #0F172A, #334155);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
}
.wfr-outc__thumbfallback { color: #fff; opacity: 0.9; }
.wfr-outc__meta { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 18rpx; }
.wfr-outc__mcol { flex: 1; min-width: 0; }
.wfr-outc__c {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #4B5563;
  line-height: 32rpx;
  max-height: 96rpx;
  overflow: hidden;
}
.wfr-outc__t {
  flex: 1;
  min-width: 0;
  font-size: 25rpx;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.wfr-outc__s { font-size: 22rpx; color: #9CA3AF; flex-shrink: 0; }
.wfr-outc__acts { display: flex; gap: 16rpx; margin-top: 20rpx; }
.wfr-act {
  flex: 1;
  height: 76rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  font-size: 26rpx;
  font-weight: 600;
}
.wfr-act--p { background: #3B82F6; color: #fff; }
.wfr-act--p:active { background: #2563EB; }

/* ===== 运行日志 ===== */
.wfr-log {
  margin-top: 36rpx;
  background: #fff;
  border-radius: 24rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
  border: 2rpx solid rgba(17, 24, 39, 0.04);
  overflow: hidden;
}
.wfr-log__h {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 20rpx 24rpx;
  border-bottom: 2rpx solid #F3F4F6;
  color: #111827;
}
.wfr-log__t { font-size: 24rpx; font-weight: 600; }
.wfr-log__c {
  margin-left: auto;
  font-size: 22rpx;
  color: #9CA3AF;
  font-family: "SF Mono", "Cascadia Mono", "Roboto Mono", monospace;
}
.wfr-log__box {
  padding: 18rpx 24rpx;
  background: #F9FAFB;
  font-family: "SF Mono", "Cascadia Mono", "Roboto Mono", monospace;
  font-size: 21rpx;
  line-height: 38rpx;
}
/* 三列固定宽度：时间戳(10ch) / 图标(1.5em) / 消息(1fr)。
   之前用 flex + gap，消息里内嵌的「时间/图标前缀」宽度随内容变化，导致
   每行消息起始 x 不一致；改成 grid 后列起始位置由列宽唯一决定，严格对齐。 */
.wfr-lg {
  display: grid;
  grid-template-columns: 118rpx 36rpx 1fr;
  align-items: baseline;
}
.wfr-lg__t { color: #9CA3AF; font-variant-numeric: tabular-nums; }
.wfr-lg__i { color: #9CA3AF; text-align: center; }
.wfr-lg__m { color: #4B5563; word-break: break-all; }
.wfr-lg--ok .wfr-lg__i, .wfr-lg--ok .wfr-lg__m { color: #047857; }
.wfr-lg--run .wfr-lg__i, .wfr-lg--run .wfr-lg__m { color: #3B82F6; }
.wfr-lg--err .wfr-lg__i, .wfr-lg--err .wfr-lg__m { color: #DC2626; }

/* 日志区「复制」小按钮（设计稿 .btn-xs：小圆角、轻量描边） */
.wfr-log__copy {
  margin-left: 16rpx;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  height: 44rpx;
  padding: 0 16rpx;
  border-radius: 8rpx;
  background: #F9FAFB;
  border: 1rpx solid #E5E7EB;
  font-size: 21rpx;
  color: #4B5563;
  flex-shrink: 0;
}
.wfr-log__copy:active { background: #F3F4F6; }
</style>
