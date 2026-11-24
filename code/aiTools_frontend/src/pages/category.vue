<template>
  <view class="page" :style="themeStyle">
    <view class="head"><text class="head__title">工具</text></view>

    <view class="search" @click="goSearch">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>
      </svg>
      <text class="search__ph">搜索工具</text>
    </view>

    <scroll-view scroll-x class="chips" :show-scrollbar="false">
      <view
        v-for="t in tabs"
        :key="t.code"
        class="chip"
        :class="{ 'chip--on': activeTab === t.code }"
        @click="activeTab = t.code"
      >{{ t.name }}</view>
    </scroll-view>

    <view v-for="sec in sections" :key="sec.code" class="group">
      <view class="group__head">
        <text class="group__title">{{ sec.title }}</text>
        <text class="group__count">共 {{ sec.tools.length }} 个工具</text>
      </view>

      <!-- 图片类：缩略图卡 -->
      <view v-if="sec.code === '图片创意工具'" class="img-grid">
        <view v-for="tool in sec.tools" :key="tool.id" class="img-cell" @click="goToTool(tool.id)">
          <image class="img-cell__thumb" src="/static/ref/thumb-bg.png" mode="aspectFill" />
          <text class="img-cell__n">{{ tool.name }}</text>
        </view>
      </view>

      <!-- 其余：2 列图标卡 -->
      <view v-else class="cell-grid">
        <view v-for="tool in sec.tools" :key="tool.id" class="cell" @click="goToTool(tool.id)">
          <view class="cell__ico">
            <svg v-if="tool.iconType === 'doc'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
              <path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/>
            </svg>
            <svg v-else-if="tool.iconType === 'text'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
              <path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/><path d="M9 12h6M9 16h6"/>
            </svg>
            <svg v-else-if="tool.iconType === 'code'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
              <rect x="4" y="4" width="12" height="16" rx="2"/><path d="M16 8h2a2 2 0 012 2v8a2 2 0 01-2 2"/><path d="M8 9h4M8 13h4"/>
            </svg>
            <svg v-else-if="tool.iconType === 'video'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
              <rect x="4" y="5" width="16" height="16" rx="3"/><path d="M8 3v4M16 3v4"/><path d="M9 13l2.5 1.8L9 16.5z"/>
            </svg>
            <svg v-else-if="tool.iconType === 'ocr'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
              <path d="M4 8V6a2 2 0 012-2h2M16 4h2a2 2 0 012 2v2M20 16v2a2 2 0 01-2 2h-2M8 20H6a2 2 0 01-2-2v-2"/><path d="M8 12h8"/>
            </svg>
            <svg v-else-if="tool.iconType === 'audio'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
              <rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0014 0"/><path d="M12 18v3"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
              <rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 16l5-5 4 4 3-3 6 6"/>
            </svg>
          </view>
          <text class="cell__n">{{ tool.name }}</text>
          <text class="cell__d">{{ SHORT[tool.id] || '' }}</text>
        </view>
      </view>
    </view>

    <view v-if="!sections.length" class="empty">
      <text class="empty__t">该分类暂无工具</text>
    </view>
  </view>
</template>

<script setup>
import { themeStyle, refreshTabBarStyle } from '@/utils/theme'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { safeBack } from '@/utils/pageTransition'
import { TOOLS, REALIZED_TOOLS, toolDetailUrl, CATEGORIES, getToolGradient, getToolEmoji, getToolCategoryTag, TOOL_SHORT_DESC } from '@/config/tools'
import { favoriteListApi, favoriteAddApi, favoriteRemoveApi } from '@/api/favorite'

const tabs = [
  { code: 'all', name: '全部' },
  { code: 'office', name: '办公' },
  { code: 'image', name: '图片' },
  { code: 'audio', name: '音视频' },
  { code: 'eff', name: '效率' }
]
const activeTab = ref('all')

// 从 TOOLS 派生列表（展示全部 13 个工具，包括未实现的）
const allTools = ref(Object.keys(TOOLS).map(id => {
  const t = TOOLS[id]
  return {
    id,
    name: t.name,
    desc: t.desc,
    iconType: getToolGradient(id),
    emoji: getToolEmoji(id),
    fileType: t.fileType || '',
    inputTypes: t.inputTypes || [],
    category: t.category || '',
    tag: getToolCategoryTag(id)
  }
}))

/**
 * 「办公」Tab 覆盖的分类集合。
 *
 * <p>分类名取自 tools.js 的 CATEGORIES（分类改名 / 新增分类时只改 tools.js 一处），
 * 此前此处硬编码 'AI办公助手'，分类一改名这个 Tab 就会空掉。
 */
const OFFICE_CATEGORIES = new Set(
  CATEGORIES.filter(c => c.code.includes('办公')).map(c => c.code)
)

// Tab 过滤
const filteredTools = computed(() => {
  switch (activeTab.value) {
    case 'document':
      return allTools.value.filter(t => t.fileType === 'document' || t.inputTypes.includes('file'))
    case 'image':
      return allTools.value.filter(t => t.fileType === 'image' || t.inputTypes.includes('image'))
    case 'audio':
    case 'video':
    case 'dev':
    case 'eff':
      return []
    case 'office':
      return allTools.value.filter(t => OFFICE_CATEGORIES.has(t.category))
    case 'all':
    default:
      return allTools.value
  }
})

/** 按分类分组（参考图：分组标题 + 共 N 个工具） */
const GROUP_TITLES = {
  'AI办公助手': 'AI 办公助手',
  '图片创意工具': '图片创意工具'
}
const sections = computed(() => {
  const map = new Map()
  filteredTools.value.forEach(t => {
    const k = t.category || '其他'
    if (!map.has(k)) map.set(k, [])
    map.get(k).push(t)
  })
  return [...map.entries()].map(([code, tools]) => ({
    code,
    title: GROUP_TITLES[code] || code,
    tools
  }))
})

const SHORT = TOOL_SHORT_DESC

const goToTool = (id) => {
  uni.navigateTo({ url: toolDetailUrl(id) })
}

/** 收藏：点击同步后端（乐观更新） */
function toggleFav(id) {
  const t = allTools.value.find(x => x.id === id)
  if (!t) return
  const next = !t.favored
  t.favored = next
  const call = next ? favoriteAddApi('tool', id) : favoriteRemoveApi('tool', id)
  call.catch(() => { t.favored = !next })
}

/** 回填已收藏状态 */
async function loadFavorites() {
  try {
    const res = await favoriteListApi('tool')
    const set = new Set((res.data || []).map(f => f.targetId))
    allTools.value.forEach(t => { t.favored = set.has(t.id) })
  } catch (e) { /* 未登录 / 失败：保持未收藏 */ }
}

onShow(() => {
  // tabBar 配色与主题同步（主题是在 settings 这个非 tabBar 页切换的）
  refreshTabBarStyle()
  loadFavorites()
})
const goBack = () => safeBack('/pages/index')

const goSearch = () => {
  uni.navigateTo({ url: '/pages/search' })
}
</script>

<style lang="scss" scoped>
/* 1:1 复刻参考图 2（工具） */
$ref-orange: #F5671D;
$ref-ink: var(--text-primary);
$ref-sub: var(--text-secondary);

.page {
  min-height: 100vh;
  /* #ifdef H5 */
  min-height: calc(100vh - var(--window-bottom, 0px));
  /* #endif */
  background: var(--bg-page);
  padding-top: calc(var(--status-bar-height, 0px) + 12rpx);
  padding-bottom: 40rpx;
}

.head { padding: 20rpx 40rpx 16rpx; }
.head__title { font-size: 44rpx; font-weight: 700; letter-spacing: -1rpx; color: $ref-ink; }

.search {
  margin: 0 40rpx 22rpx;
  height: 80rpx;
  border-radius: 999rpx;
  background: var(--bg-gray);
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 0 26rpx;
  color: $ref-sub;
}
.search svg { width: 34rpx; height: 34rpx; }
.search__ph { font-size: 26rpx; }

.chips {
  white-space: nowrap;
  padding: 0 40rpx 8rpx;
}
.chip {
  display: inline-flex;
  align-items: center;
  height: 62rpx;
  padding: 0 28rpx;
  margin-right: 16rpx;
  border-radius: 999rpx;
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  font-size: 25rpx;
  color: $ref-ink;
}
.chip--on {
  background: $ref-orange;
  border-color: $ref-orange;
  color: #fff;
  font-weight: 600;
}

.group {
  margin: 18rpx 40rpx 0;
  background: var(--bg-card);
  border-radius: 32rpx;
  padding: 26rpx 20rpx 12rpx;
}
.group__head {
  display: flex;
  align-items: baseline;
  padding: 0 8rpx;
}
.group__title { font-size: 30rpx; font-weight: 700; color: $ref-ink; }
.group__count { margin-left: auto; font-size: 22rpx; color: $ref-sub; }

.cell-grid { display: flex; flex-wrap: wrap; margin-top: 12rpx; }
/* 三列紧凑宫格：两列时每行只有 2 个工具，留白过大（参考稿是 3 列） */
.cell {
  width: 33.3333%;
  box-sizing: border-box;
  padding: 14rpx 6rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.cell:active { opacity: .9; }
.cell__ico {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  color: $ref-orange;
  background: radial-gradient(circle, #FDEDE4 0%, rgba(253,237,228,0) 72%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.cell__ico svg { width: 46rpx; height: 46rpx; }
.cell__n { margin-top: 10rpx; font-size: 26rpx; font-weight: 600; color: $ref-ink; }
.cell__d { margin-top: 4rpx; font-size: 21rpx; color: $ref-sub; }

.img-grid { display: flex; flex-wrap: wrap; margin-top: 14rpx; }
.img-cell {
  width: 25%;
  box-sizing: border-box;
  padding: 10rpx 8rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.img-cell:active { opacity: .9; }
.img-cell__thumb {
  width: 100%;
  height: 118rpx;
  border-radius: 20rpx;
}
.img-cell__n {
  margin-top: 10rpx;
  font-size: 21rpx;
  font-weight: 600;
  color: $ref-ink;
  text-align: center;
  line-height: 1.35;
}

.empty { padding: 80rpx 0; text-align: center; }
.empty__t { font-size: 26rpx; color: $ref-sub; }
</style>
