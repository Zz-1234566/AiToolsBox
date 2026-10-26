/**
 * 跨端文件选择 —— **全项目唯一入口**。
 *
 * <p><b>为什么要有这个模块</b>：各端可用的选文件 API 完全不同，且**不是「都能用，只是写法不同」**：
 * <table border="1">
 *   <tr><th>能力</th><th>H5</th><th>微信/QQ 小程序</th><th>App（Android/iOS）</th></tr>
 *   <tr><td>{@code <input type="file">} 原生多选</td><td>✅</td><td>❌</td><td>❌</td></tr>
 *   <tr><td>{@code uni.chooseImage}（图片）</td><td>✅</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>{@code uni.chooseMessageFile}（文档/音频）</td><td>❌</td><td>✅</td><td>❌</td></tr>
 *   <tr><td>{@code uni.chooseFile}（文档）</td><td>✅</td><td>❌</td><td>❌</td></tr>
 * </table>
 * 换句话说：**App 端目前只能选图片**，文档/音频在 App 端无解
 * （uni-app 官方文档里 App 一列全是 ❌；要支持需引插件市场「文件选择」或用 {@code plus.io} 自绘）。
 *
 * <p>此前这段平台分支在 4 个页面各写一份（{@code tool-common.vue} ×2、{@code workflow.vue}、
 * {@code image-bg-remover.vue}），改一处忘一处就会出现「某端点了没反应」。
 * 本模块把它收敛到一处，并保证：
 * <ol>
 *   <li><b>能力探测</b>：用 {@code typeof} 判断 API 是否存在，不存在就返回 {@code reason:'unsupported'}，
 *       **由调用方给出明确提示**（不静默失败）；</li>
 *   <li><b>返回值归一化</b>：各端返回结构不同（H5 是原生 {@code File}，小程序是临时路径），
 *       统一成 {@code { fileName, file, path, size }}；</li>
 *   <li><b>扩展名兜底过滤</b>：H5 的 {@code accept} 只是浏览器提示，用户仍可手动选「所有文件」，
 *       所以要按扩展名再过滤一遍。</li>
 * </ol>
 */

/**
 * 归一化后的文件对象
 * @typedef {Object} PickedFile
 * @property {string} fileName 原始文件名（含扩展名）
 * @property {*} file H5 端的原生 File 对象；其它端为 null
 * @property {string} path 临时路径 / 本地路径（H5 端为空串）
 * @property {number} size 文件字节数；拿不到时为 0
 */

/**
 * 选择结果
 * @typedef {Object} PickResult
 * @property {boolean} ok 是否选到了文件
 * @property {PickedFile[]} files 选中的文件（已按扩展名过滤）
 * @property {'unsupported'|'cancelled'|'error'|'empty'} [reason]
 *   失败原因：
 *   - 'unsupported' 当前平台不支持该类型的文件选择
 *   - 'cancelled' 用户取消
 *   - 'error' 选择过程出错
 *   - 'empty' 选中的文件都被扩展名过滤掉了
 * @property {string} [message] 面向用户的中文提示（调用方可直接 toast）
 */

/** 取文件扩展名（小写，不含点） */
export const extOf = (name) => {
  if (!name) return ''
  const i = name.lastIndexOf('.')
  return i >= 0 ? name.substring(i + 1).toLowerCase() : ''
}

/** 从路径末段取文件名（各端 tempFiles[i].name 可能缺失） */
export const nameFromPath = (p, fallback = '文件') => {
  if (!p) return fallback
  const seg = String(p).split('/').pop() || ''
  return seg || fallback
}

/**
 * 选文件（跨端）。
 *
 * @param {Object} [opts]
 * @param {number} [opts.count=1] 最多选几个
 * @param {string} [opts.accept] 接受的扩展名，如 '.pdf,.docx'；空串 = 不限
 * @param {'image'|'audio'|'file'} [opts.fileType='file']
 *        'image' 走 chooseImage（全平台可用）；'audio' / 'file' 走 chooseMessageFile
 * @param {string[]} [opts.sourceType] 图片来源，如 ['album','camera']
 * @param {string} [opts.fileLabel='文件'] 文案里的文件名（如「录音文件」），用于拼提示
 * @returns {Promise<PickResult>} 选择结果（永不 reject）
 */
export const pickFiles = (opts = {}) => {
  const count = Math.max(1, opts.count || 1)
  const accept = opts.accept || ''
  const fileType = opts.fileType || 'file'
  const fileLabel = opts.fileLabel || '文件'
  const exts = accept.split(',').map(s => s.trim().replace(/^\./, '').toLowerCase()).filter(Boolean)

  /** 扩展名过滤：空 exts = 不限 */
  const allowed = (name) => {
    if (!exts.length) return true
    const e = extOf(name)
    return !!e && exts.includes(e)
  }

  const notSupported = () => ({
    ok: false,
    files: [],
    reason: 'unsupported',
    message: `当前平台暂不支持选择${fileLabel}`
  })

  /**
   * 失败回调 → 结果。
   *
   * <p>uni 的 fail 回调会带 errMsg，里面能区分「用户主动取消」与「权限被拒」：
   * <ul>
   *   <li>取消：`chooseImage:fail cancel` / `chooseMessageFile:fail cancel`</li>
   *   <li>权限：`...:fail auth deny` / `permission` / `authorize`</li>
   * </ul>
   * 两者都返回 'cancelled' 会被调用方当成「用户取消」而**不提示**，
   * 于是「点了没反应」—— 用户以为 App 卡了。权限被拒必须明确告知。
   */
  const failed = (err) => {
    const msg = (err && (err.errMsg || err.message)) || ''
    const denied = /auth|permission|authorize|deny/i.test(msg)
    if (denied) {
      return {
        ok: false,
        files: [],
        reason: 'error',
        message: `没有访问${fileLabel}的权限，请在系统设置里允许`
      }
    }
    return { ok: false, files: [], reason: 'cancelled' }
  }

  // #ifdef H5
  // H5：原生 <input type="file">，支持多选，是唯一能一次选多个文件的路径
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.multiple = count > 1
    if (accept) input.accept = accept
    input.onchange = () => {
      const picked = Array.from(input.files || [])
      if (!picked.length) {
        resolve({ ok: false, files: [], reason: 'cancelled' })
        return
      }
      // accept 只是浏览器提示，用户仍可手动选「所有文件」→ 按扩展名再过滤一遍
      const valid = picked.filter(f => allowed(f.name))
      if (!valid.length) {
        resolve({
          ok: false,
          files: [],
          reason: 'empty',
          message: exts.length
            ? `不支持的${fileLabel}类型，仅支持 ${exts.map(e => e.toUpperCase()).join('/')}`
            : undefined
        })
        return
      }
      resolve({
        ok: true,
        files: valid.slice(0, count).map(f => ({
          fileName: f.name,
          file: f,
          path: '',
          size: f.size || 0
        }))
      })
    }
    input.click()
  })
  // #endif

  // #ifndef H5
  // 非 H5：uni 系列 API，按 fileType 分派
  if (fileType === 'image') {
    if (typeof uni.chooseImage !== 'function') return Promise.resolve(notSupported())
    return new Promise((resolve) => {
      uni.chooseImage({
        count,
        sourceType: opts.sourceType || ['album', 'camera'],
        extension: exts,
        success: (res) => {
          const paths = res.tempFilePaths || []
          const files = res.tempFiles || []
          if (!paths.length) {
            resolve({ ok: false, files: [], reason: 'cancelled' })
            return
          }
          const list = paths.map((p, i) => ({
            fileName: (files[i] && files[i].name) || nameFromPath(p),
            file: files[i] || null,
            path: p,
            size: (files[i] && files[i].size) || 0
          }))
          const valid = list.filter(f => allowed(f.fileName))
          if (!valid.length) {
            resolve({
              ok: false, files: [], reason: 'empty',
              message: exts.length
                ? `不支持的${fileLabel}类型，仅支持 ${exts.map(e => e.toUpperCase()).join('/')}`
                : undefined
            })
            return
          }
          resolve({ ok: true, files: valid.slice(0, count) })
        },
        fail: (err) => resolve(failed(err))
      })
    })
  }

  // 文档 / 音频：仅微信/QQ 小程序有 chooseMessageFile
  if (typeof uni.chooseMessageFile === 'function') {
    return new Promise((resolve) => {
      uni.chooseMessageFile({
        count,
        type: fileType === 'audio' ? 'audio' : 'file',
        extension: exts,
        success: (res) => {
          const files = res.tempFiles || []
          if (!files.length) {
            resolve({ ok: false, files: [], reason: 'cancelled' })
            return
          }
          const list = files.map((f) => ({
            fileName: f.name || nameFromPath(f.path),
            file: f,
            path: f.path || '',
            size: f.size || 0
          }))
          const valid = list.filter(f => allowed(f.fileName))
          if (!valid.length) {
            resolve({
              ok: false, files: [], reason: 'empty',
              message: exts.length
                ? `不支持的${fileLabel}类型，仅支持 ${exts.map(e => e.toUpperCase()).join('/')}`
                : undefined
            })
            return
          }
          resolve({ ok: true, files: valid.slice(0, count) })
        },
        fail: (err) => resolve(failed(err))
      })
    })
  }

  // 走到这里 = 当前平台（App 端）没有文件选择器
  return Promise.resolve(notSupported())
  // #endif
}
