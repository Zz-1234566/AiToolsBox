// 顶层工具配置：集中定义所有工具，按模块（tool_type）分组
// tool_type 与数据库 sys_aitools_tool.tool_type 对应

// 工具分类（模块），code 即 tool_type
// 工具已拆分为「解析层（无 AI，纯转换）」与「加工层（AI，只吃文本）」，
// 目的是让每个工具都能作为工作流的原子节点自由编排。
export const CATEGORIES = [
  {
    code: 'AI办公助手',
    tools: ['doc-to-text', 'audio-transcribe', 'meeting-minutes', 'doc-keypoint-extract', 'work-summary', 'weekly-report']
  }
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
// inputTypes: 该工具支持的输入方式数组（text 文字 / file 文件 / image 图片）
// defaultInput: 默认选中的输入方式
export const TOOLS = {
  'work-summary': {
    name: '工作总结', icon: 'summary', category: 'AI办公助手', realized: true,
    inputLabel: '工作内容',
    desc: '输入零散的工作记录，AI 自动整理成结构化的工作总结。文件请先用【文档提取】转为文字。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请粘贴或输入工作记录（可先用【文档提取】/【录音转写】准备好文字）...',
    actionText: '开始总结', resultTitle: '总结结果', resultPlaceholder: '整理后的工作总结将在这里显示...',
    validateRules: {
      text:   { token: true, file: { type: 'text', min: 1, error: '请输入内容' }, prompt: PROMPT_REQUIRED },
      file:   { unsupported: true, error: '该工具请使用文字输入（文件请先用【文档提取】转为文字）' },
      image:  { unsupported: true, error: '该工具请使用文字输入' },
    }
  },
  'doc-keypoint-extract': {
    name: '重点提取', icon: 'summary', category: 'AI办公助手', realized: true,
    inputLabel: '文本内容',
    desc: '输入已获取的文字内容，AI 逐条提炼核心要点、关键数据与结论建议。文件请先用【文档提取】转为文字。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请粘贴或输入文档内容（可先用【文档提取】/【录音转写】准备好文字）...',
    actionText: '开始提取', resultTitle: '提取结果', resultPlaceholder: '文档重点将在这里显示...',
    validateRules: {
      text:   { token: true, file: { type: 'text', min: 1, error: '请输入内容' }, prompt: PROMPT_REQUIRED },
      file:   { unsupported: true, error: '该工具请使用文字输入（文件请先用【文档提取】转为文字）' },
      image:  { unsupported: true, error: '该工具请使用文字输入' },
    }
  },
  'weekly-report': {
    name: '周报生成', icon: 'weekly', category: 'AI办公助手', realized: true,
    inputLabel: '本周工作内容',
    desc: '输入本周工作内容，一键生成结构化的工作周报。文件请先用【文档提取】转为文字。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请粘贴或输入本周完成的工作内容...',
    actionText: '生成周报', resultTitle: '周报内容', resultPlaceholder: '生成的周报将在这里显示...',
    validateRules: {
      text:   { token: true, file: { type: 'text', min: 1, error: '请输入内容' }, prompt: PROMPT_REQUIRED },
      file:   { unsupported: true, error: '该工具请使用文字输入（文件请先用【文档提取】转为文字）' },
      image:  { unsupported: true, error: '该工具请使用文字输入' },
      audio:  { unsupported: true, error: '该工具请使用文字输入（录音请先用【录音转写】）' }
    }
  },
  'meeting-minutes': {
    name: '会议纪要', icon: 'meeting', category: 'AI办公助手', realized: true,
    inputLabel: '会议内容',
    desc: '输入会议内容，AI 帮你整理会议核心结论和行动项。',
    // 已移除录音上传：转写能力解耦为独立节点/工具（audio-transcribe / doc-to-text）
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入或粘贴会议内容（可先用【录音转写】/【文档转文本】工具准备好文字）...',
    actionText: '整理纪要', resultTitle: '会议纪要', resultPlaceholder: '整理后的会议纪要将在这里显示...',
    validateRules: {
      text:   { file: { type: 'text', min: 1, error: '请输入内容' }, prompt: PROMPT_REQUIRED },
      file:   { unsupported: true, error: '该工具请使用文字输入（文件请先用【文档转文本】）' },
      image:  { unsupported: true, error: '该工具请使用文字输入' },
      audio:  { unsupported: true, error: '该工具请使用文字输入（录音请先用【录音转写】）' }
    }
  },
  'doc-to-text': {
    name: '文档提取', icon: 'summary', category: 'AI办公助手', realized: true,
    inputLabel: '文件内容',
    pureConvert: true,                        // 纯解析工具：不渲染提示词卡片、无提示词校验
    desc: '上传文档或图片提取纯文本：文本型 PDF/Word/TXT 走文字层解析，图片与扫描件自动走 OCR。',
    inputTypes: ['file'], defaultInput: 'file', fileType: 'file',
    uploadTitle: '上传文件', uploadDesc: '支持 PDF、Word、TXT、图片',
    actionText: '提取文本', resultTitle: '提取结果', resultPlaceholder: '提取的文本将在这里显示...',
    validateRules: {
      file:   { token: true, file: { type: 'single', min: 1, error: '请先上传文件' } },
      text:   { unsupported: true, error: '该工具请上传文件' },
      image:  { unsupported: true, error: '该工具请上传文件（图片请直接上传）' },
    }
  },
  'audio-transcribe': {
    name: '录音转写', icon: 'meeting', category: 'AI办公助手', realized: true,
    inputLabel: '录音内容',
    pureConvert: true,                        // 纯转换工具：提示词后端内置，不渲染提示词卡片
    desc: '上传录音（最多 10 个，总大小 200MB），AI 自动转写为文字，供会议纪要等工具使用。',
    inputTypes: ['file'], defaultInput: 'file', fileType: 'audio',
    // 多文件规则（BatchFilePicker 组件读取）
    fileRule: {
      accept: '.mp3,.wav,.m4a,.aac,.flac,.ogg,.amr',
      maxCount: 10,
      maxTotalSize: 200 * 1024 * 1024,         // 200MB
      title: '上传录音',
      desc: '支持 MP3、WAV、M4A、AAC、FLAC、OGG、AMR · 最多 10 个文件 · 总大小 200MB',
      notice: '单个录音建议不超过 500 秒（约 8 分钟）、50MB；超长录音请先裁剪',
      uploadType: 'audioBatch',                // 标识：调 audioBatchUpload
      apiPath: '/api/ai-office/meeting-minutes/batch-transcribe'
    },
    actionText: '开始转写', resultTitle: '转写结果', resultPlaceholder: '转写文字将在这里显示...',
    uploadTitle: '上传录音', uploadDesc: '支持 MP3、WAV、M4A 等格式 · 可多选',
    // 转写引擎选择（可选；后端 asr.tencent.enabled=false 时只有 minimax 可用）
    engineOptions: [
      { value: 'minimax', label: 'MiniMax', desc: '通用转写，稳定' },
      { value: 'tencent', label: '腾讯云', desc: '支持热词，术语更准' }
    ],
    defaultEngine: 'minimax',
    validateRules: {
      file:   { token: true, file: { type: 'batch', min: 1, error: '请先上传录音' } },
      text:   { unsupported: true, error: '该工具请上传录音' },
      image:  { unsupported: true, error: '该工具请上传录音' },
    }
  },
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
 * @param {String} inputType 输入方式（text / file / image）
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
