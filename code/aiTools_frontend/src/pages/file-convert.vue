<template>
  <view class="page">
    <!-- 顶栏：返回 + 标题 + 副标题（与其它工具页一致） -->
    <view class="topbar">
      <view class="topbar__row">
        <view class="topbar__back" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>
        </view>
        <text class="topbar__title">文件格式转换</text>
      </view>
      <text class="topbar__desc">一个文件，转换成你需要的格式</text>
    </view>

    <scroll-view scroll-y class="page-content">
      <!-- 1. 未选文件 -->
      <view v-if="!file" class="upload" @click="pickFile">
        <view class="upload__icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 16V4m0 0L8 8m4-4l4 4"/><path d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2"/>
          </svg>
        </view>
        <text class="upload__t">点击选择文件</text>
        <text class="upload__d">支持图片 / PDF / Word / PPT / 表格，单个不超过 50MB</text>
      </view>

      <!-- 2. 已选文件 -->
      <view v-else class="file-card">
        <view class="file-card__tag" :class="'file-card__tag--' + (file.category || 'doc')">{{ (file.ext || '').toUpperCase() }}</view>
        <view class="file-card__body">
          <text class="file-card__name">{{ file.name }}</text>
          <text class="file-card__size">{{ formatSize(file.size) }}</text>
        </view>
        <view class="file-card__x" @click="clearFile">×</view>
      </view>

      <!-- 3. 目标格式 -->
      <block v-if="file && state === 'pick'">
        <text class="sec-title">目标格式</text>
        <view class="groups">
          <view v-for="g in groupList" :key="g.key" class="group" :class="{ 'group--off': g.off }">
            <view class="group__head">
              <text class="group__name">{{ g.label }}</text>
              <text v-if="g.off" class="group__off">未启用</text>
            </view>
            <view class="pills">
              <view v-for="t in g.items" :key="t" class="pill"
                    :class="{ 'pill--on': target === t, 'pill--off': g.off || !allowed.includes(t) }"
                    @click="pickTarget(g, t)">{{ t.toUpperCase() }}</view>
            </view>
          </view>
        </view>
      </block>

      <!-- 4. 转换中 -->
      <view v-if="state === 'running'" class="state-card">
        <view class="ring">
          <view class="ring__spin" />
        </view>
        <text class="state-card__t">正在转换文件，请稍候…</text>
        <text class="state-card__s">{{ target ? target.toUpperCase() : '' }}</text>
        <text class="state-card__link" @click="cancelTip">取消任务</text>
      </view>

      <!-- 5. 成功 -->
      <view v-if="state === 'done'" class="state-card">
        <view class="ok-ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l4 4L19 7"/></svg>
        </view>
        <text class="state-card__t">转换完成</text>
        <view class="kv">
          <view class="kv__row"><text class="kv__k">原文件</text><text class="kv__v">{{ file.name }}</text></view>
          <view class="kv__row"><text class="kv__k">输出文件</text><text class="kv__v">{{ result.fileName }}</text></view>
          <view class="kv__row"><text class="kv__k">原大小</text><text class="kv__v">{{ formatSize(file.size) }}</text></view>
          <view class="kv__row"><text class="kv__k">输出大小</text><text class="kv__v">{{ formatSize(result.size) }}</text></view>
        </view>
        <view class="btn btn--primary" @click="download">下载文件</view>
        <text class="state-card__link" @click="restart">再转一个</text>
      </view>

      <!-- 6. 失败 -->
      <view v-if="state === 'fail'" class="state-card">
        <view class="fail-ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><path d="M12 7v7"/><circle cx="12" cy="17.5" r="1.2" fill="currentColor"/></svg>
        </view>
        <text class="state-card__t">转换失败</text>
        <text class="state-card__s">{{ errorMsg }}</text>
        <view class="btn btn--primary" @click="doConvert">重新转换</view>
      </view>
    </scroll-view>

    <!-- 底部主按钮 -->
    <view v-if="state === 'pick'" class="bottom-action">
      <view class="btn btn--primary" :class="{ 'btn--off': !target }" @click="doConvert">
        {{ file ? '开始转换' : '请先选择文件' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/api/request'
import { STORAGE_KEYS } from '@/utils/auth'
import { BASE_URL } from '@/config/env'

const GROUPS = [
  { key: 'image', label: '图片', items: ['jpg', 'png', 'webp', 'bmp', 'tiff'] },
  { key: 'pdf', label: 'PDF', items: ['png', 'jpg', 'docx', 'txt', 'md'] },
  { key: 'docx', label: 'Word', items: ['pdf', 'txt', 'html', 'md'] },
  { key: 'pptx', label: '演示文稿', items: ['pdf', 'png', 'jpg'] },
  { key: 'data', label: '表格 / 数据', items: ['csv', 'xlsx', 'json', 'txt'] },
  { key: 'av', label: '音视频', items: ['mp4', 'mp3'], off: true }
]

const file = ref(null)
const target = ref('')
const allowed = ref([])
const state = ref('pick')
const result = ref({ fileName: '', fileUrl: '', size: 0 })
const errorMsg = ref('')

const groupList = computed(() => GROUPS)

const goBack = () => uni.navigateBack()
const clearFile = () => { file.value = null; target.value = ''; state.value = 'pick' }
const restart = () => { file.value = null; target.value = ''; state.value = 'pick'; errorMsg.value = '' }

const pickTarget = (g, t) => {
  if (g.off) { uni.showToast({ title: '音视频转换未启用', icon: 'none' }); return }
  if (!allowed.value.includes(t)) { uni.showToast({ title: '该格式不支持此转换', icon: 'none' }); return }
  target.value = target.value === t ? '' : t
}

const formatSize = (n) => {
  if (!n) return '—'
  if (n < 1024) return n + 'B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(0) + 'KB'
  return (n / 1024 / 1024).toFixed(1) + 'MB'
}

/** 选文件（H5 用 uni.chooseFile，App 端提示暂不支持） */
const pickFile = () => {
  // #ifdef H5
  uni.chooseFile({
    count: 1,
    success: (res) => {
      const f = res.tempFiles && res.tempFiles[0]
      if (f) upload(f)
    },
    fail: () => {}
  })
  // #endif
  // #ifdef APP-PLUS
  // App 端：uni 无文档选择 API。先按图片处理（chooseImage 全平台可用），
  // 需要转 PDF/Word 时再引导用户去网页端。
  uni.chooseImage({
    count: 1,
    success: (res) => {
      const p = (res.tempFilePaths || [])[0]
      const f = (res.tempFiles || [])[0] || {}
      if (p) upload({ path: p, size: f.size || 0, name: f.name || '' })
    },
    fail: () => {}
  })
  // #endif
}

const upload = (f) => {
  uni.showLoading({ title: '上传中' })
  const token = uni.getStorageSync(STORAGE_KEYS.TOKEN)
  uni.uploadFile({
    url: BASE_URL() + '/api/file-convert/upload',
    filePath: f.path,
    name: 'file',
    header: { Authorization: 'Bearer ' + token },
    success: (res) => {
      uni.hideLoading()
      try {
        const d = JSON.parse(res.data)
        if (d.code !== 200) throw new Error(d.message || '上传失败')
        file.value = {
          token: d.data.token, name: d.data.name, size: d.data.size,
          ext: d.data.ext, category: d.data.category
        }
        allowed.value = d.data.targets || []
        state.value = 'pick'
      } catch (e) {
        uni.showToast({ title: e.message || '上传失败', icon: 'none' })
      }
    },
    fail: () => { uni.hideLoading(); uni.showToast({ title: '上传失败', icon: 'none' }) }
  })
}

const doConvert = async () => {
  if (!file.value) return pickFile()
  if (!target.value) return uni.showToast({ title: '请选择目标格式', icon: 'none' })
  state.value = 'running'
  try {
    const res = await request({ url: '/api/file-convert/do', method: 'POST', data: { token: file.value.token, targetFormat: target.value } })
    result.value = res.data
    state.value = 'done'
  } catch (e) {
    errorMsg.value = (e && e.message) || '文件加密、格式损坏或服务超时，请检查文件后重试'
    state.value = 'fail'
  }
}

const download = () => {
  // #ifdef H5
  window.open(result.value.fileUrl, '_blank')
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url: result.value.fileUrl,
    success: (r) => uni.openDocument({ filePath: r.tempFilePath, showMenu: true })
  })
  // #endif
}

const cancelTip = () => {
  state.value = 'pick'
  uni.showToast({ title: '已取消', icon: 'none' })
}

onLoad(() => { state.value = 'pick' })
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--bg-page); display: flex; flex-direction: column; }
.topbar { padding: calc(var(--status-bar-height, 0px) + 22rpx) 24rpx 0; background: var(--bg-card, #fff); }
.topbar__row { display: flex; align-items: center; gap: 12rpx; }
.topbar__back { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; color: var(--text-primary); }
.topbar__back svg { width: 40rpx; height: 40rpx; }
.topbar__title { font-size: 36rpx; font-weight: 700; color: var(--text-primary); line-height: 44rpx; }
.topbar__desc { display: block; margin: 8rpx 0 22rpx 62rpx; font-size: 23rpx; color: var(--text-tertiary); }
.page-content { flex: 1; padding: 24rpx 0 180rpx; }

.upload {
  margin: 0 32rpx; padding: 80rpx 32rpx; border: 2rpx dashed var(--border-color);
  border-radius: 24rpx; background: var(--bg-card, #fff);
  display: flex; flex-direction: column; align-items: center; gap: 16rpx;
}
.upload__icon { width: 96rpx; height: 96rpx; color: var(--text-tertiary); }
.upload__icon svg { width: 100%; height: 100%; }
.upload__t { font-size: 30rpx; font-weight: 600; color: var(--text-primary); }
.upload__d { font-size: 22rpx; color: var(--text-tertiary); text-align: center; }

.file-card {
  margin: 0 32rpx 24rpx; padding: 24rpx; border-radius: 20rpx;
  background: var(--bg-card, #fff); display: flex; align-items: center; gap: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(60,55,100,.04);
}
.file-card__tag {
  width: 96rpx; height: 72rpx; border-radius: 14rpx; display: flex; align-items: center; justify-content: center;
  font-size: 22rpx; font-weight: 700; color: #fff; background: #4a7cf6;
}
.file-card__tag--image { background: #22a06b; }
.file-card__tag--pdf { background: #d94a3d; }
.file-card__tag--docx { background: #2b6cb0; }
.file-card__tag--pptx { background: #d0691e; }
.file-card__body { flex: 1; min-width: 0; }
.file-card__name { display: block; font-size: 28rpx; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-card__size { display: block; margin-top: 6rpx; font-size: 22rpx; color: var(--text-tertiary); }
.file-card__x { font-size: 34rpx; color: var(--text-tertiary); padding: 0 8rpx; }

.sec-title { display: block; margin: 0 32rpx 16rpx; font-size: 30rpx; font-weight: 700; color: var(--text-primary); }
.groups { display: flex; flex-direction: column; gap: 20rpx; }
.group { margin: 0 32rpx; padding: 24rpx; border-radius: 20rpx; background: var(--bg-card, #fff); }
.group--off { opacity: .55; }
.group__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.group__name { font-size: 28rpx; font-weight: 600; color: var(--text-primary); }
.group__off { font-size: 22rpx; color: var(--text-tertiary); }
.pills { display: flex; flex-wrap: wrap; gap: 14rpx; }
.pill {
  padding: 12rpx 26rpx; border-radius: 999rpx; background: var(--bg-gray);
  font-size: 24rpx; color: var(--text-secondary);
}
.pill--on { background: var(--brand-primary); color: #fff; }
.pill--off { opacity: .45; }

.state-card {
  margin: 0 32rpx; padding: 60rpx 32rpx; border-radius: 24rpx; background: var(--bg-card, #fff);
  display: flex; flex-direction: column; align-items: center; gap: 20rpx;
}
.ring__spin {
  width: 160rpx; height: 160rpx; border-radius: 50%;
  border: 10rpx solid var(--bg-gray); border-top-color: var(--brand-primary);
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.ok-ico, .fail-ico {
  width: 120rpx; height: 120rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.ok-ico { background: #2fa84f; }
.fail-ico { background: #e5484d; }
.ok-ico svg, .fail-ico svg { width: 60rpx; height: 60rpx; }
.state-card__t { font-size: 34rpx; font-weight: 700; color: var(--text-primary); }
.state-card__s { font-size: 23rpx; color: var(--text-tertiary); text-align: center; line-height: 1.6; }
.state-card__link { font-size: 24rpx; color: var(--brand-primary); margin-top: 8rpx; }
.kv { width: 100%; background: var(--bg-gray); border-radius: 16rpx; padding: 20rpx 24rpx; }
.kv__row { display: flex; justify-content: space-between; padding: 8rpx 0; }
.kv__k { font-size: 23rpx; color: var(--text-tertiary); }
.kv__v { font-size: 23rpx; color: var(--text-primary); max-width: 60%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.bottom-action {
  position: fixed; left: 0; right: 0; bottom: 0; padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom, 0rpx));
  background: var(--bg-card, #fff);
}
.btn { width: 100%; height: 92rpx; line-height: 92rpx; text-align: center; border-radius: 46rpx; font-size: 30rpx; font-weight: 600; }
.btn--primary { background: var(--brand-primary); color: #fff; }
.btn--off { opacity: .5; }
</style>
