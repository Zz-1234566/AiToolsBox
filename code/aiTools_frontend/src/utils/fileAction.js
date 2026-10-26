/**
 * 产物类型判定 + 下载/打开的唯一出口。
 *
 * 【为什么必须有这个文件】
 * 1) 类型判定：后端 ToolOutput.outputType 约定为 1文本/2图片/3视频/4音频/5文件，
 *    但历史记录走的是「把产物 URL 写进 output_content」这条老路，
 *    只能靠扩展名反推。改造前 history.vue / history-detail.vue / workflowView.js
 *    各自维护一份扩展名数组，同一工具在不同页面展示口径不一致
 *    （列表页显示裸 URL，详情页显示图片）。
 * 2) 下载：改造前项目里有 5 份 downloadFile / <a download> 复制品，
 *    有的没做 403 过期处理、有的 App 端不存相册、有的带死代码。
 *
 * 【约束】新增文件下载 / 产物展示逻辑前，先 grep 本文件确认是否已有现成实现，
 * 不允许再写第 N 份（见 AGENTS.md「前端规范」§3）。
 *
 * 类型判定的底层工具（isFileUrl / extensionOf / fileNameFromUrl）仍复用
 * fileDisplayName.js，本文件只做「产物语义」的聚合，不再重复实现。
 */
import { isFileUrl, extensionOf, fileNameFromUrl, isAudioExt } from './fileDisplayName'

// ==================== 扩展名词典（唯一一份） ====================
const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg']
const VIDEO_EXT = ['mp4', 'mov', 'webm', 'avi', 'mkv']

/** 图片扩展名 */
const isImageExt = (ext) => IMAGE_EXT.indexOf(String(ext || '').toLowerCase()) >= 0
/** 视频扩展名 */
const isVideoExt = (ext) => VIDEO_EXT.indexOf(String(ext || '').toLowerCase()) >= 0

/**
 * 产物类型判定 —— 全项目唯一入口。
 *
 * @param {String|Number} output 产物 URL，或后端 outputType 数字
 * @returns {'text'|'image'|'video'|'audio'|'file'}
 *   text = 纯文本（AI 输出、摘要等，非 URL 字符串）
 *   image / video / audio = 可直接内联展示的可视产物
 *   file = 其他可下载文件（pdf、docx…）
 */
export const outputKindOf = (output) => {
  // 传了后端 outputType（1文本 2图片 3视频 4音频 5文件）时直接映射
  if (typeof output === 'number') {
    return ({ 1: 'text', 2: 'image', 3: 'video', 4: 'audio', 5: 'file' })[output] || 'text'
  }
  const s = String(output || '').trim()
  if (!s) return 'text'
  // 整串是一个 URL 才按文件判定；正文里内嵌链接的文本按 text 处理
  if (!isFileUrl(s)) return 'text'
  const ext = extensionOf(fileNameFromUrl(s))
  if (isImageExt(ext)) return 'image'
  if (isVideoExt(ext)) return 'video'
  if (isAudioExt(ext)) return 'audio'
  return 'file'
}

/**
 * 主操作按钮的文案 —— 文本走「复制结果」，可视产物走「下载」。
 * 与 history-detail 的按钮口径保持一致。
 */
export const primaryActionLabel = (output) => {
  const k = outputKindOf(output)
  return ({
    text: '复制结果',
    image: '下载图片',
    video: '下载视频',
    audio: '下载音频',
    file: '下载文件'
  })[k]
}

/** 产物在页面上的短描述（列表页用；文本取前 N 字，可视产物取文件名） */
export const outputBrief = (output, limit = 28) => {
  const k = outputKindOf(output)
  if (k === 'text') {
    const t = String(output || '').replace(/\s+/g, ' ').trim()
    if (!t) return '—'
    return t.length > limit ? t.slice(0, limit) + '…' : t
  }
  const name = fileNameFromUrl(output) || '产物'
  return name.length > limit ? name.slice(0, limit) + '…' : name
}

// ==================== 下载 / 打开 ====================

/**
 * 下载或打开一个产物。
 *
 * 跨端策略：
 * - H5：<a download> 直接存盘。跨域图片同样可下载，无需 fetch 转 blob。
 * - 非 H5：先 downloadFile 拿本地路径；图片存相册，其余 openDocument 让用户在
 *   文件管理器里保存。
 *
 * COS 私有签名 URL 有效期有限（cos.signed-url-ttl-seconds，默认 3600s），
 * 过期返回 403，这里给明确提示而不是透出英文错误码。
 *
 * @param {Object} target { url, name?, kind? }
 * @param {Object} [opts]   { toast?: boolean } 是否在结束后弹提示
 */
export const downloadOutput = (target, opts = {}) => {
  if (!target || !target.url) {
    uni.showToast({ title: '文件链接不可用', icon: 'none' })
    return
  }
  const url = target.url
  const name = target.name || fileNameFromUrl(url) || 'output'
  const kind = target.kind || outputKindOf(url)

  // #ifdef H5
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.target = '_blank'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  if (opts.toast !== false) uni.showToast({ title: '已开始下载', icon: 'success' })
  return
  // #endif

  // #ifndef H5
  uni.showLoading({ title: '下载中…', mask: true })
  // ⚠️ uni.showLoading 与 uni.showToast **共用同一个浮层**：
  //    在 showToast 之后再调 hideLoading，会把刚弹出的 toast 一起顶掉
  //    —— 用户根本看不到「下载失败（链接可能已过期）」。
  //    所以这里用 toastShown 标记：已经弹过 toast 就不再 hideLoading。
  let toastShown = false
  const showToast = (title, icon = 'none') => {
    toastShown = true
    uni.showToast({ title, icon })
  }
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode !== 200) {
        showToast('下载失败（链接可能已过期）')
        return
      }
      if (kind === 'image') {
        uni.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: () => showToast('已保存到相册', 'success'),
          fail: () => showToast('保存失败，可长按图片另存')
        })
      } else {
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true,
          fail: () => showToast('无法打开该文件')
        })
      }
    },
    fail: () => showToast('下载失败'),
    complete: () => { if (!toastShown) uni.hideLoading() }
  })
  // #endif
}

/**
 * 把**本地临时文件**（canvas 合成产物等）保存到系统相册。
 *
 * 【为什么单独提供】
 * `downloadOutput` 走的是「URL → downloadFile → 相册」链路，
 * 但 canvas 合成出来的图**本来就在本地**（canvasToTempFilePath 的 tempFilePath），
 * 再走一遍 downloadFile 既多余又会失败（本地路径不是网络地址）。
 * 故提供本函数作为「本地图片 → 相册」的唯一出口。
 *
 * 【提示时机】
 * 成功/失败提示都在**回调里**弹 —— 调用方若在调用后立刻弹「下载完成」，
 * 会变成虚假成功提示（下载其实还没结束，甚至可能失败）。
 *
 * @param {String} filePath 本地文件路径（canvasToTempFilePath 产出）
 * @param {Object} [opts]   { successText?, failText? } 自定义提示文案
 */
export const saveLocalImageToAlbum = (filePath, opts = {}) => {
  // #ifdef H5
  // H5 端没有相册概念，交给调用方走 <a download>；此处不处理
  return
  // #endif

  // #ifndef H5
  if (!filePath) {
    uni.showToast({ title: '文件链接不可用', icon: 'none' })
    return
  }
  uni.saveImageToPhotosAlbum({
    filePath,
    success: () => uni.showToast({ title: opts.successText || '已保存到相册', icon: 'success' }),
    fail: (err) => {
      const msg = (err && err.errMsg) || ''
      // 权限被拒要给明确提示（用户点了没反应是最难排查的体验）
      const text = /auth|deny|permission/i.test(msg)
        ? '保存失败：请授予相册权限'
        : (opts.failText || '保存失败，可长按图片另存')
      uni.showToast({ title: text, icon: 'none' })
    }
  })
  // #endif
}

/**
 * 把**网络资源**取到本地临时路径（不弹提示、不落相册）。
 *
 * 【用途】
 * App 端 canvas 的 `drawImage` 只接受**本地路径**，
 * 画网络图（如后端返回的 COS 签名 URL）必须先落到本地。
 * H5 端对应的是 `blob:` / `data:` 方案（见 image-bg-remover.vue 的 `_toLocalImageUrl`），
 * 两端在此收敛成同一个语义：**给我一个能画进 canvas 的本地路径**。
 *
 * 【与 downloadOutput 的区别】
 * `downloadOutput` 是「下载产物给用户」（带提示、落相册/打开文件）；
 * 本函数只是取资源（无提示、无用户可见副作用），失败返回空串由调用方降级。
 *
 * @param {String} url 网络地址（本地路径会原样返回）
 * @returns {Promise<String>} 本地临时路径；失败返回空串
 */
export const downloadToLocalPath = (url) => {
  // #ifdef H5
  // H5 端画网络图用 blob/data URL，无需下载到临时文件
  return Promise.resolve(url || '')
  // #endif

  // #ifndef H5
  if (!url) return Promise.resolve('')
  if (!/^https?:/i.test(url)) return Promise.resolve(url)
  return new Promise((resolve) => {
    uni.downloadFile({
      url,
      success: (res) => {
        resolve(res.statusCode === 200 ? res.tempFilePath : '')
      },
      fail: () => resolve('')
    })
  })
  // #endif
}

/**
 * 产物预览：图片全屏预览、视频/音频交给页面内控件，其余先下载再打开。
 * 与 downloadOutput 分开——「看」和「存」是两种用户意图。
 */
export const previewOutput = (target) => {
  if (!target || !target.url) {
    uni.showToast({ title: '文件链接不可用', icon: 'none' })
    return
  }
  const kind = target.kind || outputKindOf(target.url)
  if (kind === 'image') {
    uni.previewImage({ urls: [target.url], current: target.url })
    return
  }
  // 音频走「下载后试听」：小程序端 playVoice 只接受本地路径，直接传网络地址会失败
  if (kind === 'audio') {
    downloadOutput(target, { toast: false })
    return
  }
  // 视频：只在 **history-detail** 页有页面内 <video controls> 可直接播放；
  // 其它页（如 workflow-run）没有 video 元素，只能走「下载后用系统播放器打开」。
  // 这里统一走下载 —— 各页是否内联播放由页面自己决定，不在本函数里区分。
  if (kind !== 'video') downloadOutput(target)
}

/** 类型对应的 emoji 图标（与项目既有风格一致） */
export const outputIcon = (output) => {
  const k = outputKindOf(output)
  return ({ text: '📝', image: '🖼️', video: '🎬', audio: '🎵', file: '📎' })[k]
}
