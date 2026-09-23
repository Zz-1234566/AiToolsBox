// 顶层工具配置：集中定义所有工具，按模块（tool_type）分组
// tool_type 与数据库 sys_aitools_tool.tool_type 对应

// 工具分类（模块），code 即 tool_type
export const CATEGORIES = [
  { code: 'AI办公助手', tools: ['work-summary', 'doc-keypoint-extract', 'ai-file-reader', 'weekly-report', 'meeting-minutes', 'ocr-recognize'] },
  { code: '图片创意工具', tools: ['id-photo-bg-change', 'portrait-bg-replace', 'image-compress', 'qr-code-gen'] },
  { code: '效率小工具', tools: ['todo-list', 'pomodoro', 'password-gen'] }
]

// 工具分类 → 渐变类型映射（设计稿 tokens）
// doc / image / dev / audio / video / ocr / text / code / brand
const CATEGORY_TO_GRADIENT = {
  'AI办公助手': 'doc',
  '图片创意工具': 'image',
  '效率小工具': 'dev'
}

const CATEGORY_TO_TAG = {
  'AI办公助手': '办公',
  '图片创意工具': '图片',
  '效率小工具': '效率'
}

/**
 * 根据工具 ID 返回展示用的渐变类型（用于 ToolIcon / ToolCard）
 * @param {string} toolId
 * @returns {string} gradient name
 */
export function getToolGradient(toolId) {
  const tool = TOOLS[toolId]
  if (!tool) return 'brand'
  return CATEGORY_TO_GRADIENT[tool.category] || 'brand'
}

/**
 * 根据工具 ID 返回分类 tag 文字
 * @param {string} toolId
 * @returns {string} tag label
 */
export function getToolCategoryTag(toolId) {
  const tool = TOOLS[toolId]
  if (!tool) return ''
  return CATEGORY_TO_TAG[tool.category] || ''
}

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
  'ai-file-reader': {
    name: 'AI 文件解读', icon: 'summary', category: 'AI办公助手', realized: true,
    desc: '上传任意文件（图片/PDF/Word/TXT），AI 自动识别内容并解读。支持多种文件格式，可同时处理多个文件。',
    inputTypes: ['file'], defaultInput: 'file', fileType: 'document',
    uploadTitle: '上传文件', uploadDesc: '支持图片、PDF、Word、TXT 格式',
    actionText: '开始解读', resultTitle: '解读结果', resultPlaceholder: '文件解读结果将在这里显示...',
    fileRule: {
      accept: '.jpg,.jpeg,.png,.pdf,.docx,.txt',
      maxCount: 10,
      maxTotalSize: 200 * 1024 * 1024,
      title: '上传文件',
      desc: '支持图片/PDF/Word/TXT · 最多 10 个文件 · 总大小 200MB',
      notice: 'PDF 支持说明：\n• ✅ 数字型 PDF 可直接解析\n• ✅ 扫描型 PDF（纸质拍照）可转图片识别\n• ⚠️ 若识别效果不佳，可先截图再上传图片',
      uploadType: 'aiFileReaderBatch',
      apiPath: '/api/ai-office/ai-file-reader/batch-upload'
    },
    validateRules: {
      file:   { token: true, file: { type: 'batch', min: 1, error: '请至少选择 1 个文件' } },
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
      image:  { unsupported: true, error: '该工具请输入文字' },
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
      image:  { unsupported: true, error: '该工具请输入文字' },
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
    desc: '设置番茄钟，专注工作 25 分钟休息 5 分钟。',
    inputTypes: [], defaultInput: 'text',
    actionText: '开始专注', resultTitle: '番茄钟', resultPlaceholder: '点击开始专注',
    validateRules: {
      text: { unsupported: true, error: '本工具不需要输入' },
      file: { unsupported: true, error: '本工具不需要输入' }
    }
  },
  'password-gen': {
    name: '密码生成', icon: 'password', category: '效率小工具', realized: false,
    desc: '一键生成高强度随机密码。',
    inputTypes: [], defaultInput: 'text',
    actionText: '生成密码', resultTitle: '密码', resultPlaceholder: '生成的密码将在这里显示...',
    validateRules: {
      text: { unsupported: true, error: '本工具不需要输入' },
      file: { unsupported: true, error: '本工具不需要输入' }
    }
  }
}

// 已实现的工具（首页/快速入口/分类页推荐用）
export const REALIZED_TOOLS = Object.entries(TOOLS)
  .filter(([, t]) => t.realized)
  .map(([id]) => id)