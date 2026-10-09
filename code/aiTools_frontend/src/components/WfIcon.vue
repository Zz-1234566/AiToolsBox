<template>
  <svg
    class="wf-icon"
    :class="{ 'wf-icon--fill': isFill }"
    viewBox="0 0 24 24"
    :width="size"
    :height="size"
    :fill="isFill ? 'currentColor' : 'none'"
    :stroke="isFill ? 'none' : 'currentColor'"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import { STROKE_ICONS } from '@/utils/workflowView'

/**
 * 线性/实心图标统一出口（设计稿 ICON 同款 stroke 风格）。
 * 图标表集中在 utils/workflowView.js 的 STROKE_ICONS，避免每个页面各抄一份 path。
 */
const props = defineProps({
  /** STROKE_ICONS 里的图标名 */
  name: { type: String, required: true },
  /** 边长（rpx 由外部 class 控制，这里给 px 缺省值） */
  size: { type: [Number, String], default: 20 },
  strokeWidth: { type: [Number, String], default: 2 }
})

const def = computed(() => STROKE_ICONS[props.name] || STROKE_ICONS.summary)
const isFill = computed(() => !!(def.value && def.value.fill))
const paths = computed(() => (isFill.value ? def.value.d : def.value))
</script>

<style scoped>
.wf-icon {
  display: block;
  flex: none;
}
</style>
