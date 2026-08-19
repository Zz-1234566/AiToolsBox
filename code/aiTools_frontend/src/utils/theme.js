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
