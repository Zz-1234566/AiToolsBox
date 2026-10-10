<template>
  <svg
    class="wf-icon"
    :class="{ 'wf-icon--fill': isFill }"
    viewBox="0 0 24 24"
    :style="cssSize"
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
  /** 边长，单位 px。推荐档位见 ICON_SIZE_TOKENS；保持向后兼容，可继续传任意数字 */
  size: { type: [Number, String], default: 20 },
  strokeWidth: { type: [Number, String], default: 2 }
})

/**
 * 尺寸档位见 utils/workflowView.js 的 ICON_SIZE_TOKENS（与图标表同源）。
 * 仍可传任意数字，向后兼容，不做破坏性改动。
 */
const def = computed(() => STROKE_ICONS[props.name] || STROKE_ICONS.summary)

/**
 * 实心图标判定。
 * STROKE_ICONS 有两种形态：线性图标是 path d 的**数组**，实心图标是 `{ fill: true, d: [...] }` 对象。
 * 原写法 `!!(def.value && def.value.fill)` 对数组也成立——因为 Array.prototype.fill
 * 是原型上的**内置函数**（truthy），导致所有线性图标都被误判成实心，
 * 随后取 `def.value.d` 得 undefined，v-for 渲染 0 个 <path>：图标只剩一个空的 svg，视觉上完全消失。
 * 必须先判形态（是否带自有 fill 字段的对象），再取值。
 */
const isFill = computed(() => {
  const d = def.value
  return !!d && !Array.isArray(d) && d.fill === true
})

const paths = computed(() => {
  const d = def.value
  const list = isFill.value ? d.d : d
  return Array.isArray(list) ? list : []
})

/** 尺寸兜底：CSS 变量给 width/height 兜底，防止子组件根元素在 scoped 下丢失尺寸退化成 0×0 */
const cssSize = computed(() => ({ '--wf-size': `${props.size}px` }))
</script>

<style scoped>
.wf-icon {
  display: block;
  flex: none;
  /* 尺寸兜底：attribute 与 CSS 双通道。attribute 为主，CSS 兜底，
     避免 scoped/多端编译差异导致 svg 退化成 0×0 整图标消失。 */
  width: var(--wf-size, 20px);
  height: var(--wf-size, 20px);
  /* 颜色走 currentColor 继承，不硬编码 */
  stroke: currentColor;
  fill: none;
}
.wf-icon--fill {
  stroke: none;
  fill: currentColor;
}
</style>
