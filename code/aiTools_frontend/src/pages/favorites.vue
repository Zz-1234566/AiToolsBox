<template>
  <view class="fav-page">
    <!-- 头部 -->
    <view class="fav-head">
      <text class="fav-head__title">我的收藏</text>
    </view>

    <!-- 工具 / 提示词 -->
    <view class="fav-tabs">
      <text class="fav-tab" :class="{ 'fav-tab--active': activeTab === 'tool' }" @click="switchTab('tool')">工具</text>
      <text class="fav-tab" :class="{ 'fav-tab--active': activeTab === 'prompt' }" @click="switchTab('prompt')">提示词</text>
    </view>

    <!-- 搜索 -->
    <view class="fav-search">
      <text class="fav-search__icon">🔍</text>
      <input class="fav-search__input" v-model="keyword" placeholder="搜索收藏的工具或提示词..." placeholder-class="fav-ph" />
    </view>

    <!-- 空态 -->
    <view v-if="loading" class="redesign-empty"><text class="redesign-empty__text">加载中…</text></view>
    <view v-else-if="filtered.length === 0" class="redesign-empty">
      <view class="redesign-empty__icon"><text class="redesign-empty__emoji">⭐</text></view>
      <text class="redesign-empty__text">还没有收藏{{ activeTab === 'tool' ? '工具' : '提示词' }}</text>
    </view>

    <!-- 工具：2 列网格 -->
    <block v-else-if="activeTab === 'tool'">
      <view class="fav-section">收藏的工具 ({{ filtered.length }})</view>
      <view class="fav-grid">
        <view v-for="item in filtered" :key="item.id" class="fav-tool">
          <view class="fav-tool__top">
            <view class="tool-avatar" :class="'tool-avatar--' + iconType(item.toolCode)">
              <text class="ta-emoji">{{ emoji(item.toolCode) }}</text>
            </view>
            <text class="fav-tool__name">{{ item.toolName }}</text>
          </view>
          <text class="fav-tool__desc">{{ item.toolDesc || '暂无描述' }}</text>
          <view class="fav-tool__btn" @click="openTool(item)">
            <text>去使用</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 提示词：横向卡片 -->
    <block v-else>
      <view class="fav-section">收藏的提示词 ({{ filtered.length }})</view>
      <view v-for="item in filtered" :key="item.id" class="fav-prompt">
        <view class="tool-avatar" :class="'tool-avatar--' + iconType(item.belongToolCode)">
          <text class="ta-emoji">{{ emoji(item.belongToolCode) }}</text>
        </view>
        <view class="fav-prompt__body">
          <text class="fav-prompt__name">{{ item.promptName || '未命名' }}</text>
          <view class="fav-prompt__tags">
            <text class="redesign-tag">{{ toolNameOf(item.belongToolCode) }}</text>
            <text class="redesign-tag">{{ item.promptUse === 'format' ? '格式' : '生成内容' }}</text>
          </view>
        </view>
        <view class="fav-prompt__cancel" @click="onCancel(item)">取消收藏</view>
      </view>
    </block>

    <view class="safe-bottom"></view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { favoriteListApi, favoriteRemoveApi } from '@/api/favorite'
import { requireLogin } from '@/utils/auth'
import { TOOLS } from '@/config/tools'

const loading = ref(false)
const list = ref([])
const activeTab = ref('tool')
const keyword = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter(i =>
    (i.toolName || '').toLowerCase().includes(kw) ||
    (i.promptName || '').toLowerCase().includes(kw) ||
    (i.promptText || '').toLowerCase().includes(kw)
  )
})

const toolNameOf = (code) => {
  const t = TOOLS[code]
  return t ? t.name : (code || '')
}

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

const fetchList = async () => {
  loading.value = true
  try {
    const res = await favoriteListApi(activeTab.value)
    list.value = res.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

const switchTab = (t) => {
  activeTab.value = t
  fetchList()
}

onShow(() => {
  if (!requireLogin()) return
  fetchList()
})

/** 去使用：跳转到该工具详情页 */
const openTool = (item) => {
  uni.navigateTo({ url: `/pages/tool-common?id=${item.toolCode}` })
}

/** 取消收藏提示词 */
const onCancel = (item) => {
  uni.showModal({
    title: '取消收藏',
    content: `确定取消收藏「${item.promptName || '该提示词'}」吗？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await favoriteRemoveApi('prompt', item.targetId)
        list.value = list.value.filter(i => i.id !== item.id)
        uni.showToast({ title: '已取消收藏', icon: 'none' })
      } catch (e) {
        // request.js 已统一提示
      }
    }
  })
}
</script>

<style lang="scss" scoped>
@import '@/styles/redesign.scss';

.fav-page {
  min-height: 100vh;
  background: #F9FAFB;
  padding-bottom: 40rpx;
}

.fav-head { padding: 32rpx 32rpx 16rpx; }
.fav-head__title { font-size: 48rpx; font-weight: 700; color: #111827; }

.fav-tabs {
  display: flex;
  gap: 40rpx;
  padding: 0 32rpx;
  border-bottom: 2rpx solid #F3F4F6;
}
.fav-tab { padding: 24rpx 0; font-size: 28rpx; color: #9CA3AF; position: relative; }
.fav-tab--active { color: #111827; font-weight: 600; }
.fav-tab--active::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: -2rpx;
  height: 4rpx;
  background: #3B82F6;
}

.fav-search {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 24rpx 32rpx;
  height: 80rpx;
  padding: 0 24rpx;
  background: #F3F4F6;
  border-radius: 9999rpx;
}
.fav-search__icon { font-size: 30rpx; }
.fav-search__input { flex: 1; font-size: 26rpx; background: transparent; }
.fav-ph { color: #9CA3AF; }

.fav-section {
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
  padding: 8rpx 32rpx 16rpx;
}

.fav-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  padding: 0 32rpx;
}
.fav-tool {
  width: calc(50% - 12rpx);
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
  border: 2rpx solid #F3F4F6;
  box-sizing: border-box;
}
.fav-tool__top { display: flex; gap: 16rpx; align-items: center; }
.fav-tool__name {
  flex: 1;
  font-size: 26rpx;
  font-weight: 600;
  color: #111827;
}
.fav-tool__desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 22rpx;
  color: #9CA3AF;
  margin-top: 12rpx;
  line-height: 1.5;
  min-height: 66rpx;
}
.fav-tool__btn {
  display: inline-flex;
  align-items: center;
  height: 52rpx;
  padding: 0 24rpx;
  border-radius: 9999rpx;
  background: #EFF6FF;
  color: #3B82F6;
  font-size: 22rpx;
  margin-top: 16rpx;
  align-self: flex-start;
}

.fav-prompt {
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  margin: 0 32rpx 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.04);
}
.fav-prompt__body { flex: 1; min-width: 0; }
.fav-prompt__name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #111827;
}
.fav-prompt__tags { display: flex; gap: 12rpx; margin-top: 12rpx; }
.fav-prompt__cancel {
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 9999rpx;
  background: #F3F4F6;
  color: #4B5563;
  font-size: 22rpx;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.safe-bottom { height: 40rpx; }
</style>
