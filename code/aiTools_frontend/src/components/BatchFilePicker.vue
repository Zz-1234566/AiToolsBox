<template>
  <view class="batch-file-picker">
    <!-- 顶部：标题 + 副标题 -->
    <view class="batch-file-picker-header">
      <text class="batch-file-picker-title">{{ rule.title }}</text>
      <text class="batch-file-picker-desc">{{ rule.desc }}</text>
      <view v-if="rule.notice" class="batch-file-picker-notice">
        <text class="batch-file-picker-notice-text">{{ rule.notice }}</text>
      </view>
    </view>

    <!-- 中部：已选文件列表（按用户要求：列表在上，按钮在下） -->
    <view v-if="files.length" class="batch-file-list">
      <view v-for="(f, idx) in files" :key="idx" class="batch-file-item">
        <text class="batch-file-item-name">{{ f.fileName }}</text>
        <text class="batch-file-item-remove" @click="removeAt(idx)">×</text>
      </view>
    </view>

    <!-- 底部：添加文件按钮（按用户要求：按钮在文件列表下面） -->
    <button
      class="batch-file-picker-btn press-scale"
      :disabled="disabled"
      @click="onPick"
    >
      <text>{{ files.length > 0 ? '+ 添加文件（已选 ' + files.length + ' 个）' : '+ 添加文件' }}</text>
    </button>

    <text v-if="files.length === 0" class="batch-file-picker-hint">
      提示：点按钮每次选 1 个文件，可重复点选累加（最多 {{ rule.maxCount }} 个）
    </text>
  </view>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'

const props = defineProps({
  /**
   * 文件规则（来自 tools.js 的 tool.fileRule）
   * {
   *   accept: '.txt,.pdf,.docx'      必填
   *   maxCount: 10                    必填
   *   maxTotalSize: 200MB             必填
   *   title: '上传文档'               可选
   *   desc: '支持 PDF、Word...'        可选
   * }
   */
  rule: { type: Object, required: true },
  /** 父组件禁用（如 loading 状态） */
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['change'])

// 内部状态：当前已选文件列表 [{ fileName, filePath, file, size }]
const files = ref([])

// ============== 平台检测 ==============
// H5 端：用原生 <input type="file">，常驻 + 多次点选累加
// 小程序/APP 端：用 uni.chooseFile 一次选 N 个（H5 端不可用）
const isH5 = typeof window !== 'undefined' && typeof document !== 'undefined'

// ============== H5 端：常驻 hidden input ==============
// 每次点"+ 添加文件"复用同一个 input，change 后清 value
let h5FileInput = null

const h5FileChangeHandler = () => {
  const picked = Array.from(h5FileInput.files || [])
  const newOnes = picked.map((f) => ({
    fileName: f.name,
    filePath: '',
    file: f,
    size: f.size
  }))
  appendFiles(newOnes)
  h5FileInput.value = ''
}

const ensureH5Input = () => {
  if (h5FileInput) return h5FileInput
  h5FileInput = document.createElement('input')
  h5FileInput.type = 'file'
  h5FileInput.multiple = true       // 浏览器支持时一次可多选
  h5FileInput.accept = props.rule.accept
  h5FileInput.style.position = 'fixed'
  h5FileInput.style.top = '-9999px'
  h5FileInput.style.left = '-9999px'
  h5FileInput.style.opacity = '0'
  h5FileInput.style.pointerEvents = 'none'
  document.body.appendChild(h5FileInput)
  h5FileInput.addEventListener('change', h5FileChangeHandler)
  return h5FileInput
}

// ============== 触发选文件 ==============
const onPick = () => {
  if (props.disabled) return
  if (isH5) {
    ensureH5Input().click()
    return
  }
  // 小程序/APP 端
  const remain = props.rule.maxCount - files.value.length
  if (remain <= 0) {
    uni.showToast({ title: '已达最大数量', icon: 'none' })
    return
  }
  uni.chooseFile({
    count: remain,
    extension: props.rule.accept.split(',').map((s) => s.trim()).filter(Boolean),
    success: (res) => {
      const newOnes = (res.tempFiles || []).map((f) => ({
        fileName: f.name || (f.path ? f.path.split('/').pop() : '未命名'),
        filePath: f.path || (res.tempFilePaths && res.tempFilePaths[0]) || '',
        file: f,
        size: f.size || 0
      }))
      appendFiles(newOnes)
    },
    fail: () => { /* 用户取消 */ }
  })
}

// ============== 追加文件 + 校验 ==============
const appendFiles = (newOnes) => {
  if (!newOnes.length) return
  const merged = files.value.concat(newOnes)
  // 数量截断
  if (merged.length > props.rule.maxCount) {
    uni.showToast({ title: '最多 ' + props.rule.maxCount + ' 个文件，已截断', icon: 'none' })
    merged.splice(props.rule.maxCount)
  }
  // 大小校验（只校验新增部分，超限就整体回滚）
  const total = merged.reduce((s, f) => s + (f.size || 0), 0)
  if (total > props.rule.maxTotalSize) {
    uni.showToast({ title: '总大小超过限制', icon: 'none' })
    return
  }
  files.value = merged.slice()
  emit('change', files.value)
}

// ============== 移除单条 ==============
const removeAt = (idx) => {
  files.value.splice(idx, 1)
  files.value = files.value.slice()  // 强制新数组引用
  emit('change', files.value)
}

// ============== 暴露给父组件：拿当前文件列表 + 清空 ==============
// 通过 defineExpose 暴露（vue3 script setup 模式）
defineExpose({
  getFiles: () => files.value,
  clear: () => {
    files.value = []
    emit('change', files.value)
  }
})

// ============== 组件卸载：清理 H5 hidden input ==============
onBeforeUnmount(() => {
  if (h5FileInput) {
    h5FileInput.removeEventListener('change', h5FileChangeHandler)
    if (h5FileInput.parentNode) h5FileInput.parentNode.removeChild(h5FileInput)
    h5FileInput = null
  }
})
</script>

<style lang="scss" scoped>
.batch-file-picker {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  margin-bottom: $spacing-md;

  .batch-file-picker-header {
    margin-bottom: $spacing-sm;

    .batch-file-picker-title {
      display: block;
      font-size: $font-size-md;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: $spacing-xs;
    }

    .batch-file-picker-desc {
      display: block;
      font-size: $font-size-sm;
      color: $text-tertiary;
    }

    .batch-file-picker-notice {
      display: block;
      margin-top: $spacing-xs;
      padding: $spacing-xs $spacing-sm;
      background-color: $color_warning_bg;
      border-left: 6rpx solid $color_warning;
      border-radius: $radius-sm;

      .batch-file-picker-notice-text {
        font-size: $font-size-xs;
        color: $color_warning_text;
        line-height: 1.5;
        white-space: pre-wrap;
      }
    }
  }

  .batch-file-list {
    margin-top: $spacing-sm;
    margin-bottom: $spacing-sm;
  }

  .batch-file-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-sm $spacing-md;
    background-color: $bg-color;
    border-radius: $radius-sm;
    margin-bottom: $spacing-xs;

    .batch-file-item-name {
      flex: 1;
      font-size: $font-size-sm;
      color: $text-primary;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .batch-file-item-remove {
      width: 48rpx;
      height: 48rpx;
      line-height: 48rpx;
      text-align: center;
      font-size: 32rpx;
      color: $text-tertiary;
      margin-left: $spacing-sm;
    }
  }

  .batch-file-picker-btn {
    width: 100%;
    height: 80rpx;
    border-radius: $radius-md;
    background-color: $bg-color;
    color: $text-primary;
    font-size: $font-size-md;
    border: 1rpx dashed $divider-color;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: $spacing-xs;

    &[disabled] {
      opacity: 0.6;
    }
  }

  .batch-file-picker-hint {
    display: block;
    font-size: $font-size-sm;
    color: $text-tertiary;
    text-align: center;
    margin-top: $spacing-xs;
  }
}
</style>
