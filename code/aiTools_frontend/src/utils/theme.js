/**
 * 主题（深色 / 浅色）变量与切换。
 *
 * 【为什么不能只写 document.documentElement】
 * App 端（app-vue）是「逻辑层(jscore) / 视图层(webview)」分离架构：
 * 逻辑层**没有 document**，所以 `document.documentElement.style.setProperty()`
 * 在 App 端第一行就 return —— 深色主题只有 tabBar 变了，页面主体纹丝不动
 * （uni.scss 里 99 处 `var(--x, fallback)` 全部走浅色 fallback）。
 *
 * 【本文件的解法：数据驱动 + 视图层绑定】
 * CSS 自定义属性会**继承**，所以只要把变量写到页面根节点上，
 * 整棵子树都会拿到深色值。于是：
 *   - 维护一份响应式 `themeVars`（深/浅两套值）；
 *   - 需要主题的页面在**根节点**绑 `:style="themeVars"`；
 *   - 视图层（webview）收到 style 绑定后由 Vue runtime 执行
 *     `style.setProperty('--x', v)`（实测产物里就有
 *     `t.startsWith("--")?e.setProperty(t,n)` 这段），**逻辑层全程不碰 DOM**。
 *
 * H5 端仍保留 document 写入：因为页面根节点不一定都绑了 themeStyle，
 * documentElement 是全局兜底，两条路径并存不冲突（值相同）。
 */
import { reactive, computed } from 'vue'

const STORAGE_KEY = 'isDarkTheme'

const lightVars = {
  '--bg-color': '#F6F6FB',
  '--bg-page': '#F6F6FB',
  '--bg-white': '#FFFFFF',
  '--bg-card': '#FFFFFF',
  '--bg-gray': '#F2F2F7',
  '--bg-hover': '#F2F2F7',
  '--bg-disabled': '#E8E8EF',
  '--text-primary': '#17171A',
  '--text-secondary': '#5C5C66',
  '--text-tertiary': '#86868F',
  '--border-color': '#ECECF2',
  '--border-light': '#F2F2F7',
  '--divider-color': '#F2F2F7',
  '--brand-primary': '#F5671D',
  '--brand-primary-hover': '#E85A12',
  '--brand-primary-pressed': '#D24F0F',
  '--brand-primary-light': '#F2F2F7',
  '--brand-secondary': '#8E5A6E',
  '--accent': '#F5671D',
  '--color-primary': '#F5671D',
  // 工具图标块：实色
  '--gradient-brand': '#F5671D',
  '--gradient-doc': '#B08A3E',
  '--gradient-image': '#F5671D',
  '--gradient-dev': '#4F7A6D',
  '--gradient-audio': '#8E5A6E',
  '--gradient-video': '#9A6A4A',
  '--gradient-ocr': '#5B7391',
  '--gradient-text': '#6B7A4F',
  '--gradient-code': '#46596B',
  // 语义色：浅色版
  '--color-success': '#3F7D5A',
  '--color-success-bg': '#E8F7EF',
  '--color-danger': '#B4433A',
  '--color-danger-bg': '#FDECEA',
  '--color-danger-soft': '#FEF6F5',
  '--color-warning': '#A87A2E',
  '--color-warning-bg': '#FEF4E4',
  '--color-warning-text': '#8A5A12',
}

const darkVars = {
  // base（更暗）/ elevated（提亮一档）两级，不用纯黑
  '--bg-color': '#14130F',
  '--bg-page': '#14130F',
  '--bg-white': '#1E1B16',
  '--bg-card': '#1E1B16',
  '--bg-gray': '#282419',
  '--bg-hover': '#26221C',
  '--bg-disabled': '#2A2520',
  '--text-primary': '#EDE9E0',
  '--text-secondary': '#A9A49A',
  '--text-tertiary': '#928C81',
  '--border-color': '#332E26',
  '--border-light': '#2A2620',
  '--divider-color': '#26221C',
  // 主色在深底上略微提亮，保证对比度
  '--brand-primary': '#F5671D',
  '--brand-primary-hover': '#FF7A2E',
  '--brand-primary-pressed': '#D24F0F',
  // 浅主色在深色下必须是暗底，否则会出现刺眼白块
  '--brand-primary-light': '#3A2A22',
  '--brand-secondary': '#B07E90',
  '--accent': '#DE8264',
  '--color-primary': '#DE8264',
  '--gradient-brand': '#F5671D',
  '--gradient-doc': '#B08A3E',
  '--gradient-image': '#F5671D',
  '--gradient-dev': '#4F7A6D',
  '--gradient-audio': '#8E5A6E',
  '--gradient-video': '#9A6A4A',
  '--gradient-ocr': '#5B7391',
  '--gradient-text': '#6B7A4F',
  '--gradient-code': '#46596B',
  // 语义色：深色版（提亮，保证 4.5:1 以上）
  '--color-success': '#5FA57C',
  '--color-success-bg': '#1B2E23',
  '--color-danger': '#D9746A',
  '--color-danger-bg': '#3A1E1B',
  '--color-danger-soft': '#2A1614',
  '--color-warning': '#C9A05A',
  '--color-warning-bg': '#362C1A',
  '--color-warning-text': '#E8C68E',
}

/**
 * 当前生效的主题变量（内部状态，响应式）。
 */
const currentVars = reactive({ ...lightVars })

/**
 * 供页面绑定的主题变量（computed，**每次返回新对象**）。
 *
 * 用法：在需要主题的页面根节点绑 `:style="themeStyle"`，CSS 变量沿 DOM 继承下去。
 * 例：`<view class="page" :style="themeStyle">`
 *
 * 【为什么不用 reactive 对象直接绑】
 * App 端逻辑层 → 视图层是**跨层传值**（序列化后发到 webview）。
 * reactive 对象的**引用不变**，只有内部字段变了 ——
 * 跨层 diff 时若按引用比对，可能判定「没变」而不下发新值。
 * computed 每次重算都返回**全新对象**，引用必变 → 一定能触发视图层更新。
 */
export const themeStyle = computed(() => ({ ...currentVars }))

/**
 * 当前是否深色主题（响应式）。
 * 供个别需要「反色资源」的场景使用，例如深色下把深色 logo 反白。
 */
export const isDarkMode = computed(() => currentVars['--bg-page'] === darkVars['--bg-page'])

/**
 * H5 端兜底：把变量写到 documentElement。
 *
 * App 端逻辑层没有 document，这里直接跳过（App 端靠 themeStyle 绑定生效）。
 */
function setCSSVars(vars) {
  if (typeof document === 'undefined') return
  Object.entries(vars).forEach(([key, value]) => {
    document.documentElement.style.setProperty(key, value)
  })
}

const TAB_ITEMS = [
  { index: 0, key: 'home' },
  { index: 1, key: 'category' },
  { index: 2, key: 'workflow' },
  { index: 3, key: 'agent' },
  { index: 4, key: 'my' },
]

function updateTabBarStyle(isDark) {
  // 某些平台在非 tabBar 页调用 setTabBarStyle 会静默失败 —— 包 try/catch，
  // 避免「切主题」这个动作因为一个装饰性调用而整体失败。
  try {
    uni.setTabBarStyle({
      color: isDark ? '#A8A8B2' : '#9A9AA3',
      selectedColor: isDark ? '#FF8A4D' : '#F5671D',
      backgroundColor: isDark ? '#1E1B16' : '#FFFFFF',
      borderStyle: 'white',
      // 非 tabBar 页调用会失败（H5 下是 Promise reject，try/catch 抓不到）：
      // 传 fail 回调后 uni 不再返回 Promise，从根上消除未捕获拒绝
      fail: () => {},
    })
  } catch (e) {
    // 忽略：tabBar 配色只是装饰，CSS 变量才是主题的主体
  }
  // 图标是 PNG，不会跟随 selectedColor，必须按主题换图
  // 路径前缀：H5 下 setTabBarItem 按「当前路由」解析相对路径（会变成 /pages/static/... 而 404），
  // App 端则要求相对项目根目录的写法，两边不一致 —— 用条件编译各取一套。
  let iconBase = 'static/icons/'
  // #ifdef H5
  iconBase = '/static/icons/'
  // #endif
  TAB_ITEMS.forEach(({ index, key }) => {
    try {
      uni.setTabBarItem({
        index,
        iconPath: `${iconBase}${key}${isDark ? '-dark' : ''}-v2.png`,
        selectedIconPath: `${iconBase}${key}-active${isDark ? '-dark' : ''}-v2.png`,
        fail: () => {},
      })
    } catch (e) {
      // 同上：单项失败不影响其余
    }
  })
}

/**
 * 把当前主题的配色同步到 tabBar。
 *
 * <p>供 **tabBar 页的 onShow** 调用：主题是在 settings 页（**非 tabBar 页**）切换的，
 * 那次 setTabBarStyle 可能没生效 → 用户切回 tabBar 时仍是旧配色。
 * 在 tabBar 页每次 onShow 同步一次，保证 tabBar 与主题永远一致。
 */
/**
 * H5 专用：uni 的 tabBar 内部节点（.uni-tabbar）在路由切换时会被重建，
 * 重建时会把 pages.json 里的默认配色写回去 —— 表现为「页面是深色，底栏还是白的」。
 * 这里挂一个轻量监听：节点重建后若配色与当前主题不符，就补设一次。
 * （App 端 tabBar 在原生层，setTabBarStyle 本身就是持久的，不需要这段。）
 */
function watchTabBarRebuild() {
  // #ifdef H5
  if (typeof document === 'undefined' || typeof MutationObserver === 'undefined') return
  if (window.__tabbarWatcher) return
  let timer = null
  const obs = new MutationObserver(() => {
    if (timer) return
    timer = setTimeout(() => {
      timer = null
      const bar = document.querySelector('.uni-tabbar')
      if (!bar) return
      const isDark = !!uni.getStorageSync(STORAGE_KEY)
      const want = isDark ? 'rgb(30, 27, 22)' : 'rgb(255, 255, 255)'
      if (getComputedStyle(bar).backgroundColor !== want) {
        updateTabBarStyle(isDark)
      }
    }, 50)
  })
  obs.observe(document.body, { childList: true, subtree: true })
  window.__tabbarWatcher = obs
  // #endif
}

export function refreshTabBarStyle() {
  const isDark = !!uni.getStorageSync(STORAGE_KEY)
  updateTabBarStyle(isDark)
  watchTabBarRebuild()
}

export function initTheme() {
  const isDark = !!uni.getStorageSync(STORAGE_KEY)
  applyTheme(isDark)
  watchTabBarRebuild()
  return isDark
}

function applyTheme(isDark) {
  const vars = isDark ? darkVars : lightVars
  // 数据驱动：App 端页面靠这个拿到主题变量
  Object.keys(currentVars).forEach((key) => {
    currentVars[key] = vars[key]
  })
  // H5 端兜底：全局写 documentElement
  setCSSVars(vars)
  updateTabBarStyle(isDark)
}

export function toggleTheme(isDark) {
  uni.setStorageSync(STORAGE_KEY, isDark ? 1 : '')
  applyTheme(isDark)
}
