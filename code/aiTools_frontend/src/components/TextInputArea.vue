<template>
  <view class="input-section">
    <view class="input-card">
      <textarea
        class="text-input-area"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.detail.value)"
        :placeholder="placeholder"
        placeholder-class="textarea-placeholder"
        :maxlength="maxlength || 5000"
      />
    </view>
  </view>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  maxlength: { type: Number, default: 5000 }
})
defineEmits(['update:modelValue'])
</script>

<style lang="scss" scoped>
.input-section {
  height: 380rpx;
  margin-bottom: $spacing-md;
  display: flex;
}

.input-card {
  flex: 1;
  background-color: $bg-white;
  border-radius: $radius-lg;
  padding: $spacing-md;
  box-shadow: $shadow-card;
  display: flex;
}

.text-input-area {
  width: 100%;
  flex: 1;
  font-size: $font-size-md;
  color: $text-primary;
  line-height: 1.7;
}

.textarea-placeholder {
  color: $text-tertiary;
  font-size: $font-size-md;
}
</style>
