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

export function applyTheme(isDark) {
  setCSSVars(isDark ? darkVars : lightVars)
  updateTabBarStyle(isDark)
}

export function toggleTheme(isDark) {
  uni.setStorageSync(STORAGE_KEY, isDark ? 1 : '')
  applyTheme(isDark)
}

export function getIsDarkTheme() {
  return !!uni.getStorageSync(STORAGE_KEY)
}
