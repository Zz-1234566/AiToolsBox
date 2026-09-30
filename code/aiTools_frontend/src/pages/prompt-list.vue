<template>
  <view class="pl-page">
    <!-- 头部 -->
    <view class="pl-head">
      <view class="pl-back" @click="goBack">
        <svg class="pl-back__icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M15 19L8 12L15 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </view>
      <text class="pl-head__title">提示词</text>
      <view class="pl-add" v-if="activeTab === 'mine' || adminMode" @click="openAddModal">
        <text>＋ 新建</text>
      </view>
    </view>

    <!-- 系统 / 我的 -->
    <view class="pl-tabs">
      <text class="pl-tab" :class="{ 'pl-tab--active': activeTab === 'system' }" @click="switchTab('system')">系统</text>
      <text class="pl-tab" :class="{ 'pl-tab--active': activeTab === 'mine' }" @click="switchTab('mine')">我的</text>
    </view>

    <!-- 工具筛选（可横向滚动） -->
    <scroll-view scroll-x class="pl-toolbar" :show-scrollbar="false">
      <view v-for="t in flatTools" :key="t.toolCode" class="pl-toolchip"
            :class="{ 'pl-toolchip--on': selectedToolCode === t.toolCode }"
            @click="onPickTool(t)">
        {{ t.toolName }}
      </view>
    </scroll-view>

    <!-- 生成提示词 / 格式提示词（二级 tab） -->
    <view class="pl-subtabs">
      <text class="pl-subtab" :class="{ 'pl-subtab--active': activeUse === 'generate' }" @click="switchUse('generate')">生成提示词</text>
      <text class="pl-subtab" :class="{ 'pl-subtab--active': activeUse === 'format' }" @click="switchUse('format')">格式提示词</text>
    </view>

    <!-- 列表 -->
    <view v-if="loading" class="redesign-empty"><text class="redesign-empty__text">加载中…</text></view>
    <view v-else-if="filteredList.length === 0" class="redesign-empty">
      <view class="redesign-empty__icon"><text class="redesign-empty__emoji">📝</text></view>
      <text class="redesign-empty__text">{{ emptyText }}</text>
    </view>

    <block v-else>
      <view v-for="item in filteredList" :key="item.id" class="pl-card">
        <view class="tool-avatar" :class="'tool-avatar--' + iconType(item.toolCode)">
          <text class="ta-emoji">{{ emoji(item.toolCode) }}</text>
        </view>
        <view class="pl-card__body">
          <text class="pl-card__title">{{ item.promptName || '未命名' }}</text>
          <view class="pl-card__tags">
            <text class="redesign-tag">{{ toolNameOf(item) }}</text>
            <text class="redesign-tag">{{ item.promptUse === 'format' ? '格式' : '生成内容' }}</text>
          </view>
          <text class="pl-card__preview">{{ item.promptText }}</text>
          <text class="pl-card__time">更新于 {{ formatTime(item.createTime) }}</text>
          <!-- 「我的」可编辑/删除；「系统」仅管理员可编辑/删除 -->
          <view v-if="activeTab === 'mine' || adminMode" class="pl-card__acts">
            <text class="pl-act pl-act--primary" @click="openEditModal(item)">编辑</text>
            <view class="pl-act__sep"></view>
            <text class="pl-act pl-act--danger" @click="onDelete(item)">删除</text>
          </view>
          <view v-else class="pl-card__readonly">
            <text class="pl-card__readonly-text">系统预制 · 只读</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 新增/编辑弹层 -->
    <view v-if="showModal" class="modal-mask" @click="closeModal">
      <view class="modal-card" @click.stop>
        <text class="modal-title">{{ modalTitle }}</text>

        <text class="modal-label">名称</text>
        <input class="modal-input" v-model="modalName" placeholder="给提示词起个名字" placeholder-class="pl-ph" :maxlength="32" />

        <text class="modal-label">类型</text>
        <view class="modal-seg">
          <text class="modal-seg__item" :class="{ 'modal-seg__item--on': modalUse === 'generate' }" @click="modalUse = 'generate'">生成内容</text>
          <text class="modal-seg__item" :class="{ 'modal-seg__item--on': modalUse === 'format' }" @click="modalUse = 'format'">格式</text>
        </view>

        <text class="modal-label">内容</text>
        <textarea class="modal-textarea" v-model="modalText" placeholder="请输入提示词内容..." placeholder-class="pl-ph" :maxlength="2000" />

        <view class="modal-actions">
          <view class="modal-btn" @click="closeModal">取消</view>
          <view class="modal-btn modal-btn--primary" @click="saveModal">保存</view>
        </view>
      </view>
    </view>

    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { promptListApi, promptAddApi, promptUpdateApi, promptDeleteApi, toolListApi, systemPromptListApi, systemPromptAddApi, systemPromptUpdateApi, systemPromptDeleteApi } from '@/api/prompt'
import { requireLogin, isAdmin } from '@/utils/auth'
import { safeBack } from '@/utils/pageTransition'
import { REALIZED_TOOLS, TOOLS } from '@/config/tools'

const loading = ref(false)
/** 当前用户是否管理员（仅控制前端显隐，权限由后端校验） */
const adminMode = ref(false)
const promptList = ref([])
const systemList = ref([])
const activeTab = ref('mine')
/** 二级 tab：提示词用途（generate 生成内容 / format 格式） */
const activeUse = ref('generate')
const flatTools = ref([])
const selectedToolCode = ref('')

const selectedToolName = computed(() => {
  const t = TOOLS[selectedToolCode.value]
  return t ? t.name : ''
})

const toolNameOf = (item) => {
  if (item.toolName) return item.toolName
  const t = TOOLS[item.toolCode]
  return t ? t.name : (item.toolCode || '')
}

/** 按用途过滤（前端过滤） */
const filteredList = computed(() => {
  const src = activeTab.value === 'system' ? systemList.value : promptList.value
  // 用途过滤：generate 生成内容 / format 格式
  const use = activeUse.value
  return src.filter(i => {
    const u = i.promptUse === 'format' ? 'format' : 'generate'
    return u === use
  })
})

/** 空态文案：区分 系统/我的 × 生成提示词/格式提示词 */
const emptyText = computed(() => {
  const useLabel = activeUse.value === 'format' ? '格式提示词' : '生成提示词'
  if (activeTab.value === 'system') return `该工具暂无${useLabel}`
  return `还没有${useLabel}`
})

const iconType = (code) => {
  const c = code || ''
  if (c.includes('ocr') || c.includes('recognize')) return 'ocr'
  if (c.includes('image') || c.includes('photo') || c.includes('qr')) return 'image'
  if (c.includes('doc') || c.includes('file-reader')) return 'doc'
  return 'text'
}
const emoji = (code) => {
  const m = { ocr: '🖨', image: '🖼️', doc: '📄', text: '📝' }
  return m[iconType(code)] || '📝'
}

const showModal = ref(false)
const modalTitle = ref('')
const modalText = ref('')
const modalName = ref('')
const modalUse = ref('generate')
const editingId = ref(null)

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 16)
}

/** 返回：栈内有上一页则返回，否则回「我的」页（本页入口来源） */
const goBack = () => safeBack('/pages/my')

const fetchToolList = async () => {
  try {
    const res = await toolListApi()
    const list = (res && res.data) || []
    flatTools.value = list.map(t => ({ toolCode: t.toolCode, toolName: t.toolName }))
  } catch (e) {
    flatTools.value = []
  }
  if (!selectedToolCode.value) {
    const firstRealized = REALIZED_TOOLS[0]
    const firstAvailable = flatTools.value[0]
    selectedToolCode.value = firstRealized || (firstAvailable && firstAvailable.toolCode) || ''
  }
}

const onPickTool = (tool) => {
  selectedToolCode.value = tool.toolCode
  fetchList()
}

const switchTab = (t) => {
  activeTab.value = t
  fetchList()
}

/** 切换二级 tab（用途）——本地过滤，无需重新请求 */
const switchUse = (u) => {
  activeUse.value = u
}

const fetchList = async () => {
  if (!selectedToolCode.value) {
    promptList.value = []
    systemList.value = []
    return
  }
  loading.value = true
  try {
    if (activeTab.value === 'system') {
      const res = await systemPromptListApi(selectedToolCode.value)
      systemList.value = res.data || []
    } else {
      const res = await promptListApi(selectedToolCode.value)
      promptList.value = res.data || []
    }
  } catch (err) {
    if (activeTab.value === 'system') systemList.value = []
    else promptList.value = []
  } finally {
    loading.value = false
  }
}

onShow(async () => {
  if (!requireLogin()) return
  adminMode.value = isAdmin()
  await fetchToolList()
  fetchList()
})

const openAddModal = () => {
  // 系统 tab 下仅管理员可新增
  if (activeTab.value === 'system' && !isAdmin()) {
    uni.showToast({ title: '系统提示词不可新增', icon: 'none' })
    return
  }
  showModal.value = true
  modalTitle.value = activeTab.value === 'system' ? '新增系统提示词' : '新增提示词'
  editingId.value = null
  modalText.value = ''
  modalName.value = ''
  modalUse.value = activeUse.value
}

const openEditModal = (item) => {
  // 系统 tab 下仅管理员可编辑
  if (activeTab.value === 'system' && !isAdmin()) {
    uni.showToast({ title: '仅管理员可编辑系统提示词', icon: 'none' })
    return
  }
  showModal.value = true
  modalTitle.value = activeTab.value === 'system' ? '编辑系统提示词' : '编辑提示词'
  editingId.value = item.id
  modalText.value = item.promptText || ''
  modalName.value = item.promptName || ''
  modalUse.value = item.promptUse === 'format' ? 'format' : 'generate'
}

const closeModal = () => {
  showModal.value = false
  modalText.value = ''
  modalName.value = ''
  editingId.value = null
  modalUse.value = 'generate'
}

const saveModal = async () => {
  const text = modalText.value.trim()
  const name = modalName.value.trim()
  if (!text) {
    uni.showToast({ title: '请输入提示词内容', icon: 'none' })
    return
  }
  if (!name) {
    uni.showToast({ title: '请输入提示词名称', icon: 'none' })
    return
  }
  if (!selectedToolCode.value) {
    uni.showToast({ title: '请先选择所属工具', icon: 'none' })
    return
  }
  try {
    if (activeTab.value === 'system') {
      // 系统提示词：仅管理员（后端二次校验）
      if (editingId.value) {
        await systemPromptUpdateApi(editingId.value, text, modalUse.value, selectedToolCode.value, name)
        uni.showToast({ title: '修改成功', icon: 'none' })
      } else {
        await systemPromptAddApi(text, modalUse.value, selectedToolCode.value, name)
        uni.showToast({ title: '新增成功', icon: 'none' })
      }
    } else if (editingId.value) {
      await promptUpdateApi(editingId.value, text, modalUse.value, selectedToolCode.value, name)
      uni.showToast({ title: '修改成功', icon: 'none' })
    } else {
      await promptAddApi(text, modalUse.value, selectedToolCode.value, name)
      uni.showToast({ title: '新增成功', icon: 'none' })
    }
    closeModal()
    fetchList()
  } catch (err) {
    // request.js 已统一提示错误（同名将提示"该工具下已存在同名提示词"）
  }
}

const onDelete = (item) => {
  const isSystem = activeTab.value === 'system'
  if (isSystem && !isAdmin()) {
    uni.showToast({ title: '仅管理员可删除系统提示词', icon: 'none' })
    return
  }
  uni.showModal({
    title: isSystem ? '删除系统提示词' : '删除提示词',
    content: isSystem
      ? '确定要删除这条【系统预制】提示词吗？删除后所有用户将无法再选用。'
      : '确定要删除这条提示词吗？',
    confirmColor: '#211E1E',
    success: async (res) => {
      if (!res.confirm) return
      try {
        if (isSystem) {
          await systemPromptDeleteApi(item.id)
          systemList.value = systemList.value.filter((p) => p.id !== item.id)
        } else {
          await promptDeleteApi(item.id)
          promptList.value = promptList.value.filter((p) => p.id !== item.id)
        }
        uni.showToast({ title: '删除成功', icon: 'none' })
      } catch (err) {
        // request.js 已统一提示错误
      }
    }
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.pl-page {
  min-height: 100vh;
  background: #F9FAFB;
  padding-bottom: 40rpx;
}

.pl-head {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 32rpx 32rpx 16rpx;
}
.pl-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: -12rpx;
  border-radius: 50%;
}
.pl-back__icon {
  width: 44rpx;
  height: 44rpx;
  color: #111827;
}
.pl-head__title {
  flex: 1;
  font-size: 48rpx;
  font-weight: 700;
  color: #111827;
}
.pl-add {
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

.pl-tabs {
  display: flex;
  gap: 40rpx;
  padding: 0 32rpx;
  border-bottom: 2rpx solid #F3F4F6;
}
.pl-tab {
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #9CA3AF;
  position: relative;
}
.pl-tab--active { color: #111827; font-weight: 600; }
.pl-tab--active::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: -2rpx;
  height: 4rpx;
  background: #3B82F6;
}

/* 二级 tab：生成内容 / 格式（胶囊样式，与一级下划线 tab 区分） */
.pl-subtabs {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 32rpx 0;
}
.pl-subtab {
  flex: 1;
  text-align: center;
  height: 56rpx;
  line-height: 56rpx;
  border-radius: 9999rpx;
  background: #F3F4F6;
  font-size: 24rpx;
  color: #6B7280;
}
.pl-subtab--active {
  background: #EFF6FF;
  color: #2563EB;
  font-weight: 600;
}

.pl-toolbar {
  white-space: nowrap;
  padding: 24rpx 32rpx 8rpx;
}
.pl-toolchip {
  display: inline-block;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 24rpx;
  margin-right: 16rpx;
  border-radius: 9999rpx;
  background: #fff;
  border: 2rpx solid #F3F4F6;
  font-size: 24rpx;
  color: #4B5563;
}
.pl-toolchip--on {
  background: #EFF6FF;
  border-color: #BFDBFE;
  color: #2563EB;
  font-weight: 500;
}

.pl-ph { color: #9CA3AF; }

.pl-card {
  display: flex;
  gap: 24rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin: 0 32rpx 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.pl-card__body { flex: 1; min-width: 0; }
.pl-card__title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 12rpx;
}
.pl-card__tags { display: flex; gap: 12rpx; margin-bottom: 12rpx; }
.pl-card__preview {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 24rpx;
  color: #9CA3AF;
  line-height: 1.5;
}
.pl-card__time {
  display: block;
  font-size: 22rpx;
  color: #D1D5DB;
  margin-top: 12rpx;
}
.pl-card__acts {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24rpx;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 2rpx solid #F3F4F6;
}
.pl-act { font-size: 26rpx; color: #4B5563; }
.pl-act--primary { color: #3B82F6; }
.pl-act--danger { color: #EF4444; }
.pl-act__sep { width: 2rpx; height: 28rpx; background: #F3F4F6; }

/* 非管理员查看系统提示词时的只读标记 */
.pl-card__readonly {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 2rpx solid #F3F4F6;
  display: flex;
  justify-content: flex-end;
}
.pl-card__readonly-text {
  font-size: 22rpx;
  color: #9CA3AF;
}

/* 弹层 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 1000;
}
.modal-card {
  width: 100%;
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 40rpx 32rpx 48rpx;
  box-sizing: border-box;
}
.modal-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: #111827;
  margin-bottom: 24rpx;
  text-align: center;
}
.modal-label {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 12rpx;
}
.modal-input {
  height: 88rpx;
  padding: 0 24rpx;
  border: 2rpx solid #E5E7EB;
  border-radius: 16rpx;
  font-size: 28rpx;
  margin-bottom: 24rpx;
  box-sizing: border-box;
  width: 100%;
}
.modal-seg { display: flex; gap: 16rpx; margin-bottom: 24rpx; }
.modal-seg__item {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 16rpx;
  background: #F3F4F6;
  color: #4B5563;
  font-size: 26rpx;
}
.modal-seg__item--on {
  background: #EFF6FF;
  color: #2563EB;
  font-weight: 600;
}
.modal-textarea {
  width: 100%;
  min-height: 200rpx;
  padding: 20rpx 24rpx;
  border: 2rpx solid #E5E7EB;
  border-radius: 16rpx;
  font-size: 28rpx;
  box-sizing: border-box;
  margin-bottom: 32rpx;
}
.modal-actions { display: flex; gap: 24rpx; }
.modal-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: 9999rpx;
  background: #F3F4F6;
  color: #374151;
  font-size: 28rpx;
}
.modal-btn--primary {
  background: #3B82F6;
  color: #fff;
  font-weight: 600;
}

.safe-bottom { height: 40rpx; }
</style>
