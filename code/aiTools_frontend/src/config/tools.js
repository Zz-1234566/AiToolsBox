// 顶层工具配置：集中定义所有工具，按模块（tool_type）分组
// tool_type 与数据库 sys_aitools_tool.tool_type 对应

// 工具分类（模块），code 即 tool_type
export const CATEGORIES = [
  { code: 'AI办公助手', tools: ['work-summary', 'doc-keypoint-extract', 'weekly-report', 'meeting-minutes', 'ocr-recognize'] },
  { code: '图片创意工具', tools: ['id-photo-bg-change', 'portrait-bg-replace', 'image-compress', 'qr-code-gen'] },
  { code: '效率小工具', tools: ['todo-list', 'pomodoro', 'password-gen'] }
]

// ==================== 校验规则 ====================
// 每个工具 + 每种输入方式 都有自己的校验规则：
//   {
//     file:   { type: 'text' | 'single' | 'batch', min: 1, error: '...' },  // 输入校验
//     prompt: { type: 'any', min: 1, error: '...' }                            // 提示词校验（type: 'any' 表示任一非空）
//   }
//   或 { unsupported: true, error: '...' }                                    // 该输入方式未支持
//
// 校验器由 tool-common.vue 的 validate() 统一处理，前置校验不通过直接 return，不进 if-else 分支。

const PROMPT_REQUIRED = { type: 'any', min: 1, error: '请填写格式或生成内容提示词' }

// 每个工具的完整配置
// inputTypes: 该工具支持的输入方式数组（text 文字 / file 文件 / image 图片 / audio 音频）
// defaultInput: 默认选中的输入方式
export const TOOLS = {
  'work-summary': {
    name: '工作总结', icon: 'summary', category: 'AI办公助手', realized: true,
    desc: '输入零散的工作记录，AI 自动整理成结构化的工作内容总结。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'text',
    placeholder: '请输入今天的工作内容...',
    actionText: '开始总结', resultTitle: '总结结果', resultPlaceholder: '整理后的工作总结将在这里显示...',
    validateRules: {
      text:   { token: true, file: { type: 'text', min: 1, error: '请输入内容' }, prompt: PROMPT_REQUIRED },
      file:   { token: true, file: { type: 'single', min: 1, error: '请先上传文件' }, prompt: PROMPT_REQUIRED },
      image:  { unsupported: true, error: '该工具请使用文字或文件输入' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'doc-keypoint-extract': {
    name: '文档重点提取', icon: 'summary', category: 'AI办公助手', realized: true,
    desc: '上传多个文档（最多 10 个，总大小 200MB），AI 逐个提炼核心要点和待办事项。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'file', fileType: 'document',
    uploadTitle: '上传文档', uploadDesc: '支持 PDF、Word、TXT 格式',
    placeholder: '请输入或粘贴文档内容...',
    actionText: '开始提取', resultTitle: '提取结果', resultPlaceholder: '文档重点将在这里显示...',
    // 多文件规则（BatchFilePicker 组件读取）
    fileRule: {
      accept: '.txt,.pdf,.docx',
      maxCount: 10,
      maxTotalSize: 200 * 1024 * 1024,         // 200MB
      title: '上传文档',
      desc: '支持 PDF、Word、TXT 格式 · 最多 10 个文件 · 总大小 200MB',
      // 提示：BatchFilePicker 在 desc 下方渲染，列出 PDF 限制
      notice: 'PDF 支持说明：\n• ✅ 数字型 PDF（Word/电子发票/文本型 PDF）可直接解析\n• ❌ 扫描型 PDF（手机拍照的纸质文件）暂不支持，文字识别需用【智能识别】工具且先转图片',
      uploadType: 'docBatch',                  // 标识：调 batchUpload
      apiPath: '/api/ai-office/document-summary/batch-upload'
    },
    validateRules: {
      file:   { token: true, file: { type: 'batch', min: 1, error: '请至少选择 1 个文件' }, prompt: PROMPT_REQUIRED },
      image:  { unsupported: true, error: '该工具请使用文件上传' },
      text:   { unsupported: true, error: '该工具请使用文件上传' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'weekly-report': {
    name: '周报生成', icon: 'weekly', category: 'AI办公助手', realized: false,
    desc: '输入本周工作内容，一键生成结构化的工作周报。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'text',
    placeholder: '请输入本周完成的工作内容...',
    actionText: '生成周报', resultTitle: '周报内容', resultPlaceholder: '生成的周报将在这里显示...',
    validateRules: {
      text:   { file: { type: 'text', min: 1, error: '请输入内容' }, prompt: PROMPT_REQUIRED },
      file:   { file: { type: 'single', min: 1, error: '请先上传文件' }, prompt: PROMPT_REQUIRED },
      image:  { unsupported: true, error: '该工具请使用文字输入' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'meeting-minutes': {
    name: '会议纪要', icon: 'meeting', category: 'AI办公助手', realized: false,
    desc: '输入会议内容，AI 帮你整理会议核心结论和行动项。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'text',
    placeholder: '请输入会议内容或语音转写文字...',
    actionText: '整理纪要', resultTitle: '会议纪要', resultPlaceholder: '整理后的会议纪要将在这里显示...',
    validateRules: {
      text:   { file: { type: 'text', min: 1, error: '请输入内容' }, prompt: PROMPT_REQUIRED },
      file:   { file: { type: 'single', min: 1, error: '请先上传文件' }, prompt: PROMPT_REQUIRED },
      image:  { unsupported: true, error: '该工具请使用文字输入' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'ocr-recognize': {
    name: '智能识别', icon: 'ocr', category: 'AI办公助手', realized: true,
    desc: '上传多张图片（最多 10 个，总大小 200MB），AI 自动识别图片中的文字内容并整理成结构化结果。',
    inputTypes: ['image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传图片', uploadDesc: '支持 JPG、PNG 格式（PDF 请用【文档重点提取】）',
    actionText: '开始识别', resultTitle: '识别结果', resultPlaceholder: '识别结果将在这里显示...',
    // 多文件规则（BatchFilePicker 组件读取）
    fileRule: {
      accept: '.jpg,.jpeg,.png',
      maxCount: 10,
      maxTotalSize: 200 * 1024 * 1024,         // 200MB
      title: '上传图片',
      desc: '支持 JPG、PNG 格式 · 最多 10 个文件 · 总大小 200MB',
      notice: '本工具仅支持图片（PNG/JPG/JPEG）；\n• PDF 文件请改用【文档重点提取】工具',
      uploadType: 'ocrBatch',                  // 标识：调 ocrBatchUpload
      apiPath: '/api/ai-office/ocr-recognize/batch-upload'
    },
    validateRules: {
      image:  { token: true, file: { type: 'batch', min: 1, error: '请至少选择 1 个图片' }, prompt: PROMPT_REQUIRED },
      text:   { unsupported: true, error: '该工具请上传图片' },
      file:   { unsupported: true, error: '该工具请上传图片' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'id-photo-bg-change': {
    name: '证件照换背景色', icon: 'bg-color', category: '图片创意工具', realized: false,
    desc: '上传证件照，快速更换背景颜色。',
    inputTypes: ['image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传证件照', uploadDesc: '支持 JPG、PNG 格式',
    actionText: '开始处理', resultTitle: '处理结果', resultPlaceholder: '处理后的图片将在这里显示...',
    validateRules: {
      image:  { file: { type: 'single', min: 1, error: '请先上传证件照' } },
      text:   { unsupported: true, error: '该工具请上传图片' },
      file:   { unsupported: true, error: '该工具请上传图片' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'portrait-bg-replace': {
    name: '人像换背景图', icon: 'bg-image', category: '图片创意工具', realized: false,
    desc: '上传人像照片，AI 自动抠图并替换背景。',
    inputTypes: ['image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传人像照片', uploadDesc: '支持 JPG、PNG 格式',
    actionText: '开始抠图', resultTitle: '处理结果', resultPlaceholder: '处理后的图片将在这里显示...',
    validateRules: {
      image:  { file: { type: 'single', min: 1, error: '请先上传人像照片' } },
      text:   { unsupported: true, error: '该工具请上传图片' },
      file:   { unsupported: true, error: '该工具请上传图片' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'image-compress': {
    name: '图片压缩', icon: 'compress', category: '图片创意工具', realized: false,
    desc: '上传图片，压缩图片大小方便分享。',
    inputTypes: ['image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传图片', uploadDesc: '支持 JPG、PNG 格式',
    actionText: '开始压缩', resultTitle: '压缩结果', resultPlaceholder: '压缩后的图片将在这里显示...',
    validateRules: {
      image:  { file: { type: 'single', min: 1, error: '请先上传图片' } },
      text:   { unsupported: true, error: '该工具请上传图片' },
      file:   { unsupported: true, error: '该工具请上传图片' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'qr-code-gen': {
    name: '二维码生成', icon: 'qr', category: '图片创意工具', realized: false,
    desc: '输入网址或文本，生成可扫描的二维码。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入网址或文本内容...',
    actionText: '生成二维码', resultTitle: '二维码', resultPlaceholder: '生成的二维码将在这里显示...',
    validateRules: {
      text:   { file: { type: 'text', min: 1, error: '请输入内容' } },
      file:   { unsupported: true, error: '该工具请输入文本' },
      image:  { unsupported: true, error: '该工具请输入文本' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'todo-list': {
    name: '待办清单', icon: 'todo', category: '效率小工具', realized: false,
    desc: '输入待办事项，快速整理成清单。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入待办事项，用逗号分隔...',
    actionText: '生成清单', resultTitle: '待办清单', resultPlaceholder: '生成的待办清单将在这里显示...',
    validateRules: {
      text:   { file: { type: 'text', min: 1, error: '请输入待办事项' } },
      file:   { unsupported: true, error: '该工具请输入文本' },
      image:  { unsupported: true, error: '该工具请输入文本' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'pomodoro': {
    name: '番茄钟', icon: 'tomato', category: '效率小工具', realized: false,
    desc: '设置专注时长，开始番茄工作法。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入专注时长（分钟）...',
    actionText: '开始专注', resultTitle: '专注状态', resultPlaceholder: '专注状态将在这里显示...',
    validateRules: {
      text:   { file: { type: 'text', min: 1, error: '请输入专注时长' } },
      file:   { unsupported: true, error: '该工具请输入专注时长' },
      image:  { unsupported: true, error: '该工具请输入专注时长' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  },
  'password-gen': {
    name: '密码生成', icon: 'password', category: '效率小工具', realized: false,
    desc: '输入密码长度，生成随机强密码。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入密码长度...',
    actionText: '生成密码', resultTitle: '生成密码', resultPlaceholder: '生成的密码将在这里显示...',
    validateRules: {
      text:   { file: { type: 'text', min: 1, error: '请输入密码长度' } },
      file:   { unsupported: true, error: '该工具请输入密码长度' },
      image:  { unsupported: true, error: '该工具请输入密码长度' },
      audio:  { unsupported: true, error: '音频输入功能开发中' }
    }
  }
}

// 已实现的工具 code（用于提示词管理页工具下拉过滤）
export const REALIZED_TOOLS = Object.entries(TOOLS).filter(([, v]) => v.realized).map(([k]) => k)

// 根据 toolId 获取工具配置（带默认值兜底）
export const getTool = (toolId) => TOOLS[toolId] || {
  name: '工具详情',
  desc: '暂无该工具信息',
  inputTypes: ['text'],
  defaultInput: 'text',
  placeholder: '请输入内容...',
  actionText: '开始处理',
  resultTitle: '处理结果',
  resultPlaceholder: '结果将在这里显示...'
}

/**
 * 通用校验器：按工具 + 输入方式，校验输入和提示词
 * @param {String} toolId 工具 code
 * @param {String} inputType 输入方式（text / file / image / audio）
 * @param {Object} ctx 上下文 { filePath, batchFiles, inputText, promptFormat, promptGenerate, token }
 * @returns {String|null} 第一个失败的错误文案，null = 通过
 */
export const validate = (toolId, inputType, ctx) => {
  const tool = getTool(toolId)
  const rule = tool.validateRules && tool.validateRules[inputType]
  if (!rule) return '该输入方式暂未接入'
  if (rule.unsupported) return rule.error || '该输入方式暂未接入'
  // token 校验（可选规则：rule.token === true 时必须有 token）
  if (rule.token && !ctx.token) {
    return '请先登录'
  }
  // file 输入校验
  if (rule.file) {
    const min = rule.file.min || 1
    let ok = true
    if (rule.file.type === 'batch') {
      ok = (ctx.batchFiles && ctx.batchFiles.length >= min)
    } else if (rule.file.type === 'single') {
      ok = !!(ctx.filePath)
    } else if (rule.file.type === 'text') {
      ok = !!(ctx.inputText && ctx.inputText.trim().length >= min)
    }
    if (!ok) return rule.file.error
  }
  // prompt 校验
  if (rule.prompt) {
    const min = rule.prompt.min || 1
    const filled = ((ctx.promptFormat || '') + (ctx.promptGenerate || '')).trim().length
    if (filled < min) return rule.prompt.error
  }
  return null
}
