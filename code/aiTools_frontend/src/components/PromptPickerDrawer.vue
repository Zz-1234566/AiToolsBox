<template>
  <view v-if="show" class="prompt-drawer-mask" @click="close">
    <view class="prompt-drawer" @click.stop>
      <!-- 顶部 header -->
      <view class="prompt-drawer__header">
        <view class="prompt-drawer__title-row">
          <view class="prompt-drawer__icon-bg">
            <svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>
          </view>
          <view class="prompt-drawer__title-text">
            <text class="prompt-drawer__name">{{ toolName || '提示词选择' }}</text>
            <text class="prompt-drawer__desc">{{ toolDesc || '这里是工具简介内容' }}</text>
          </view>
        </view>
        <view class="prompt-drawer__close" @click="close">
          <svg viewBox="0 0 24 24"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
        </view>
      </view>

      <!-- 左栏 + 右栏 -->
      <view class="prompt-drawer__body">
        <!-- 左栏 -->
        <view class="prompt-drawer__left">
          <view class="prompt-drawer__title-bar">
            <text class="prompt-drawer__modal-title">选择提示词</text>
          </view>
          <view class="prompt-drawer__tab-bar">
            <view
              v-for="t in tabsList"
              :key="t.key"
              class="prompt-drawer__tab-item"
              :class="{ 'is-active': currentTab === t.key }"
              @click="currentTab = t.key"
            >
              <text>{{ t.label }}</text>
            </view>
          </view>
          <view class="prompt-drawer__search">
            <svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 001.48-5.34c-.47-2.78-2.79-5-5.59-5.34a6.505 6.505 0 00-7.27 7.27c.34 2.8 2.56 5.12 5.34 5.59a6.5 6.5 0 005.34-1.48l.27.28v.79l4.25 4.25c.41.41 1.08.41 1.49 0 .41-.41.41-1.08 0-1.49L15.5 14zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
            <input class="prompt-drawer__search-input" placeholder="搜索提示词名称…" v-model="searchKeyword" />
          </view>
          <scroll-view scroll-y class="prompt-drawer__list">
            <view
              v-for="item in filteredPrompts"
              :key="item.id"
              class="prompt-list-item"
              :class="{ 'is-active': selectedId === item.id }"
              @click="onPreview(item)"
            >
              <view class="prompt-list-item__icon">
                <svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm-2 7l-4 4 4 4V9z"/></svg>
              </view>
              <view class="prompt-list-item__body">
                <text class="prompt-list-item__name">{{ item.promptName || '未命名' }}</text>
                <text class="prompt-list-item__meta">{{ item.promptUse === 'format' ? '格式' : '生成内容' }} · {{ item.promptType === 'system' ? '系统' : '我的' }}</text>
              </view>
            </view>
            <view v-if="filteredPrompts.length === 0" class="prompt-list-empty">
              <text>{{ searchKeyword ? '未找到匹配的提示词' : '暂无提示词' }}</text>
            </view>
          </scroll-view>
        </view>

        <!-- 右栏：当前选中预览 -->
        <view class="prompt-drawer__right">
          <view v-if="selectedItem" class="prompt-preview">
            <view class="prompt-preview__header">
              <view class="prompt-preview__icon">
                <svg viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm-2 7l-4 4 4 4V9z"/></svg>
              </view>
              <view class="prompt-preview__title-block">
                <text class="prompt-preview__name">{{ selectedItem.promptName || '未命名' }}</text>
                <view class="prompt-preview__tag-row">
                  <text class="prompt-preview__tag">{{ selectedItem.promptUse === 'format' ? '格式' : '生成内容' }}</text>
                </view>
              </view>
            </view>
            <view class="prompt-preview__desc">
              <text class="prompt-preview__desc-title">说明</text>
              <scroll-view scroll-y class="prompt-preview__desc-scroll">
                <text class="prompt-preview__desc-text">{{ selectedItem.promptText }}</text>
              </scroll-view>
            </view>
          </view>
          <view v-else class="prompt-preview prompt-preview--empty">
            <text>从左侧选择一条提示词查看详情</text>
          </view>
        </view>
      </view>

      <!-- 底部 fixed 主按钮 -->
      <view class="prompt-drawer__bottom">
        <button class="prompt-drawer__confirm-btn" :disabled="!selectedItem" @click="confirm">
          <text>确定{{ selectedItem ? '（已选择 1 个）' : '' }}</text>
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  toolName: { type: String, default: '' },
  toolDesc: { type: String, default: '' },
  systemPrompts: { type: Array, default: () => [] },
  userPrompts: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:show', 'confirm'])

const currentTab = ref('system')
const searchKeyword = ref('')
const selectedId = ref(null)

// tab 列表（只显示 system / user，AI 是创建入口不进选择弹窗）
const tabsList = computed(() => ([
  { key: 'system', label: '系统预设' },
  { key: 'user', label: '我的' }
]))

// 根据当前 tab + 搜索关键词过滤列表
const filteredPrompts = computed(() => {
  const list = currentTab.value === 'system' ? props.systemPrompts : props.userPrompts
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return list
  return list.filter(item =>
    (item.promptName || '').toLowerCase().includes(keyword) ||
    (item.promptText || '').toLowerCase().includes(keyword)
  )
})

// 当前选中预览项
const selectedItem = computed(() => {
  if (!selectedId.value) return null
  return filteredPrompts.value.find(item => item.id === selectedId.value)
    || [...props.systemPrompts, ...props.userPrompts].find(item => item.id === selectedId.value)
})

// 点列表项 → 设置预览
const onPreview = (item) => {
  selectedId.value = item.id
}

// 关闭弹窗
const close = () => {
  emit('update:show', false)
  emit('confirm', null)
}

// 确定选择 → emit confirm + 关闭
const confirm = () => {
  if (!selectedItem.value) return
  emit('confirm', selectedItem.value)
  emit('update:show', false)
}

// 弹窗打开时重置状态
watch(() => props.show, (val) => {
  if (val) {
    currentTab.value = 'system'
    searchKeyword.value = ''
    selectedId.value = null
  }
})
</script>

<style lang="scss" scoped>
/* ============ 抽屉式 prompt-picker 弹窗（独立组件） ============ */
.prompt-drawer-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.prompt-drawer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  max-height: 85vh;
  background: var(--bg-page, #F9FAFB);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: drawerSlideUp 0.3s ease-out;
  border-top-left-radius: 32rpx;
  border-top-right-radius: 32rpx;
  box-shadow: 0 -8rpx 32rpx rgba(0, 0, 0, 0.12);
}
@keyframes drawerSlideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

/* 顶部 header */
.prompt-drawer__header {
  padding: 32rpx;
  background: linear-gradient(135deg, #EEF2FF 0%, #F5F3FF 100%);
  border-bottom: 1rpx solid var(--border-color, #E5E7EB);
  position: relative;
}
.prompt-drawer__title-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.prompt-drawer__icon-bg {
  width: 80rpx;
  height: 80rpx;
  border-radius: 24rpx;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.prompt-drawer__icon-bg svg {
  width: 40rpx;
  height: 40rpx;
  fill: #FFFFFF;
}
.prompt-drawer__title-text {
  flex: 1;
  min-width: 0;
}
.prompt-drawer__name {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
}
.prompt-drawer__desc {
  display: block;
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  margin-top: 4rpx;
}
.prompt-drawer__close {
  position: absolute;
  top: 32rpx;
  right: 32rpx;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary, #4B5563);
  cursor: pointer;
}
.prompt-drawer__close svg {
  width: 36rpx;
  height: 36rpx;
  fill: currentColor;
}

/* 主体 */
.prompt-drawer__body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左栏 */
.prompt-drawer__left {
  width: 50%;
  display: flex;
  flex-direction: column;
  border-right: 1rpx solid var(--divider-color, #F0F0F0);
  background: var(--bg-card, #FFFFFF);
}
.prompt-drawer__title-bar {
  padding: 24rpx 32rpx 16rpx;
}
.prompt-drawer__modal-title {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--text-primary, #111827);
}
.prompt-drawer__tab-bar {
  display: flex;
  gap: 16rpx;
  padding: 0 32rpx 16rpx;
}
.prompt-drawer__tab-item {
  padding: 12rpx 28rpx;
  font-size: 26rpx;
  color: var(--text-secondary, #4B5563);
  font-weight: 500;
  border-radius: 999rpx;
  background: var(--bg-card, #FFFFFF);
  border: 1rpx solid var(--border-color, #E5E7EB);
  cursor: pointer;
}
.prompt-drawer__tab-item.is-active {
  color: #FFFFFF;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  border-color: transparent;
}
.prompt-drawer__search {
  margin: 0 32rpx 16rpx;
  padding: 16rpx 20rpx;
  background: var(--bg-page, #F3F4F6);
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.prompt-drawer__search svg {
  width: 28rpx;
  height: 28rpx;
  fill: var(--text-tertiary, #9CA3AF);
  flex-shrink: 0;
}
.prompt-drawer__search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 26rpx;
  color: var(--text-primary, #111827);
  font-family: inherit;
}
.prompt-drawer__list {
  flex: 1;
  padding: 0 32rpx 24rpx;
}
.prompt-list-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  margin-bottom: 12rpx;
  border-radius: 16rpx;
  cursor: pointer;
  transition: background 0.2s;
}
.prompt-list-item.is-active {
  background: var(--brand-primary-light, #EFF6FF);
}
.prompt-list-item__icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.prompt-list-item__icon svg {
  width: 28rpx;
  height: 28rpx;
  fill: currentColor;
}
.prompt-list-item__body {
  flex: 1;
  min-width: 0;
}
.prompt-list-item__name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 4rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.prompt-list-item__meta {
  display: block;
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
}
.prompt-list-empty {
  text-align: center;
  padding: 80rpx 0;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 26rpx;
}

/* 右栏：当前选中预览 */
.prompt-drawer__right {
  width: 50%;
  background: var(--bg-page, #F9FAFB);
  overflow-y: auto;
}
.prompt-preview {
  padding: 32rpx;
}
.prompt-preview__header {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid var(--divider-color, #F0F0F0);
  margin-bottom: 24rpx;
}
.prompt-preview__icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.prompt-preview__icon svg {
  width: 32rpx;
  height: 32rpx;
  fill: currentColor;
}
.prompt-preview__title-block {
  flex: 1;
  min-width: 0;
}
.prompt-preview__name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
  margin-bottom: 12rpx;
  word-break: break-all;
}
.prompt-preview__tag-row {
  display: flex;
  gap: 8rpx;
}
.prompt-preview__tag {
  display: inline-block;
  padding: 4rpx 12rpx;
  background: var(--brand-primary-light, #EFF6FF);
  color: var(--brand-primary, #3B82F6);
  font-size: 22rpx;
  border-radius: 8rpx;
}
.prompt-preview__desc-title {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 16rpx;
}
.prompt-preview__desc-scroll {
  max-height: 40vh;
}
.prompt-preview__desc-text {
  display: block;
  font-size: 26rpx;
  color: var(--text-primary, #111827);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
.prompt-preview--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 28rpx;
}

/* 底部按钮 */
.prompt-drawer__bottom {
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom, 0rpx));
  background: var(--bg-card, #FFFFFF);
  border-top: 1rpx solid var(--divider-color, #F0F0F0);
}
.prompt-drawer__confirm-btn {
  width: 100%;
  height: 88rpx;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  color: #FFFFFF;
  border-radius: 999rpx;
  font-size: 30rpx;
  font-weight: 500;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.prompt-drawer__confirm-btn[disabled] {
  opacity: 0.4;
}
</style>
