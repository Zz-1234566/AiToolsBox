// 顶层工具配置：集中定义所有工具，按模块（tool_type）分组
// tool_type 与数据库 sys_aitools_tool.tool_type 对应

// 工具分类（模块），code 即 tool_type
export const CATEGORIES = [
  { code: 'AI办公助手', tools: ['work-summary', 'doc-keypoint-extract', 'weekly-report', 'meeting-minutes', 'ocr-recognize'] },
  { code: '图片创意工具', tools: ['id-photo-bg-change', 'portrait-bg-replace', 'image-compress', 'qr-code-gen'] },
  { code: '效率小工具', tools: ['todo-list', 'pomodoro', 'password-gen'] }
]

// 每个工具的完整配置
// inputTypes: 该工具支持的输入方式数组（text 文字 / file 文件 / image 图片 / audio 音频）
// defaultInput: 默认选中的输入方式
export const TOOLS = {
  'work-summary': {
    name: '工作总结', icon: 'summary', category: 'AI办公助手', realized: true,
    desc: '输入零散的工作记录，AI 自动整理成结构化的工作内容总结。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'text',
    placeholder: '请输入今天的工作内容...',
    actionText: '开始总结', resultTitle: '总结结果', resultPlaceholder: '整理后的工作总结将在这里显示...'
  },
  'doc-keypoint-extract': {
    name: '文档重点提取', icon: 'summary', category: 'AI办公助手', realized: true,
    desc: '上传文档或粘贴内容，AI 自动提炼核心要点和待办事项。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'file', fileType: 'document',
    uploadTitle: '上传文档', uploadDesc: '支持 PDF、Word、TXT 格式',
    placeholder: '请输入或粘贴文档内容...',
    actionText: '开始提取', resultTitle: '提取结果', resultPlaceholder: '文档重点将在这里显示...'
  },
  'weekly-report': {
    name: '周报生成', icon: 'weekly', category: 'AI办公助手', realized: false,
    desc: '输入本周工作内容，一键生成结构化的工作周报。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'text',
    placeholder: '请输入本周完成的工作内容...',
    actionText: '生成周报', resultTitle: '周报内容', resultPlaceholder: '生成的周报将在这里显示...'
  },
  'meeting-minutes': {
    name: '会议纪要', icon: 'meeting', category: 'AI办公助手', realized: false,
    desc: '输入会议内容，AI 帮你整理会议核心结论和行动项。',
    inputTypes: ['text', 'file', 'audio'], defaultInput: 'text',
    placeholder: '请输入会议内容或语音转写文字...',
    actionText: '整理纪要', resultTitle: '会议纪要', resultPlaceholder: '整理后的会议纪要将在这里显示...'
  },
  'ocr-recognize': {
    name: '智能识别', icon: 'ocr', category: 'AI办公助手', realized: false,
    desc: '上传发票、名片、文档图片，自动识别文字内容。',
    inputTypes: ['file', 'image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传图片', uploadDesc: '支持 JPG、PNG、PDF 格式',
    actionText: '开始识别', resultTitle: '识别结果', resultPlaceholder: '识别结果将在这里显示...'
  },
  'id-photo-bg-change': {
    name: '证件照换背景色', icon: 'bg-color', category: '图片创意工具', realized: false,
    desc: '上传证件照，快速更换背景颜色。',
    inputTypes: ['image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传证件照', uploadDesc: '支持 JPG、PNG 格式',
    actionText: '开始处理', resultTitle: '处理结果', resultPlaceholder: '处理后的图片将在这里显示...'
  },
  'portrait-bg-replace': {
    name: '人像换背景图', icon: 'bg-image', category: '图片创意工具', realized: false,
    desc: '上传人像照片，AI 自动抠图并替换背景。',
    inputTypes: ['image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传人像照片', uploadDesc: '支持 JPG、PNG 格式',
    actionText: '开始抠图', resultTitle: '处理结果', resultPlaceholder: '处理后的图片将在这里显示...'
  },
  'image-compress': {
    name: '图片压缩', icon: 'compress', category: '图片创意工具', realized: false,
    desc: '上传图片，压缩图片大小方便分享。',
    inputTypes: ['image'], defaultInput: 'image', fileType: 'image',
    uploadTitle: '上传图片', uploadDesc: '支持 JPG、PNG 格式',
    actionText: '开始压缩', resultTitle: '压缩结果', resultPlaceholder: '压缩后的图片将在这里显示...'
  },
  'qr-code-gen': {
    name: '二维码生成', icon: 'qr', category: '图片创意工具', realized: false,
    desc: '输入网址或文本，生成可扫描的二维码。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入网址或文本内容...',
    actionText: '生成二维码', resultTitle: '二维码', resultPlaceholder: '生成的二维码将在这里显示...'
  },
  'todo-list': {
    name: '待办清单', icon: 'todo', category: '效率小工具', realized: false,
    desc: '输入待办事项，快速整理成清单。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入待办事项，用逗号分隔...',
    actionText: '生成清单', resultTitle: '待办清单', resultPlaceholder: '生成的待办清单将在这里显示...'
  },
  'pomodoro': {
    name: '番茄钟', icon: 'tomato', category: '效率小工具', realized: false,
    desc: '设置专注时长，开始番茄工作法。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入专注时长（分钟）...',
    actionText: '开始专注', resultTitle: '专注状态', resultPlaceholder: '专注状态将在这里显示...'
  },
  'password-gen': {
    name: '密码生成', icon: 'password', category: '效率小工具', realized: false,
    desc: '输入密码长度，生成随机强密码。',
    inputTypes: ['text'], defaultInput: 'text',
    placeholder: '请输入密码长度...',
    actionText: '生成密码', resultTitle: '生成密码', resultPlaceholder: '生成的密码将在这里显示...'
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
