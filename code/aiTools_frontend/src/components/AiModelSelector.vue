<template>
  <view v-if="requiredCapability" class="model-picker">
    <text class="model-picker__label">AI 模型</text>
    <picker
      v-if="filtered.length > 0"
      mode="selector"
      :range="pickerLabels"
      :value="pickerIndex"
      @change="onPick"
    >
      <view class="model-picker__btn">
        <text v-if="selected" class="model-picker__name">
          {{ selected.nickname || selected.modelCode }}
        </text>
        <text v-else class="model-picker__placeholder">请选择</text>
        <text v-if="selected && selected.isDefault === 1" class="model-picker__default">默认</text>
        <WfIcon name="chevron-down" :size="28" />
      </view>
    </picker>
    <view v-else-if="loading" class="model-picker__btn model-picker__btn--readonly">
      <text class="model-picker__placeholder">加载中…</text>
    </view>
    <view v-else class="model-picker__btn model-picker__btn--readonly">
      <text class="model-picker__placeholder model-picker__placeholder--warn">
        暂无可用「{{ capabilityLabel }}」模型
      </text>
    </view>
    <text v-if="selected && selected.apiKeyPrefix" class="model-picker__prefix">
      {{ selected.apiKeyPrefix }}…
    </text>
  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { listUserAiModels, filterModelsByTool, TOOL_CAPABILITY_MAP } from '../api/aiModel.js'
import WfIcon from './WfIcon.vue'

const props = defineProps({
  toolId: { type: String, required: true },
  modelValue: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'change'])

const all = ref([])
const loading = ref(false)

const requiredCapability = computed(() => TOOL_CAPABILITY_MAP[props.toolId] || null)
const capabilityLabel = computed(() => {
  const m = { text: '文本', vision: '视觉', asr: '语音', ocr: 'OCR' }
  return m[requiredCapability.value] || requiredCapability.value
})
const filtered = computed(() => filterModelsByTool(all.value, props.toolId))

// picker 用：[{ label, value }]
const pickerLabels = computed(() => filtered.value.map((m) => m.nickname || m.modelCode))
const pickerIndex = computed(() => {
  if (!props.modelValue) return 0
  const idx = filtered.value.findIndex((m) => m.id === props.modelValue.id || m.modelCode === props.modelValue.modelCode)
  return idx >= 0 ? idx : 0
})
const selected = computed(() => props.modelValue)

const onPick = (e) => {
  const m = filtered.value[Number(e.detail.value)]
  if (m) {
    emit('update:modelValue', m)
    emit('change', m)
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await listUserAiModels()
    const list = (res && res.data) || res || []
    all.value = Array.isArray(list) ? list : []
  } catch (e) {
    all.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.toolId, load)
</script>

<style lang="scss" scoped>
.model-picker {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  margin: 16rpx 32rpx 0;
  border-radius: 24rpx;
  background: var(--bg-card, #FFFFFF);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);

  &__label {
    font-size: 24rpx;
    color: var(--text-tertiary, #9CA3AF);
    flex-shrink: 0;
  }
  &__btn {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8rpx;
    padding: 8rpx 16rpx;
    border-radius: 12rpx;
    background: var(--bg-hover, #F3F4F6);
    min-height: 52rpx;
    &--readonly { background: transparent; }
  }
  &__name {
    font-size: 28rpx;
    color: var(--text-primary, #1F2937);
    font-weight: 600;
  }
  &__placeholder {
    font-size: 26rpx;
    color: var(--text-tertiary, #9CA3AF);
    &--warn { color: #F59E0B; }
  }
  &__default {
    font-size: 20rpx;
    color: #fff;
    background: var(--accent, #2563EB);
    padding: 2rpx 8rpx;
    border-radius: 8rpx;
    line-height: 1.4;
  }
  &__prefix {
    font-family: monospace;
    font-size: 22rpx;
    color: var(--text-tertiary, #9CA3AF);
    flex-shrink: 0;
  }
}
</style>
