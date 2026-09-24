<template>
  <view class="page page--no-tabbar">
    <!-- 顶栏：标题 + 创建按钮 -->
    <view class="topbar">
      <text class="topbar__title topbar__title--left" style="margin-left: 16px;">工作流</text>
      <view class="topbar__spacer"></view>
      <view class="btn-create" @click="createWorkflow">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z" />
        </svg>
        <text class="btn-create__label">创建工作流</text>
      </view>
    </view>

    <scroll-view scroll-y class="page-content">
      <!-- 工作流卡片 -->
      <view
        v-for="wf in workflows"
        :key="wf.id"
        class="workflow-card"
      >
        <view class="workflow-card__header">
          <text class="workflow-card__title">{{ wf.title }}</text>
          <view class="icon-btn" @click="toggleFav(wf.id)">
            <svg viewBox="0 0 24 24" :fill="wf.favored ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.5">
              <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
            </svg>
          </view>
        </view>
        <view class="workflow-card__desc">{{ wf.desc }}</view>
        <view class="workflow-card__stats">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
            <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5C21.27 7.61 17 4.5 12 4.5z" />
          </svg>
          <text>{{ wf.runCount }}</text>
        </view>

        <!-- 节点缩略（竖向） -->
        <view class="workflow-nodes">
          <view
            v-for="(node, idx) in wf.nodes"
            :key="idx"
            class="workflow-node"
          >
            <view class="workflow-node__rail">
              <view class="tool-icon" :class="['tool-icon--' + node.iconType, 'tool-icon--sm']">
                <text class="tool-icon__emoji">{{ node.emoji }}</text>
              </view>
              <view v-if="idx < wf.nodes.length - 1" class="workflow-node__line"></view>
            </view>
            <view class="workflow-node__body">
              <text class="workflow-node__name">{{ node.name }}</text>
              <text class="workflow-node__sub">{{ node.sub }}</text>
            </view>
          </view>
        </view>

        <view class="btn btn--primary btn--block" @click="runWorkflow(wf.id)">
          运行工作流
        </view>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

// 占位数据：按 mockup 的"商品图生成视频工作流"
const workflows = ref([
  {
    id: 'wf-1',
    title: '商品图生成视频工作流',
    desc: '从商品图到宣传视频，一键完成',
    runCount: '12.6万次',
    favored: false,
    nodes: [
      { name: '上传商品图', sub: '支持 JPG / PNG / WEBP', iconType: 'image', emoji: '🖼️' },
      { name: '图片去背景', sub: '自动移除图片背景',     iconType: 'image', emoji: '🎨' },
      { name: '图片增强',   sub: '智能提升画质与色彩',   iconType: 'image', emoji: '✨' },
      { name: '生成商品海报', sub: '一键生成营销海报',     iconType: 'text',  emoji: '📰' },
      { name: '生成宣传视频', sub: '输出 1080P 宣传视频',   iconType: 'video', emoji: '🎬' }
    ]
  }
])

const createWorkflow = () => {
  uni.showToast({ title: '创建工作流功能开发中', icon: 'none' })
}
const runWorkflow = (id) => {
  uni.showToast({ title: `运行工作流：${id}`, icon: 'none' })
}
const toggleFav = (id) => {
  const wf = workflows.value.find(w => w.id === id)
  if (wf) wf.favored = !wf.favored
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: var(--bg-page, #F9FAFB);
}

/* ===== Topbar ===== */
.topbar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--bg-card, #FFFFFF);
  border-bottom: 1rpx solid var(--border-light, #F3F4F6);
}
.topbar__title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
.topbar__spacer {
  flex: 1;
}
.btn-create {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 20rpx;
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  color: #fff;
  border-radius: 32rpx;
  font-size: 24rpx;
  cursor: pointer;
}
.btn-create__label {
  color: #fff;
}

/* ===== Page Content ===== */
.page-content {
  padding: 24rpx;
  height: calc(100vh - 88rpx);
}

/* ===== Workflow Card ===== */
.workflow-card {
  background: var(--bg-card, #FFFFFF);
  border-radius: 24rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.workflow-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.workflow-card__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
.workflow-card__desc {
  font-size: 24rpx;
  color: var(--text-secondary, #4B5563);
  margin-bottom: 16rpx;
}
.workflow-card__stats {
  display: flex;
  align-items: center;
  gap: 6rpx;
  color: var(--text-tertiary, #9CA3AF);
  font-size: 22rpx;
  margin-bottom: 24rpx;
}

.icon-btn {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary, #9CA3AF);
  cursor: pointer;
}
.icon-btn svg {
  width: 32rpx;
  height: 32rpx;
}

/* ===== Workflow Nodes (Vertical) ===== */
.workflow-nodes {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 16rpx 0;
}
.workflow-node {
  display: flex;
  align-items: stretch;
  gap: 20rpx;
}
.workflow-node__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 64rpx;
  flex-shrink: 0;
}
.workflow-node__line {
  flex: 1;
  width: 2rpx;
  background: var(--border-light, #F3F4F6);
  margin-top: 4rpx;
  min-height: 16rpx;
}
.workflow-node__body {
  flex: 1;
  padding: 12rpx 0;
}
.workflow-node__name {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--text-primary, #111827);
  margin-bottom: 4rpx;
}
.workflow-node__sub {
  display: block;
  font-size: 22rpx;
  color: var(--text-tertiary, #9CA3AF);
}

/* ===== Tool Icon (small) ===== */
.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  color: #fff;
  flex-shrink: 0;
}
.tool-icon--sm {
  width: 64rpx;
  height: 64rpx;
}
.tool-icon--image { background: var(--gradient-image, linear-gradient(135deg, #C7D2FE 0%, #6366F1 100%)); }
.tool-icon--text { background: var(--gradient-text, linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%)); }
.tool-icon--video { background: var(--gradient-video, linear-gradient(135deg, #F9A8D4 0%, #EC4899 100%)); }
.tool-icon__emoji {
  font-size: 28rpx;
}

/* ===== Button ===== */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: 44rpx;
  font-size: 30rpx;
  font-weight: 500;
  margin-top: 32rpx;
  cursor: pointer;
}
.btn--primary {
  background: var(--gradient-brand, linear-gradient(135deg, #3B82F6 0%, #6366F1 100%));
  color: #fff;
}
.btn--block {
  width: 100%;
}

.safe-area-bottom {
  height: 60rpx;
}
</style>