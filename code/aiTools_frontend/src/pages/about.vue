<template>
  <view class="page-container">
    <page-header title="关于 AI Tools Box" showBack></page-header>

    <scroll-view scroll-y class="page-content">
      <!-- 品牌区 -->
      <view class="brand">
        <view class="brand-logo">
          <image class="brand-logo__img" src="/static/logo.png" mode="aspectFit"></image>
        </view>
        <text class="brand-name">智汇工具箱</text>
        <text class="brand-sub">AI Tools Box</text>
        <text class="brand-version">v{{ appVersion }}</text>
      </view>

      <!-- 简介 -->
      <view class="card">
        <text class="card__title">项目简介</text>
        <text class="card__text">一个面向移动端 / H5 的多端 AI 工具聚合应用。内置工作总结、文档重点提取、会议纪要、图片处理、效率小工具等 AI 办公能力，支持文本类工具的流式输出与多节点工作流编排。</text>
      </view>

      <!-- 技术栈 -->
      <view class="card">
        <text class="card__title">技术栈</text>
        <view class="kv" v-for="item in techItems" :key="item.k">
          <text class="kv__k">{{ item.k }}</text>
          <text class="kv__v">{{ item.v }}</text>
        </view>
      </view>

      <!-- 声明 -->
      <view class="card">
        <text class="card__title">声明</text>
        <text class="card__text">本应用为毕业设计作品，仅供学习与交流使用。</text>
        <text class="card__copyright">© 2026 AI Tools Box</text>
      </view>

      <view class="safe-area-bottom"></view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'

const appVersion = ref('1.0.0')

const techItems = [
  { k: '前端', v: 'uni-app + Vue 3 + Vite' },
  { k: '后端', v: 'Spring Boot 3.2 + Java 17' },
  { k: '数据库', v: 'MySQL 8 + Redis' },
  { k: 'AI 能力', v: 'MiniMax（文本 / 多模态）' },
  { k: '存储', v: '腾讯云 COS' }
]
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

/* 品牌区 */
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 80rpx 0 48rpx;
}
.brand-logo {
  width: 160rpx;
  height: 160rpx;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #EFF6FF 0%, #F5F3FF 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(59, 130, 246, 0.12);
  overflow: hidden;
}
.brand-logo__img {
  width: 112rpx;
  height: 112rpx;
}
.brand-name {
  margin-top: 16rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
}
.brand-sub {
  font-size: 26rpx;
  color: $text-tertiary;
}
.brand-version {
  margin-top: 4rpx;
  font-size: 24rpx;
  color: $text-tertiary;
}

/* 卡片 */
.card {
  margin-top: $spacing-lg;
  background: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-lg;
  box-shadow: 0 1rpx 3rpx rgba(0, 0, 0, 0.06);
}
.card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}
.card__text {
  display: block;
  font-size: 28rpx;
  line-height: 1.7;
  color: $text-secondary;
}
.card__copyright {
  display: block;
  margin-top: $spacing-md;
  font-size: 24rpx;
  color: $text-tertiary;
  text-align: center;
}

/* 键值行 */
.kv {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1rpx solid $divider-color;

  &:last-child {
    border-bottom: none;
  }
}
.kv__k {
  font-size: 28rpx;
  color: $text-tertiary;
}
.kv__v {
  font-size: 28rpx;
  color: $text-primary;
}
</style>
