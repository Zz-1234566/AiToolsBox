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
  '--bg-color': '#F8F8F8',
  '--bg-white': '#FFFFFF',
  '--bg-gray': '#F5F5F5',
  '--text-primary': '#211E1E',
  '--text-secondary': '#656363',
  '--text-tertiary': '#8E8B8B',
  '--border-color': '#E8E8E8',
  '--divider-color': '#F0F0F0',
  // 语义色：浅色版（绿/红/橙）
  '--color-success': '#19be6b',
  '--color-success-bg': '#e8f7ed',
  '--color-danger': '#e54d42',
  '--color-danger-bg': '#fff5f5',
  '--color-danger-soft': '#fde9e9',
  '--color-warning': '#ff9900',
  '--color-warning-bg': '#fff8e6',
  '--color-warning-text': '#8a6300',
}

const darkVars = {
  '--bg-color': '#0A0A0A',
  '--bg-white': '#1A1A1A',
  '--bg-gray': '#2C2C2C',
  '--text-primary': '#F0F0F0',
  '--text-secondary': '#A0A0A0',
  '--text-tertiary': '#6B6B6B',
  '--border-color': '#2C2C2C',
  '--divider-color': '#252525',
  // 语义色：深色版（亮度提一档，深底上仍能看清）
  '--color-success': '#3dd683',
  '--color-success-bg': '#14331f',
  '--color-danger': '#ff6b62',
  '--color-danger-bg': '#3a1a1a',
  '--color-danger-soft': '#2c1414',
  '--color-warning': '#ffb84d',
  '--color-warning-bg': '#3a2a14',
  '--color-warning-text': '#ffd599',
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

function updateTabBarStyle(isDark) {
  uni.setTabBarStyle({
    color: isDark ? '#6B6B6B' : '#8E8B8B',
    selectedColor: isDark ? '#F0F0F0' : '#211E1E',
    backgroundColor: isDark ? '#1A1A1A' : '#FFFFFF',
    borderStyle: 'white',
  })
}

export function initTheme() {
  const isDark = !!uni.getStorageSync(STORAGE_KEY)
  applyTheme(isDark)
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
