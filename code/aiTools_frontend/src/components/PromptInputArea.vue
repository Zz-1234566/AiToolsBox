<template>
  <view class="input-section prompt-area">
    <view class="prompt-field">
      <text class="prompt-label">格式提示词（系统统一管理，不可修改）</text>
      <view class="readonly-prompt-box">
        <text class="readonly-prompt-text">{{ formatPromptDisplay || '暂未配置' }}</text>
      </view>
    </view>
    <view class="prompt-field">
      <text class="prompt-label">生成内容提示词（可选）</text>
      <textarea
        class="prompt-textarea"
        :value="generateText"
        @input="$emit('update:generateText', $event.detail.value)"
        :placeholder="generatePlaceholder || '例如：请总结要点并输出待办事项'"
        placeholder-class="textarea-placeholder"
        maxlength="1000"
      />
      <view class="prompt-select-link press-scale" @click="$emit('pickGenerate')">
        <svg class="prompt-select-icon" width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M4 7C4 6.44772 4.44772 6 5 6H14C14.5523 6 15 6.44772 15 7V17C15 17.5523 14.4477 18 14 18H5C4.44772 18 4 17.5523 4 17V7Z" stroke="currentColor" stroke-width="1.5"/>
          <path d="M15 9H18L20 11V17C20 17.5523 19.5523 18 19 18H15" stroke="currentColor" stroke-width="1.5"/>
        </svg>
        <text class="prompt-select-text">选择提示词</text>
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  formatPromptDisplay: { type: String, default: '' },
  generateText: { type: String, default: '' },
  generatePlaceholder: { type: String, default: '' }
})
defineEmits(['update:generateText', 'pickGenerate'])
</script>

<style lang="scss" scoped>
.input-section {
  margin-bottom: $spacing-md;
}

.prompt-field {
  margin-bottom: $spacing-sm;

  &:last-child {
    margin-bottom: 0;
  }

  .prompt-label {
    display: block;
    font-size: $font-size-xs;
    color: $text-secondary;
    margin-bottom: $spacing-xs;
  }

  .prompt-textarea {
    width: 100%;
    min-height: 120rpx;
    background-color: $bg-white;
    border-radius: $radius-md;
    padding: $spacing-sm $spacing-md;
    font-size: $font-size-sm;
    color: $text-primary;
    border: 2rpx dashed $border-color;
  }

  .readonly-prompt-box {
    width: 100%;
    min-height: 120rpx;
    max-height: 240rpx;
    background-color: $bg-gray;
    border-radius: $radius-md;
    padding: $spacing-sm $spacing-md;
    border: 2rpx solid $border-color;
    overflow-y: auto;
  }

  .readonly-prompt-text {
    font-size: $font-size-sm;
    color: $text-primary;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
  }

  .prompt-select-link {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 6rpx;
    padding: $spacing-xs $spacing-xs 0 0;

    .prompt-select-icon {
      width: 28rpx;
      height: 28rpx;
      color: $text-tertiary;
    }

    .prompt-select-text {
      color: $text-tertiary;
      font-size: $font-size-xs;
    }
  }
}

.textarea-placeholder {
  color: $text-tertiary;
  font-size: $font-size-md;
}
</style>
