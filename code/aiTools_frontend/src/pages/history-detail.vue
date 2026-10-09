<template>
  <view class="hd-page">
    <!-- 头部 -->
    <view class="hd-head">
      <view class="hd-back" @click="goBack">
        <svg class="hd-back__icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M15 19L8 12L15 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      <text class="hd-head__title">记录详情</text>
    </view>

    <view v-if="loading" class="redesign-empty"><text class="redesign-empty__text">加载中…</text></view>
    <view v-else-if="!item" class="redesign-empty">
      <view class="redesign-empty__icon"><text class="redesign-empty__emoji">📭</text></view>
      <text class="redesign-empty__text">记录不存在或已删除</text>
    </view>

    <block v-else>
      <!-- 概览卡 -->
      <view class="hd-hero">
        <view class="tool-avatar" :class="'tool-avatar--' + iconType(item)">
          <text class="ta-emoji">{{ emoji(item) }}</text>
        </view>
        <view class="hd-hero__info">
          <text class="hd-hero__name">{{ getToolName(item) }}</text>
          <view class="hd-hero__meta">
            <view class="hist-badge" :class="statusOk ? 'hist-badge--ok' : 'hist-badge--fail'">
              <text>{{ statusOk ? '✓ 成功' : '✗ 失败' }}</text>
            </view>
            <text class="hd-hero__time">{{ formatTime(item.createTime) }}</text>
          </view>
        </view>
      </view>

      <!-- 耗时 -->
      <view v-if="item.duration" class="hd-row">
        <text class="hd-row__k">耗时</text>
        <text class="hd-row__v">{{ (item.duration / 1000).toFixed(2) }} 秒</text>
      </view>

      <!-- 输入 -->
      <view class="hd-block">
        <text class="hd-block__title">输入</text>
        <view class="hd-box">
          <text v-if="!item.inputContent && !inputFile" class="hd-box__empty">（无输入）</text>
          <!-- 文件类输入：只显示文件名 + 下载入口，不把 COS 签名 URL 铺在页面上 -->
          <view v-else-if="inputFile" class="hd-inputfile" hover-class="hd-inputfile--hover" @click="onInputFileTap">
            <text class="hd-inputfile__icon">{{ fileIcon(inputFile.ext) }}</text>
            <view class="hd-inputfile__name">
              <text class="hd-inputfile__base">{{ inputFile.base }}</text><text class="hd-inputfile__ext">{{ inputFile.suffix }}</text>
            </view>
            <text class="hd-inputfile__action">{{ inputFileAction }}</text>
          </view>
          <scroll-view v-else-if="isLong(item.inputContent)" scroll-y class="hd-box__scroll">
            <text class="hd-box__text">{{ item.inputContent }}</text>
          </scroll-view>
          <text v-else class="hd-box__text">{{ item.inputContent }}</text>
        </view>
      </view>

      <!-- 结果 -->
      <view class="hd-block">
        <text class="hd-block__title">结果</text>
        <view v-if="statusOk" class="hd-box">
          <block v-if="item.outputContent">
            <scroll-view v-if="isLong(item.outputContent)" scroll-y class="hd-box__scroll">
              <MarkdownView class="hd-md" :source="item.outputContent" />
            </scroll-view>
            <MarkdownView v-else class="hd-md" :source="item.outputContent" />
          </block>
          <text v-else class="hd-box__empty">（无输出）</text>
        </view>
        <view v-else class="hd-box hd-box--error">
          <scroll-view v-if="isLong(item.errorMsg)" scroll-y class="hd-box__scroll">
            <text class="hd-box__text hd-box__text--error">{{ item.errorMsg }}</text>
          </scroll-view>
          <text v-else class="hd-box__text hd-box__text--error">{{ item.errorMsg || '处理失败' }}</text>
        </view>
      </view>

      <!-- 使用的提示词 -->
      <view v-if="hasPrompt" class="hd-block">
        <text class="hd-block__title">使用的提示词</text>
        <view v-if="item.promptFormat" class="hd-prompt">
          <text class="hd-prompt__label">格式提示词</text>
          <scroll-view v-if="isLong(item.promptFormat)" scroll-y class="hd-prompt__scroll">
            <text class="hd-prompt__text">{{ item.promptFormat }}</text>
          </scroll-view>
          <text v-else class="hd-prompt__text">{{ item.promptFormat }}</text>
        </view>
        <view v-if="item.promptGenerate" class="hd-prompt">
          <text class="hd-prompt__label">生成提示词</text>
          <scroll-view v-if="isLong(item.promptGenerate)" scroll-y class="hd-prompt__scroll">
            <text class="hd-prompt__text">{{ item.promptGenerate }}</text>
          </scroll-view>
          <text v-else class="hd-prompt__text">{{ item.promptGenerate }}</text>
        </view>
      </view>

      <!-- 附件（文件落库未实现，files 恒为空；保留渲染分支，后端补齐后自动可用） -->
      <view v-if="files.length" class="hd-block">
        <text class="hd-block__title">附件</text>
        <view v-for="f in files" :key="f.id" class="hd-file" @click="onOpenFile(f)">
          <text class="hd-file__icon">📎</text>
          <text class="hd-file__name">{{ f.fileName || '文件' }}</text>
          <text class="hd-file__action">下载</text>
        </view>
      </view>

      <!-- 操作 -->
      <view class="hd-actions">
        <view class="hd-btn" @click="onCopyResult">
          <text>复制结果</text>
        </view>
        <view class="hd-btn hd-btn--primary" @click="onReuse">
          <text>再次使用</text>
        </view>
      </view>

      <view class="safe-bottom"></view>
    </block>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { historyListByToolApi } from '@/api/history'
import { requireLogin } from '@/utils/auth'
import { safeBack } from '@/utils/pageTransition'
import { copyRaw } from '@/utils/clipboard'
import { isFileUrl, extensionOf, fileKeyOf, fileNameFromUrl, isPlayableExt, isAudioExt } from '@/utils/fileDisplayName'
import MarkdownView from '@/components/MarkdownView.vue'

const loading = ref(false)
const item = ref(null)
const fileLoading = ref(false)   // 输入文件下载中，防止重复点击

const statusOk = computed(() => !!item.value && item.value.status === 1)
const files = computed(() => (item.value && item.value.files) || [])
const hasPrompt = computed(() => !!item.value && (item.value.promptFormat || item.value.promptGenerate))

/** 文件图标（按扩展名粗分，与详情页既有 emoji 风格一致） */
const fileIcon = (ext) => {
  if (isAudioExt(ext)) return '🎵'
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].indexOf(ext) >= 0) return '🖼️'
  if (ext === 'pdf') return '📕'
  if (['doc', 'docx', 'txt', 'md'].indexOf(ext) >= 0) return '📄'
  return '📎'
}

/**
 * 「输入」区域的文件对象；非 URL 输入（工作总结等纯文本）返回 null，
 * 此时沿用原有纯文本展示，不做改造。
 * 文件名取值优先级：files 元数据 fileName → URL 末段 → 「查看文件」兜底。
 */
const inputFile = computed(() => {
  const raw = item.value && item.value.inputContent
  if (!raw) return null
  const s = String(raw).trim()
  // 只认整串就是一个 URL，避免把正文里内嵌链接的文本误判成文件
  if (!isFileUrl(s)) return null
  // 按 object key 匹配：签名 URL 每次查询重新签发（q-sign-time 变），完整 URL 必然不相等
  const k = fileKeyOf(s)
  const meta = files.value.find((f) => f && f.fileUrl && fileKeyOf(f.fileUrl) === k)
  const name = (meta && meta.fileName) || fileNameFromUrl(s) || '查看文件'
  const ext = extensionOf(name)
  const dot = name.lastIndexOf('.')
  const hasExt = dot > 0 && dot < name.length - 1
  return {
    url: s,
    ext,
    base: hasExt ? name.slice(0, dot) : name,
    suffix: hasExt ? name.slice(dot) : '',
    playable: isPlayableExt(ext)
  }
})

const inputFileAction = computed(() => (inputFile.value && inputFile.value.playable ? '试听' : '下载'))

/**
 * 输入文件点击：
 * - 音频：先下载到临时路径再 playVoice（小程序端 playVoice 只吃本地路径，直接传网络地址会失败），
 *   且 openDocument 不支持音频，试听体验明显更好；
 * - 其余：uni.downloadFile + uni.openDocument。
 */
const onInputFileTap = () => {
  const f = inputFile.value
  if (!f || !f.url) {
    uni.showToast({ title: '文件链接不可用', icon: 'none' })
    return
  }
  if (fileLoading.value) return
  fileLoading.value = true
  uni.showLoading({ title: f.playable ? '加载中…' : '下载中…', mask: true })
  uni.downloadFile({
    url: f.url,
    success: (res) => {
      // COS 私有签名 URL 有效期约 5 分钟，过期返回 403
      if (res.statusCode !== 200) {
        uni.showToast({ title: '下载失败（链接可能已过期）', icon: 'none' })
        return
      }
      if (f.playable) {
        uni.playVoice({
          src: res.tempFilePath,
          fail: () => uni.showToast({ title: '该音频无法播放', icon: 'none' })
        })
      } else {
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true,
          fail: () => uni.showToast({ title: '无法预览该文件', icon: 'none' })
        })
      }
    },
    fail: () => uni.showToast({ title: '下载失败', icon: 'none' }),
    complete: () => {
      uni.hideLoading()
      fileLoading.value = false
    }
  })
}

const getToolName = (it) => it.toolName || it.aiCode || '未知工具'

/** 超过该字符数才启用"固定高度 + 内部滚动"，短文本直接铺开（避免大片留白） */
const LONG_TEXT_THRESHOLD = 260
const isLong = (s) => !!s && String(s).length > LONG_TEXT_THRESHOLD

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 16)
}

/** 工具图标类型（与 history.vue 保持一致） */
const iconType = (it) => {
  const c = (it && it.aiCode) || ''
  if (c.includes('ocr') || c.includes('recognize') || c.includes('invoice') || c.includes('receipt')) return 'ocr'
  if (c.includes('image') || c.includes('photo') || c.includes('portrait') || c.includes('bg')) return 'image'
  if (c.includes('file-reader') || c.includes('doc')) return 'doc'
  if (c.includes('audio') || c.includes('transcribe') || c.includes('meeting')) return 'text'
  return 'text'
}
const emoji = (it) => {
  const m = { ocr: '🖨', image: '🖼️', doc: '📄', text: '📝', code: '✨' }
  return m[iconType(it)] || '📝'
}

const goBack = () => safeBack('/pages/history')

/** 按 id + aiCode 拉取记录（复用列表接口，避免新增后端端点） */
const fetchDetail = async (id, aiCode) => {
  if (!id) return
  loading.value = true
  try {
    const res = await historyListByToolApi(aiCode || undefined, 50)
    const list = (res && res.data) || []
    item.value = list.find((h) => String(h.id) === String(id)) || null
  } catch (e) {
    item.value = null
  } finally {
    loading.value = false
  }
}

onLoad((option) => {
  if (!requireLogin()) return
  fetchDetail(option.id, option.aiCode)
})

const onCopyResult = () => {
  const text = item.value && (statusOk.value ? item.value.outputContent : (item.value.errorMsg || ''))
  copyRaw(text, '', '没有可复制的内容')
}

/** 再次使用：跳转到对应工具页，带上本次记录 id，工具页自动回填参数 */
const onReuse = () => {
  if (!item.value) return
  const code = item.value.aiCode
  if (!code) {
    uni.showToast({ title: '该记录缺少工具信息，无法再次使用', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/tool-common?id=${code}&historyId=${item.value.id}` })
}

/** 打开附件（文件落库未实现，当前不会触发；后端补齐 key + 签名后可用） */
const onOpenFile = (f) => {
  if (!f || !f.fileUrl) {
    uni.showToast({ title: '文件链接不可用', icon: 'none' })
    return
  }
  // 签名 URL 需重新签发（后端在查询时实时生成），此处直接使用
  // #ifdef H5
  window.open(f.fileUrl, '_blank')
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url: f.fileUrl,
    success: (res) => {
      if (res.statusCode === 200) uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
    }
  })
  // #endif
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.hd-page {
  min-height: 100vh;
  background: #F9FAFB;
  padding-bottom: 40rpx;
}

/* 头部 */
.hd-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 32rpx 32rpx 16rpx;
}
.hd-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: -12rpx;
  border-radius: 50%;
}
.hd-back__icon { width: 44rpx; height: 44rpx; color: #111827; }
.hd-head__title {
  flex: 1;
  font-size: 48rpx;
  font-weight: 700;
  color: #111827;
}

/* 概览卡 */
.hd-hero {
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 28rpx 24rpx;
  margin: 0 32rpx 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.hd-hero__info { flex: 1; min-width: 0; }
.hd-hero__name {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #111827;
}
.hd-hero__meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 12rpx;
}
.hd-hero__time { font-size: 24rpx; color: #9CA3AF; }

/* 通用信息行 */
.hd-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  margin: 0 32rpx 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
}
.hd-row__k { font-size: 26rpx; color: #6B7280; }
.hd-row__v { font-size: 26rpx; color: #111827; }

/* 区块 */
.hd-block { margin: 0 32rpx 24rpx; }
.hd-block__title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12rpx;
}
.hd-box {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.hd-box--error { background: #FEF2F2; }
.hd-box__scroll {
  height: 480rpx;
  overflow-y: auto;
}
.hd-box__text {
  font-size: 26rpx;
  line-height: 1.7;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
}
.hd-box__text--error { color: #DC2626; }
.hd-box__empty { font-size: 26rpx; color: #9CA3AF; }

/* 输入文件（只展示文件名，不暴露签名 URL） */
.hd-inputfile {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 4rpx;
}
.hd-inputfile--hover { opacity: 0.6; }
.hd-inputfile__icon { font-size: 36rpx; }
.hd-inputfile__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.hd-inputfile__base { font-size: 26rpx; color: #2563EB; }
.hd-inputfile__ext { font-size: 26rpx; color: #93C5FD; }
.hd-inputfile__action { font-size: 26rpx; color: #2563EB; }

/* 提示词 */
.hd-prompt {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.hd-prompt__label {
  display: block;
  font-size: 24rpx;
  color: #2563EB;
  font-weight: 500;
  margin-bottom: 12rpx;
}
.hd-prompt__scroll {
  height: 320rpx;
  overflow-y: auto;
}
.hd-prompt__text {
  font-size: 26rpx;
  line-height: 1.7;
  color: #4B5563;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 附件 */
.hd-file {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.hd-file__icon { font-size: 32rpx; }
.hd-file__name {
  flex: 1;
  font-size: 26rpx;
  color: #111827;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.hd-file__action { font-size: 26rpx; color: #2563EB; }

/* 操作按钮 */
.hd-actions {
  display: flex;
  gap: 24rpx;
  margin: 40rpx 32rpx 0;
}
.hd-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 9999rpx;
  background: #fff;
  border: 2rpx solid #E5E7EB;
  font-size: 28rpx;
  color: #374151;
}
.hd-btn--primary {
  background: #3B82F6;
  border-color: #3B82F6;
  color: #fff;
  font-weight: 600;
}

.safe-bottom { height: 40rpx; }
</style>
