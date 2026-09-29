import { createI18n } from 'vue-i18n'
import zh from './zh'
import en from './en'

const currentLocale = uni.getStorageSync('language') || 'zh'

// uni-app 内置组件（showModal / showLoading 等）的按钮文案走 uni 自己的 i18n，
// 从 localStorage['UNI_LOCALE'] 读取（见 uni-shared: UNI_STORAGE_LOCALE = 'UNI_LOCALE'）。
// 不设置时会 fallback 到 navigator.language → 英文环境下按钮显示 Cancel / OK。
// 项目自己的 i18n 用的键名是 'language'，与之无关，故此处显式同步 uni 的 locale 键。
try {
  uni.setStorageSync('UNI_LOCALE', currentLocale === 'en' ? 'en' : 'zh-Hans')
} catch (e) { /* 忽略存储异常 */ }

const i18n = createI18n({
  legacy: false,
  locale: currentLocale,
  fallbackLocale: 'zh',
  messages: { zh, en },
})

export default i18n
