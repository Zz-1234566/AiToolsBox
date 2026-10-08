-- ============================================================
-- 工具重构：init.sql 的修正补丁
-- ============================================================
-- 用法：在 init.sql 之后执行本文件（或将本文件内容追加到 init.sql 末尾）。
--
-- 背景：工具已拆分为「解析层（无 AI）」与「加工层（只吃文本）」，
--       原 init.sql 中的 15 个工具 / 11 条提示词已不再适用。
--       本补丁把库状态对齐到重构后的 6 个工具 / 8 条提示词。
--
-- 说明：全部用逻辑删除（dr=1）而非物理删除，保留回滚能力。
-- ============================================================

-- ---------- 1. 逻辑删除已废弃的工具 ----------
-- ocr-recognize      能力已并入 doc-to-text（图片直送 OCR / 扫描件 PDF 渲染后送 OCR）
-- ai-file-reader     多模态解读，与「加工层只吃文本」的定位冲突
-- 以下 7 个一直未实现（无后端接口）
UPDATE `sys_aitools_tool` SET `dr` = 1
WHERE `tool_code` IN (
    'ocr-recognize', 'ai-file-reader',
    'id-photo-bg-change', 'portrait-bg-replace', 'image-compress', 'qr-code-gen',
    'todo-list', 'pomodoro', 'password-gen'
) AND `dr` = 0;

-- ---------- 2. 逻辑删除这些工具的提示词 ----------
UPDATE `sys_ai_prompt` SET `dr` = 1
WHERE `tool_code` IN (
    'ocr-recognize', 'ai-file-reader',
    'id-photo-bg-change', 'portrait-bg-replace', 'image-compress', 'qr-code-gen',
    'todo-list', 'pomodoro', 'password-gen'
) AND `dr` = 0;

-- ---------- 3. 解析层工具：补齐 input_type / output_type ----------
-- doc-to-text 支持图片与扫描件 PDF，input_type 必须是 file（工作流里 file = 通吃所有上游输出）
UPDATE `sys_aitools_tool` SET
    sort_no = 1, tool_name = '文档提取',
    input_type = 'file', output_type = 'text', component_type = 'office',
    description = '提取文档/图片中的文字，支持 PDF、Word、TXT、图片与扫描件（不调用 AI）'
WHERE `tool_code` = 'doc-to-text';

UPDATE `sys_aitools_tool` SET
    sort_no = 2, tool_name = '录音转写',
    input_type = 'audio', output_type = 'text', component_type = 'office',
    description = '把录音转写为文字（可选择 MiniMax 或腾讯云，不调用文本大模型）'
WHERE `tool_code` = 'audio-transcribe';

-- ---------- 4. 加工层工具：统一为「只吃文本」 ----------
UPDATE `sys_aitools_tool` SET
    sort_no = 3, input_type = 'text', output_type = 'text', component_type = 'office',
    description = '将会议内容整理为结构化会议纪要'
WHERE `tool_code` = 'meeting-minutes';

UPDATE `sys_aitools_tool` SET
    sort_no = 4, tool_name = '重点提取',
    input_type = 'text', output_type = 'text', component_type = 'office',
    description = '从已有文本中提炼核心要点、关键数据与结论建议'
WHERE `tool_code` = 'doc-keypoint-extract';

UPDATE `sys_aitools_tool` SET
    sort_no = 5, input_type = 'text', output_type = 'text', component_type = 'office',
    description = '把零散工作记录整理成结构化工作总结'
WHERE `tool_code` = 'work-summary';

UPDATE `sys_aitools_tool` SET
    sort_no = 6, input_type = 'text', output_type = 'text', component_type = 'office',
    description = '把一周工作记录整理成重点突出的工作周报'
WHERE `tool_code` = 'weekly-report';

-- ---------- 5. 加工层提示词：先删旧的 ----------
-- getDefaultByUse 按 id 升序取第一条，必须先删后插，否则旧的会继续被当默认
UPDATE `sys_ai_prompt` SET `dr` = 1
WHERE `tool_code` IN ('meeting-minutes', 'doc-keypoint-extract', 'work-summary', 'weekly-report')
  AND `dr` = 0;
