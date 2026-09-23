<template>
  <view class="tool-card press-scale" @click="onClick">
    <view class="card-icon">
      <tool-icon :name="icon" :gradient="gradient" size="64rpx"></tool-icon>
    </view>
    <text class="card-name">{{ name }}</text>
    <text class="card-desc" v-if="desc">{{ desc }}</text>
    <view v-if="categoryTag" class="card-meta">
      <text class="card-tag">{{ categoryTag }}</text>
    </view>
  </view>
</template>

<script setup>
import ToolIcon from './ToolIcon.vue'

const props = defineProps({
  icon: {
    type: String,
    default: ''
  },
  name: {
    type: String,
    default: ''
  },
  desc: {
    type: String,
    default: ''
  },
  toolId: {
    type: String,
    default: ''
  },
  isCustom: {
    type: Boolean,
    default: false
  },
  // 工具类型，对应 ToolIcon gradient 枚举
  // doc / image / dev / audio / video / ocr / text / code / brand
  gradient: {
    type: String,
    default: 'brand'
  },
  // 显示在卡片左下角的分类 tag 文字
  categoryTag: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['click'])

const onClick = () => {
  emit('click', {
    toolId: props.toolId,
    isCustom: props.isCustom
  })
}
</script>

<style lang="scss" scoped>
.tool-card {
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-3;
  box-shadow: $shadow-card;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  transition: opacity 0.1s ease-in-out;

  &:active {
    opacity: 0.85;
  }

  .card-icon {
    margin-bottom: $spacing-2;
  }

  .card-name {
    font-size: $font-size-md;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 4rpx;
    line-height: 1.4;
    // 单行省略
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }

  .card-desc {
    font-size: $font-size-xs;
    color: $text-tertiary;
    line-height: 1.4;
    margin-bottom: $spacing-2;
    // 2 行省略
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .card-meta {
    margin-top: auto;
    padding-top: $spacing-2;
  }

  .card-tag {
    font-size: 22rpx;
    color: $text-secondary;
    background-color: $bg-gray;
    padding: 4rpx 12rpx;
    border-radius: 8rpx;
  }
}
</style>