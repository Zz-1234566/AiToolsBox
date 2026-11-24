<template>
  <view class="page" :style="themeStyle">
    <!-- 顶部品牌栏 -->
    <view class="brandbar">
      <view class="brandbar__mark">
        <image class="logo-img" :class="{ 'logo-img--dark': isDarkMode }" src="/static/ref/logo.png" mode="heightFix" />
      </view>
      <text class="brandbar__name">AI Tools Box</text>
      <view class="brandbar__acts">
        <view class="ibtn ibtn--avatar" @click="switchTab('/pages/my')">
          <image v-if="userInfo && userInfo.avatar" class="avatar-img" :src="userInfo.avatar" mode="aspectFill" />
          <text v-else class="avatar-letter">{{ initials }}</text>
        </view>
      </view>
    </view>

    <!-- Hero -->
    <view class="hero">
      <view class="hero__text">
        <text class="hero__t1">一个入口</text>
        <text class="hero__t2">使用各种 AI 工具</text>
        <text class="hero__sub">高效 · 便捷 · 专业</text>
        <view class="hero__cta" @click="goToSearch">
          <text class="hero__cta-t">开始使用</text>
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 12h13M13 6l6 6-6 6"/>
          </svg>
        </view>
      </view>
      <image class="hero__cube" src="/static/ref/hero-cube.png" mode="aspectFit" />
    </view>

    <!-- 热门工具 -->
    <view class="sec">
      <text class="sec__t">热门工具</text>
      <text class="sec__more" @click="goToCategory">全部 ›</text>
    </view>
    <view class="marquee">
      <view class="marquee__track">
      <view v-for="(tool, i) in hotTools" :key="tool.id" class="tcell" @click="goToTool(tool.id)">
        <view class="tcell__ico" :class="{ 'tcell__ico--dark': i === 1 }">
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
            <path d="M4 8V6a2 2 0 012-2h2M16 4h2a2 2 0 012 2v2M20 16v2a2 2 0 01-2 2h-2M8 20H6a2 2 0 01-2-2v-2"/>
            <path d="M8 12h8"/>
          </svg>
          <svg v-else-if="tool.iconType === 'audio'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
            <rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0014 0"/><path d="M12 18v3"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
            <rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 16l5-5 4 4 3-3 6 6"/>
          </svg>
        </view>
        <text class="tcell__n">{{ tool.name }}</text>
        <text class="tcell__d">{{ tool.desc }}</text>
      </view>
    
      <view v-for="(tool, i) in hotTools" :key="'dup' + tool.id" class="tcell" @click="goToTool(tool.id)">
        <view class="tcell__ico" :class="{ 'tcell__ico--dark': i === 1 }">
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
            <path d="M4 8V6a2 2 0 012-2h2M16 4h2a2 2 0 012 2v2M20 16v2a2 2 0 01-2 2h-2M8 20H6a2 2 0 01-2-2v-2"/>
            <path d="M8 12h8"/>
          </svg>
          <svg v-else-if="tool.iconType === 'audio'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
            <rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0014 0"/><path d="M12 18v3"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
            <rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 16l5-5 4 4 3-3 6 6"/>
          </svg>
        </view>
        <text class="tcell__n">{{ tool.name }}</text>
        <text class="tcell__d">{{ tool.desc }}</text>
      </view>
      </view>
    </view>

    <!-- 轮播推荐：与热门工具同一批数据，定时轮换 -->
    <view class="wide" :key="bannerTool.id" @click="goToTool(bannerTool.id)">
      <view class="wide__thumb" :class="'wide__thumb--' + (bannerTool.iconType || 'image')">
        <text class="wide__emoji">{{ bannerTool.emoji || '✨' }}</text>
      </view>
      <view class="wide__body">
        <text class="wide__t">{{ bannerTool.name }}</text>
        <text class="wide__d">{{ bannerTool.desc }}</text>
      </view>
      <view class="wide__go">
        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 12h13M13 6l6 6-6 6"/>
        </svg>
      </view>
      <!-- 轮播指示点 -->
      <view class="wide__dots">
        <view v-for="(b, bi) in bannerTools" :key="b.id" class="wide__dot"
              :class="{ 'wide__dot--on': bi === bannerIndex }" />
      </view>
    </view>

    <!-- 最近使用 -->
    <view class="sec">
      <text class="sec__t">最近使用</text>
      <text class="sec__more" @click="goToHistory">全部 ›</text>
    </view>
    <view class="recent">
      <view v-for="tool in recentTools" :key="tool.id" class="recent__item" @click="goToTool(tool.id)">
        <view class="recent__ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round">
            <path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/>
          </svg>
        </view>
        <view class="recent__body">
          <text class="recent__n">{{ tool.name }}</text>
          <text class="recent__t">{{ tool.time }}</text>
        </view>
        <text class="recent__chev">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { themeStyle, refreshTabBarStyle, isDarkMode } from '@/utils/theme'
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { TOOLS, REALIZED_TOOLS, toolDetailUrl, getToolGradient, getToolCategoryTag, TOOL_SHORT_DESC } from '@/config/tools'
import { favoriteListApi, favoriteAddApi, favoriteRemoveApi } from '@/api/favorite'
import { switchTab } from '@/utils/pageTransition'
import { STORAGE_KEYS } from '@/utils/auth'
import { onMounted, onUnmounted } from 'vue'

// 每个工具的 emoji（轮播卡缩略图用）
const TOOL_EMOJI = { 'work-summary': '📝', 'doc-keypoint-extract': '📄', 'weekly-report': '📋', 'meeting-minutes': '🎯', 'doc-to-text': '📑', 'image-bg-remover': '🖼️', 'id-photo-bg-change': '🪪', 'audio-transcribe': '🎙️' }

// 渐变类型 / 分类 tag 统一取自 config/tools.js（唯一定义处），
// 此前本页自维护 ICON_TYPE_MAP / TAG_MAP，与分类页各一份，新增工具容易漏改。
const hotTools = ref(Object.keys(TOOLS).slice(0, 6).map(id => {
  const t = TOOLS[id] || {}
  return {
    id,
    name: t.name || id,
    desc: TOOL_SHORT_DESC[id] || t.desc || '',
    tag: getToolCategoryTag(id) || t.category || '工具',
    iconType: getToolGradient(id),
    emoji: TOOL_EMOJI[id] || '✨'
  }
}))

// 轮播推荐：取热门工具里的前 4 个，每 4s 切换
const bannerTools = computed(() => hotTools.value.slice(0, 4))
const bannerIndex = ref(0)
const bannerTool = computed(() => bannerTools.value[bannerIndex.value] || hotTools.value[0] || {})
let bannerTimer = null
onMounted(() => {
  bannerTimer = setInterval(() => {
    if (!bannerTools.value.length) return
    bannerIndex.value = (bannerIndex.value + 1) % bannerTools.value.length
  }, 4000)
})
onUnmounted(() => { if (bannerTimer) clearInterval(bannerTimer) })

const userInfo = ref(null)
const initials = computed(() => {
  const u = userInfo.value
  if (!u) return 'U'
  const n = u.nickname || u.username || ''
  return n ? n.charAt(0).toUpperCase() : 'U'
})

const recentTools = ref(Object.keys(TOOLS).slice(0, 1).map(id => {
  const t = TOOLS[id] || {}
  return {
    id,
    name: t.name || id,
    time: '刚刚',
    iconType: getToolGradient(id)
  }
}))

function goToSearch() {
  uni.navigateTo({ url: '/pages/search' })
}
/**
 * 进入智能体（**tabBar 页面**）。
 * <p>⚠️ tabBar 页只能用 switchTab：用 navigateTo 在 App / 小程序端会直接失败，
 * H5 端侥幸能跳但会多压一层页面栈（返回时多退一次才回首页）。
 */
function goAgent() {
  switchTab('/pages/agent')
}
function goToCategory() {
  // ⚠️ category 在 pages.json 的 tabBar.list 里，navigateTo 指向 tabBar 页
  // 在 App / 小程序端会直接失败（tabBar 页只能 switchTab）。H5 端侥幸能跳，
  // 但会多压一层页面栈，导致返回时多退一次才回到首页。
  switchTab('/pages/category')
}
function goToHistory() {
  uni.navigateTo({ url: '/pages/history' })
}
function goToTool(id) {
  uni.navigateTo({ url: toolDetailUrl(id) })
}
function toggleFav(id) {
  const t = hotTools.value.find(x => x.id === id)
  if (!t) return
  const next = !t.favored
  // 乐观更新
  t.favored = next
  const call = next ? favoriteAddApi('tool', id) : favoriteRemoveApi('tool', id)
  call.catch(() => {
    t.favored = !next // 失败回滚
  })
}

/** 拉取当前用户已收藏的工具编码集合，回填 favored */
async function loadFavorites() {
  try {
    const res = await favoriteListApi('tool')
    const set = new Set((res.data || []).map(f => f.targetId))
    hotTools.value.forEach(t => { t.favored = set.has(t.id) })
  } catch (e) { /* 未登录 / 请求失败：保持默认未收藏 */ }
}

onShow(() => {
  userInfo.value = uni.getStorageSync(STORAGE_KEYS.USER_INFO) || null
  // tabBar 配色与主题同步（主题是在 settings 这个非 tabBar 页切换的）
  refreshTabBarStyle()
  loadFavorites()
})
</script>

<style lang="scss" scoped>
/* 1:1 复刻参考图 1（首页） */
$ref-orange: #F5671D;
$ref-ink: var(--text-primary);
$ref-sub: var(--text-secondary);
$ref-bg: var(--bg-page);

.page {
  /*
   * 高度按平台区分（App 优先）：
   *  - App：原生 tabBar 在 webview 之外，100vh 就是内容区高度，用 100vh
   *  - H5：tabBar 覆盖在页面上，100vh 会多算 50px → 底栏被挤出可视区、标签被裁
   */
  min-height: 100vh;
  /* #ifdef H5 */
  min-height: calc(100vh - var(--window-bottom, 0px));
  /* #endif */
  background: $ref-bg;
  padding: calc(var(--status-bar-height, 0px) + 12rpx) 0 40rpx;
}

/* 顶部品牌栏 */
.brandbar {
  display: flex;
  align-items: center;
  padding: 44rpx 40rpx 40rpx;
  gap: 14rpx;
}
.brandbar__mark {
  display: flex;
  align-items: center;
}
.logo-img {
  height: 54rpx;
  width: 134rpx;
}
/* 深色底上深色 logo 看不清 → 反白（橙色点同变白，换取可读性） */
.logo-img--dark {
  filter: brightness(0) invert(1);
}
.brandbar__name {
  font-size: 30rpx;
  font-weight: 500;
  color: $ref-ink;
}
.brandbar__acts {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.ibtn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $ref-ink;
  svg { width: 40rpx; height: 40rpx; }
}
.ibtn--avatar {
  width: 92rpx;
  height: 92rpx;
  border-radius: 50%;
  background: #FDEDE4;
  color: #F5671D;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 600;
}
.avatar-img { width: 100%; height: 100%; }
.avatar-letter { line-height: 1; }

/* Hero */
.hero {
  position: relative;
  margin: 0 40rpx;
  border-radius: 40rpx;
  background: #E9E9F2;
  padding: 36rpx 32rpx 36rpx 36rpx;
  overflow: hidden;
  display: flex;
  align-items: center;
}
.hero__text { flex: 1; min-width: 0; }
.hero__t1, .hero__t2 {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  /* hero 是插画卡，底色恒为浅色 → 文字固定深色，避免深色模式下白字压白底 */
  color: #17171A;
  line-height: 1.36;
  letter-spacing: -1rpx;
}
.hero__sub {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #6E6E78;
}
.hero__cta {
  margin-top: 24rpx;
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  height: 66rpx;
  padding: 0 26rpx;
  border-radius: 999rpx;
  background: #1B1B1F;
}
.hero__cta:active { opacity: .88; }
.hero__cta-t { font-size: 24rpx; font-weight: 600; color: #fff; }
.hero__cta svg { width: 26rpx; height: 26rpx; }
.hero__cube {
  width: 220rpx;
  height: 250rpx;
  margin-right: -20rpx;
  flex-shrink: 0;
}

/* 分节标题 */
.sec {
  display: flex;
  align-items: baseline;
  padding: 0 40rpx;
  margin: 34rpx 0 18rpx;
}
.sec__t { font-size: 34rpx; font-weight: 700; color: $ref-ink; letter-spacing: -0.5rpx; }
.sec__more { margin-left: auto; font-size: 24rpx; color: $ref-sub; }

/* 三列宫格 */
.grid3 {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 0 32rpx;
}
.tcell {
  /* 不用负外边距：rpx 随视口缩放，负 margin 会让总宽超过 100% 产生横向滚动条 */
  width: calc((100% - 32rpx) / 3);
  margin: 0 0 0;
  box-sizing: border-box;
  background: var(--bg-card);
  border-radius: 28rpx;
  padding: 22rpx 12rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.tcell:active { opacity: .92; }
.tcell__ico {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $ref-orange;
  background: radial-gradient(circle, #FDEDE4 0%, rgba(253,237,228,0) 72%);
}
.tcell__ico--dark { color: #2B2B31; background: radial-gradient(circle, #F0F0F5 0%, rgba(240,240,245,0) 72%); }
.tcell__ico svg { width: 48rpx; height: 48rpx; }
.tcell__n {
  margin-top: 8rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: $ref-ink;
  white-space: nowrap;
}
.tcell__d {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: $ref-sub;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

/* 图片去背景 宽卡 */
.wide {
  margin: 10rpx 40rpx 0;
  background: var(--bg-gray);
  border-radius: 32rpx;
  padding: 22rpx 24rpx;
  display: flex;
  align-items: center;
  gap: 22rpx;
}
.wide:active { opacity: .94; }
.wide__thumb {
  /* 直接用参考图里完整的缩略图（自带外框与圆角），按 57:48 比例铺满 */
  width: 106rpx;
  height: 90rpx;
  border-radius: 20rpx;
  overflow: hidden;
  flex-shrink: 0;
}
.wide__thumb-img {
  width: 100%;
  height: 100%;
  display: block;
}
.wide__body { flex: 1; min-width: 0; }
.wide__t { display: block; font-size: 28rpx; font-weight: 700; color: $ref-ink; }
.wide__d { display: block; margin-top: 6rpx; font-size: 22rpx; color: $ref-sub; }
.wide__go {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #1B1B1F;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.wide__go svg { width: 30rpx; height: 30rpx; }

/* 最近使用 */
.recent { padding: 0 40rpx; }
.recent__item {
  background: var(--bg-card);
  border-radius: 28rpx;
  padding: 20rpx 22rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
}
.recent__item:active { opacity: .94; }
.recent__ico {
  width: 76rpx;
  height: 76rpx;
  border-radius: 20rpx;
  background: #FDEDE4;
  color: $ref-orange;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.recent__ico svg { width: 42rpx; height: 42rpx; }
.recent__body { flex: 1; min-width: 0; }
.recent__n { display: block; font-size: 28rpx; font-weight: 600; color: $ref-ink; }
.recent__t { display: block; margin-top: 6rpx; font-size: 22rpx; color: $ref-sub; }
.recent__chev { font-size: 36rpx; color: #C4C4CC; }

/* 热门工具：单行循环滚动（内容复制两份，位移 -50% 实现无缝） */
.marquee { overflow: hidden; margin: 0 32rpx; }
.marquee__track { display: flex; gap: 16rpx; width: max-content; animation: marquee-flow 26s linear infinite; }
.marquee__track .tcell { flex: 0 0 auto; width: 200rpx; }
@keyframes marquee-flow {
  from { transform: translateX(0); }
  to   { transform: translateX(-50%); }
}
/* 用户按住在看的时候暂停，避免点错 */
.marquee:active .marquee__track { animation-play-state: paused; }

/* 轮播推荐卡：切换时淡入；position:relative 让指示点相对卡片定位（否则会跑到页面右下角） */
.wide { position: relative; animation: wide-fade .45s ease; }
@keyframes wide-fade { from { opacity: .35; transform: translateY(4rpx); } to { opacity: 1; transform: none; } }
.wide__emoji { font-size: 44rpx; line-height: 1; }
.wide__thumb { display: flex; align-items: center; justify-content: center; }
.wide__dots { position: absolute; left: 50%; transform: translateX(-50%); bottom: 14rpx; display: flex; gap: 6rpx; }
.wide__dot { width: 8rpx; height: 8rpx; border-radius: 50%; background: rgba(0,0,0,.15); }
.wide__dot--on { background: var(--brand-primary); width: 16rpx; border-radius: 4rpx; }
</style>
