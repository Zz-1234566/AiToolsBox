<template>
  <view v-if="types.length > 1" class="input-switcher">
    <view
      v-for="type in types"
      :key="type"
      class="input-switch-item press-scale"
      :class="{ active: current === type }"
      @click="$emit('change', type)"
    >
      <text class="input-switch-text">{{ label(type) }}</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  types: { type: Array, default: () => [] },
  current: { type: String, default: 'text' }
})
defineEmits(['change'])

// audio 类型已从 inputTypes 中移除（2026-08 按用户要求砍掉），label 暂留作 fallback
const label = (t) => ({ text: '文字', file: '文件', image: '图片' }[t] || t)
</script>

<style lang="scss" scoped>
.input-switcher {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;

  .input-switch-item {
    flex: 1;
    padding: $spacing-sm 0;
    border-radius: $radius-md;
    background-color: $bg-white;
    text-align: center;
    border: 2rpx solid $border-color;

    &.active {
      background-color: $text-primary;
      border-color: $text-primary;

      .input-switch-text {
        color: $bg-white;
      }
    }

    .input-switch-text {
      font-size: $font-size-sm;
      color: $text-secondary;
    }
  }
}
</style>
