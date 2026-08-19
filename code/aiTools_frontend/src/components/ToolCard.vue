<template>
  <view class="tool-card press-scale" @click="onClick">
    <view class="card-icon">
      <tool-icon :name="icon" size="52rpx"></tool-icon>
    </view>
    <text class="card-name">{{ name }}</text>
    <text class="card-desc">{{ desc }}</text>
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
  padding: $spacing-md;
  box-shadow: $shadow-card;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  
  &:active {
    transform: scale(0.97);
  }
  
  .card-icon {
    width: 88rpx;
    height: 88rpx;
    border-radius: $radius-md;
    background-color: $bg-gray;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: $spacing-sm;
  }
  
  .card-name {
    font-size: $font-size-md;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 6rpx;
  }
  
  .card-desc {
    font-size: $font-size-xs;
    color: $text-tertiary;
    line-height: 1.4;
  }
}
</style>
