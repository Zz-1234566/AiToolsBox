<template>
  <view class="tool-icon" :style="{ width: size, height: size }">
    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <!-- 文档/总结 -->
      <g v-if="name === 'summary'">
        <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M14 2V8H20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M8 13H16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M8 17H13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </g>
      
      <!-- 周报 -->
      <g v-else-if="name === 'weekly'">
        <rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" stroke-width="1.5"/>
        <path d="M16 2V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M8 2V6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M3 10H21" stroke="currentColor" stroke-width="1.5"/>
        <path d="M8 14H10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M8 18H10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </g>
      
      <!-- 会议 -->
      <g v-else-if="name === 'meeting'">
        <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" stroke-width="1.5"/>
        <path d="M12 6V12L16 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </g>
      
      <!-- OCR/识别 -->
      <g v-else-if="name === 'ocr'">
        <path d="M2 7L2 5C2 3.89543 2.89543 3 4 3L6 3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M18 3L20 3C21.1046 3 22 3.89543 22 5L22 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M22 17L22 19C22 20.1046 21.1046 21 20 21L18 21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M6 21L4 21C2.89543 21 2 20.1046 2 19L2 17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M9 12H15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M12 9V15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </g>
      
      <!-- 图片/换背景 -->
      <g v-else-if="name === 'bg-color'">
        <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" stroke-width="1.5"/>
        <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor"/>
        <path d="M21 15L16 10L3 21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </g>
      
      <!-- 换背景图 -->
      <g v-else-if="name === 'bg-image'">
        <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" stroke-width="1.5"/>
        <path d="M3 16L8 11L13 16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M13 16L16 13L21 18" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </g>
      
      <!-- 图片压缩 -->
      <g v-else-if="name === 'compress'">
        <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M14 3H21V10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M21 3L14 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </g>
      
      <!-- 二维码 -->
      <g v-else-if="name === 'qr'">
        <rect x="3" y="3" width="7" height="7" rx="1" stroke="currentColor" stroke-width="1.5"/>
        <rect x="14" y="3" width="7" height="7" rx="1" stroke="currentColor" stroke-width="1.5"/>
        <rect x="3" y="14" width="7" height="7" rx="1" stroke="currentColor" stroke-width="1.5"/>
        <rect x="14" y="14" width="7" height="7" rx="1" stroke="currentColor" stroke-width="1.5"/>
      </g>
      
      <!-- 待办 -->
      <g v-else-if="name === 'todo'">
        <path d="M9 11L12 14L22 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M21 12V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </g>
      
      <!-- 番茄钟 -->
      <g v-else-if="name === 'tomato'">
        <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" stroke-width="1.5"/>
        <path d="M12 6V12L16 16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </g>
      
      <!-- 密码 -->
      <g v-else-if="name === 'password'">
        <rect x="5" y="11" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.5"/>
        <path d="M7 11V7C7 5.93913 7.42143 4.92172 8.17157 4.17157C8.92172 3.42143 9.93913 3 11 3H13C14.0609 3 15.0783 3.42143 15.8284 4.17157C16.5786 4.92172 17 5.93913 17 7V11" stroke="currentColor" stroke-width="1.5"/>
        <circle cx="12" cy="16" r="1" fill="currentColor"/>
      </g>
      
      <!-- 自定义 -->
      <g v-else-if="name === 'custom'">
        <path d="M12 20H21" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M16.5 3.5C16.8978 3.10217 17.4374 2.87868 18 2.87868C18.5626 2.87868 19.1022 3.10217 19.5 3.5C19.8978 3.89782 20.1213 4.43739 20.1213 5C20.1213 5.56261 19.8978 6.10217 19.5 6.5L7 19L3 20L4 16L16.5 3.5Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </g>
      
      <!-- 默认：方块 -->
      <g v-else>
        <rect x="4" y="4" width="16" height="16" rx="2" stroke="currentColor" stroke-width="1.5"/>
      </g>
    </svg>
  </view>
</template>

<script setup>
const props = defineProps({
  name: {
    type: String,
    default: ''
  },
  size: {
    type: String,
    default: '56rpx'
  }
})
</script>

<style lang="scss" scoped>
.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-primary;
  
  svg {
    width: 100%;
    height: 100%;
  }
}
</style>
