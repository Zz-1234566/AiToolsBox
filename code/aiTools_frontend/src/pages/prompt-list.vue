<template>
  <view class="page-container">
    <page-header title="我的提示词" showBack></page-header>

    <view class="page-content">
      <!-- 工具筛选下拉（按 tool_type 分组） -->
      <view class="tool-filter">
        <view class="tool-filter-label">所属工具</view>
        <view class="tool-filter-select press-scale" @click="showToolPicker = true">
          <text class="tool-filter-name">{{ selectedToolName || '请选择工具' }}</text>
          <svg class="tool-filter-arrow" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M6 9L12 15L18 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </view>
      </view>

      <view class="add-btn press-scale" @click="openAddModal">
        <svg class="add-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 5V19" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          <path d="M5 12H19" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <text class="add-text">新增提示词</text>
      </view>

      <!-- 空状态 -->
      <view v-if="!loading && promptList.length === 0" class="empty-state">
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M4 5C4 3.89543 4.89543 3 6 3H18C19.1046 3 20 3.89543 20 5V15C20 16.1046 19.1046 17 18 17H11L6 21V17H6C4.89543 17 4 16.1046 4 15V5Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <text class="empty-text">还没有提示词</text>
      </view>

      <!-- 提示词列表 -->
      <view v-else class="prompt-list">
        <view
          v-for="item in promptList"
          :key="item.id"
          class="prompt-item animate-fade-in-up"
        >
          <view class="item-main">
            <text class="item-text">{{ item.promptText }}</text>
            <view class="item-meta">
              <text class="item-tag">{{ item.promptUse === 'format' ? '格式' : '生成内容' }}</text>
              <text v-if="toolNameOf(item)" class="item-tool-tag">{{ toolNameOf(item) }}</text>
              <text class="item-time">{{ formatTime(item.createTime) }}</text>
            </view>
          </view>
          <view class="item-actions">
            <view class="action-btn press-scale" @click.stop="openEditModal(item)">
              <svg class="action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M17 3C17.5523 2.44772 18.4477 2.44772 19 3L21 5C21.5523 5.55228 21.5523 6.44772 21 7L8 20L4 21L5 17L17 3Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </view>
            <view class="action-btn press-scale" @click.stop="onDelete(item)">
              <svg class="action-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M3 6H21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                <path d="M19 6V20C19 20.5523 18.5523 21 18 21H6C5.44772 21 5 20.5523 5 20V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </view>
          </view>
        </view>
      </view>

      <view class="safe-area-bottom"></view>
    </view>

    <!-- 新增/编辑弹窗 -->
    <view v-if="showModal" class="modal-mask animate-fade-in" @click="closeModal">
      <view class="modal-content animate-scale-in" @click.stop>
        <text class="modal-title">{{ modalTitle }}</text>
        <view class="modal-tool-row">
          <text class="modal-tool-label">所属工具</text>
          <text class="modal-tool-name">{{ selectedToolName || '未选择' }}</text>
        </view>
        <view class="modal-type-row">
          <view
            class="modal-type-btn press-scale"
            :class="{ active: modalUse === 'format' }"
            @click="modalUse = 'format'"
          >格式</view>
          <view
            class="modal-type-btn press-scale"
            :class="{ active: modalUse === 'generate' }"
            @click="modalUse = 'generate'"
          >生成内容</view>
        </view>
        <textarea
          class="modal-input"
          v-model="modalText"
          placeholder="请输入提示词内容"
          maxlength="2000"
        ></textarea>
        <view class="modal-buttons">
          <view class="modal-btn cancel-btn press-scale" @click="closeModal">取消</view>
          <view class="modal-btn confirm-btn press-scale" @click="saveModal">保存</view>
        </view>
      </view>
    </view>

    <!-- 工具选择弹窗（按 tool_type 分组） -->
    <view v-if="showToolPicker" class="modal-mask animate-fade-in" @click="showToolPicker = false">
      <view class="modal-content tool-picker-content animate-scale-in" @click.stop>
        <text class="modal-title">选择所属工具</text>
        <scroll-view scroll-y class="tool-picker-list">
          <view v-for="group in toolGroups" :key="group.toolType" class="tool-picker-group">
            <text class="tool-picker-group-title">{{ group.toolType }}</text>
            <view
              v-for="tool in group.tools"
              :key="tool.toolCode"
              class="tool-picker-item press-scale"
              :class="{ active: selectedToolCode === tool.toolCode }"
              @click="onPickTool(tool)"
            >
              <text class="tool-picker-item-text">{{ tool.toolName }}</text>
            </view>
          </view>
          <view v-if="toolGroups.length === 0" class="prompt-empty">
            <text>暂无可用工具</text>
          </view>
        </scroll-view>
        <view class="modal-buttons">
          <view class="modal-btn cancel-btn press-scale" @click="showToolPicker = false">关闭</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '@/components/PageHeader.vue'
import { promptListApi, promptAddApi, promptUpdateApi, promptDeleteApi, toolListApi } from '@/api/prompt'
import { requireLogin } from '@/utils/auth'
import { REALIZED_TOOLS, TOOLS } from '@/config/tools'

const loading = ref(false)
const promptList = ref([])
const showToolPicker = ref(false)
const toolGroups = ref([]) // [{ toolType, tools: [{ toolCode, toolName }] }]
// 工具筛选下拉：按 tool_type 分组展示，默认选第一个已实现工具
const selectedToolCode = ref('')

// 当前选中工具的名称（用于标题/标签展示）
const selectedToolName = computed(() => {
  const t = TOOLS[selectedToolCode.value]
  return t ? t.name : ''
})

// 列表项所属工具名称（优先用接口返回的 toolName，回退到本地配置）
const toolNameOf = (item) => {
  if (item.toolName) return item.toolName
  const t = TOOLS[item.toolCode]
  return t ? t.name : ''
}

const showModal = ref(false)
const modalTitle = ref('')
const modalText = ref('')
const modalUse = ref('generate') // 类型：format 格式 / generate 生成内容
const editingId = ref(null)

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 16)
}

const fetchToolList = async () => {
  try {
    const res = await toolListApi()
    const list = (res && res.data) || []
    // 兼容两种返回结构：[{ toolType, tools: [...] }] 或扁平 [{ toolCode, toolType, toolName }]
    if (list.length && Array.isArray(list[0].tools)) {
      toolGroups.value = list
    } else {
      const map = new Map()
      for (const t of list) {
        const type = t.toolType || '其他'
        if (!map.has(type)) map.set(type, [])
        map.get(type).push({ toolCode: t.toolCode, toolName: t.toolName })
      }
      toolGroups.value = Array.from(map, ([toolType, tools]) => ({ toolType, tools }))
    }
  } catch (e) {
    toolGroups.value = []
  }
  // 默认选第一个已实现工具，回退到下拉首个工具
  if (!selectedToolCode.value) {
    const firstRealized = REALIZED_TOOLS[0]
    const firstAvailable = toolGroups.value.flatMap(g => g.tools)[0]
    selectedToolCode.value = firstRealized || (firstAvailable && firstAvailable.toolCode) || ''
  }
}

const onToolChange = () => {
  fetchList()
}

const onPickTool = (tool) => {
  selectedToolCode.value = tool.toolCode
  showToolPicker.value = false
  fetchList()
}

const fetchList = async () => {
  if (!selectedToolCode.value) {
    promptList.value = []
    return
  }
  loading.value = true
  try {
    const res = await promptListApi(selectedToolCode.value)
    promptList.value = res.data || []
  } catch (err) {
    // request.js 已统一提示错误，这里清空列表避免残留旧数据
    promptList.value = []
  } finally {
    loading.value = false
  }
}

onShow(async () => {
  if (!requireLogin()) return
  await fetchToolList()
  fetchList()
})

const openAddModal = () => {
  showModal.value = true
  modalTitle.value = '新增提示词'
  editingId.value = null
  modalText.value = ''
  modalUse.value = 'generate'
}

const openEditModal = (item) => {
  showModal.value = true
  modalTitle.value = '编辑提示词'
  editingId.value = item.id
  modalText.value = item.promptText || ''
  modalUse.value = item.promptUse === 'format' ? 'format' : 'generate'
}

const closeModal = () => {
  showModal.value = false
  modalText.value = ''
  editingId.value = null
  modalUse.value = 'generate'
}

const saveModal = async () => {
  const text = modalText.value.trim()
  if (!text) {
    uni.showToast({ title: '请输入提示词内容', icon: 'none' })
    return
  }
  if (!selectedToolCode.value) {
    uni.showToast({ title: '请先选择所属工具', icon: 'none' })
    return
  }
  try {
    if (editingId.value) {
      await promptUpdateApi(editingId.value, text, modalUse.value, selectedToolCode.value)
      uni.showToast({ title: '修改成功', icon: 'none' })
    } else {
      await promptAddApi(text, modalUse.value, selectedToolCode.value)
      uni.showToast({ title: '新增成功', icon: 'none' })
    }
    closeModal()
    fetchList()
  } catch (err) {
    // request.js 已统一提示错误
  }
}

const onDelete = (item) => {
  uni.showModal({
    title: '删除提示词',
    content: '确定要删除这条提示词吗？',
    confirmColor: '#211E1E',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await promptDeleteApi(item.id)
        promptList.value = promptList.value.filter((p) => p.id !== item.id)
        uni.showToast({ title: '删除成功', icon: 'none' })
      } catch (err) {
        // request.js 已统一提示错误
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100vh;
  background-color: $bg-color;
  display: flex;
  flex-direction: column;
}

.page-content {
  flex: 1;
  padding: 0 $spacing-md;
}

.add-btn {
  margin-top: $spacing-md;
  height: 88rpx;
  border-radius: $radius-lg;
  background-color: $bg-white;
  border: 1rpx solid $border-color;
  box-shadow: $shadow-card;
  display: flex;
  align-items: center;
  justify-content: center;

  .add-icon {
    width: 36rpx;
    height: 36rpx;
    color: $text-primary;
    margin-right: $spacing-xs;
  }

  .add-text {
    font-size: $font-size-md;
    font-weight: 600;
    color: $text-primary;
  }
}

.empty-state {
  padding: $spacing-xl * 3 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  .empty-icon {
    width: 96rpx;
    height: 96rpx;
    color: $text-tertiary;
    margin-bottom: $spacing-md;
  }

  .empty-text {
    font-size: $font-size-md;
    color: $text-tertiary;
  }
}

.prompt-list {
  padding-top: $spacing-md;
}

.prompt-item {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-card;
  display: flex;
  align-items: flex-start;

  .item-main {
    flex: 1;
    min-width: 0;
    margin-right: $spacing-sm;

    .item-text {
      display: block;
      font-size: $font-size-sm;
      color: $text-primary;
      line-height: 1.6;
      word-break: break-all;
      white-space: pre-wrap;
      margin-bottom: $spacing-xs;
    }

    .item-meta {
      display: flex;
      align-items: center;

      .item-tag {
        font-size: $font-size-xs;
        color: $text-secondary;
        background-color: $bg-gray;
        border-radius: $radius-pill;
        padding: 2rpx 14rpx;
        margin-right: $spacing-xs;
      }

      .item-time {
        font-size: $font-size-xs;
        color: $text-tertiary;
      }
    }
  }

  .item-actions {
    display: flex;
    flex-shrink: 0;

    .action-btn {
      width: 64rpx;
      height: 64rpx;
      border-radius: $radius-pill;
      background-color: $bg-gray;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-left: $spacing-xs;

      .action-icon {
        width: 32rpx;
        height: 32rpx;
        color: $text-secondary;
      }
    }
  }
}

.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;

  .modal-content {
    width: 600rpx;
    background-color: $bg-white;
    border-radius: $radius-lg;
    padding: $spacing-lg;
    display: flex;
    flex-direction: column;

    .modal-title {
      font-size: $font-size-lg;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: $spacing-md;
    }

    .modal-type-row {
      display: flex;
      margin-bottom: $spacing-md;

      .modal-type-btn {
        flex: 1;
        height: 72rpx;
        border-radius: $radius-pill;
        background-color: $bg-gray;
        color: $text-secondary;
        font-size: $font-size-sm;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-right: $spacing-sm;

        &:last-child {
          margin-right: 0;
        }

        &.active {
          background-color: #211E1E;
          color: #FFFFFF;
        }
      }
    }

    .modal-input {
      width: 100%;
      box-sizing: border-box;
      min-height: 200rpx;
      max-height: 400rpx;
      background-color: $bg-gray;
      border-radius: $radius-md;
      padding: $spacing-sm $spacing-md;
      font-size: $font-size-sm;
      color: $text-primary;
      line-height: 1.6;
    }

    .modal-buttons {
      display: flex;
      justify-content: flex-end;
      margin-top: $spacing-lg;

      .modal-btn {
        min-width: 144rpx;
        height: 72rpx;
        border-radius: $radius-pill;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: $font-size-sm;
        margin-left: $spacing-sm;
      }

      .cancel-btn {
        background-color: $bg-gray;
        color: $text-secondary;
      }

      .confirm-btn {
        background-color: #211E1E;
        color: #FFFFFF;
      }
    }
  }
}

// 工具筛选下拉
.tool-filter {
  margin-top: $spacing-md;
  background-color: $bg-white;
  border-radius: $radius-lg;
  border: 1rpx solid $border-color;
  box-shadow: $shadow-card;
  padding: $spacing-md;
  display: flex;
  align-items: center;

  .tool-filter-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    margin-right: $spacing-sm;
    flex-shrink: 0;
  }

  .tool-filter-select {
    flex: 1;
    height: 64rpx;
    border-radius: $radius-pill;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 $spacing-md;

    .tool-filter-name {
      font-size: $font-size-sm;
      color: $text-primary;
      flex: 1;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .tool-filter-arrow {
      width: 32rpx;
      height: 32rpx;
      color: $text-secondary;
      margin-left: $spacing-xs;
    }
  }
}

// 列表项所属工具标签
.item-tool-tag {
  font-size: $font-size-xs;
  color: $text-secondary;
  background-color: $divider-color;
  border-radius: $radius-pill;
  padding: 2rpx 14rpx;
  margin-right: $spacing-xs;
  max-width: 200rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

// 弹窗内所属工具展示行
.modal-tool-row {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-md;

  .modal-tool-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    margin-right: $spacing-sm;
  }

  .modal-tool-name {
    font-size: $font-size-sm;
    color: $text-primary;
    font-weight: 500;
  }
}

// 工具选择弹窗
.tool-picker-content {
 max-height: 80vh;

  .tool-picker-list {
    max-height: 600rpx;
    margin-bottom: $spacing-md;

    .tool-picker-group {
      margin-bottom: $spacing-md;

      .tool-picker-group-title {
        display: block;
        font-size: $font-size-xs;
        color: $text-tertiary;
        margin-bottom: $spacing-xs;
      }

      .tool-picker-item {
        height: 72rpx;
        border-radius: $radius-md;
        background-color: $bg-gray;
        display: flex;
        align-items: center;
        padding: 0 $spacing-md;
        margin-bottom: $spacing-xs;

        .tool-picker-item-text {
          font-size: $font-size-sm;
          color: $text-primary;
        }

        &.active {
          background-color: #211E1E;

          .tool-picker-item-text {
            color: #FFFFFF;
          }
        }
      }
    }

    .prompt-empty {
      padding: $spacing-xl 0;
      display: flex;
      align-items: center;
      justify-content: center;

      text {
        font-size: $font-size-sm;
        color: $text-tertiary;
      }
    }
  }
}
</style>
