-- ============================================================
-- 工具重构 · 第 6 步：删除冗余工具
-- ============================================================
-- 背景：工具拆分为「解析层（无 AI）」与「加工层（AI）」后，以下工具不再独立存在：
--   1. ocr-recognize      —— 能力已并入 doc-to-text（图片直送 OCR / 扫描件 PDF 渲染后送 OCR）
--   2. ai-file-reader     —— 多模态解读，与「加工层只吃文本」的定位冲突
--   3-9. 图片创意 / 效率小工具 —— 一直未实现（realized: false），无后端接口
--
-- 安全性：已核实 sys_workflow.nodes 中不存在对上述 tool_code 的引用
--        （工作流用 nodeRef 存 tool_code 字符串，与工具表自增 id 解耦）
--
-- 采用逻辑删除（dr=1）而非物理删除，保留回滚能力。
-- ============================================================

-- ---------- 1. 逻辑删除工具 ----------
UPDATE `sys_aitools_tool` SET `dr` = 1
WHERE `tool_code` IN (
    'ocr-recognize',        -- 能力并入 doc-to-text
    'ai-file-reader',       -- 多模态，与加工层定位冲突
    'id-photo-bg-change',   -- 未实现
    'portrait-bg-replace',  -- 未实现
    'image-compress',       -- 未实现
    'qr-code-gen',          -- 未实现
    'todo-list',            -- 未实现
    'pomodoro',             -- 未实现
    'password-gen'          -- 未实现
) AND `dr` = 0;

-- ---------- 2. 逻辑删除这些工具的提示词 ----------
UPDATE `sys_ai_prompt` SET `dr` = 1
WHERE `tool_code` IN (
    'ocr-recognize',
    'ai-file-reader',
    'id-photo-bg-change',
    'portrait-bg-replace',
    'image-compress',
    'qr-code-gen',
    'todo-list',
    'pomodoro',
    'password-gen'
) AND `dr` = 0;

-- ---------- 3. 保留工具的最终排序（解析层在前，加工层在后）----------
UPDATE `sys_aitools_tool` SET `sort_no` = 1 WHERE `tool_code` = 'doc-to-text';
UPDATE `sys_aitools_tool` SET `sort_no` = 2 WHERE `tool_code` = 'audio-transcribe';
UPDATE `sys_aitools_tool` SET `sort_no` = 3 WHERE `tool_code` = 'meeting-minutes';
UPDATE `sys_aitools_tool` SET `sort_no` = 4 WHERE `tool_code` = 'doc-keypoint-extract';
UPDATE `sys_aitools_tool` SET `sort_no` = 5 WHERE `tool_code` = 'work-summary';
UPDATE `sys_aitools_tool` SET `sort_no` = 6 WHERE `tool_code` = 'weekly-report';

-- ---------- 4. 统一 component_type（office）与 description ----------
UPDATE `sys_aitools_tool` SET
    component_type = 'office',
    description = '提取文档/图片中的文字，支持 PDF、Word、TXT、图片与扫描件（不调用 AI）'
WHERE `tool_code` = 'doc-to-text';

UPDATE `sys_aitools_tool` SET
    component_type = 'office',
    description = '把录音转写为文字（可选择 MiniMax 或腾讯云，不调用文本大模型）'
WHERE `tool_code` = 'audio-transcribe';

UPDATE `sys_aitools_tool` SET
    component_type = 'office',
    description = '将会议内容整理为结构化会议纪要'
WHERE `tool_code` = 'meeting-minutes';

UPDATE `sys_aitools_tool` SET
    component_type = 'office',
    description = '从已有文本中提炼核心要点、关键数据与结论建议'
WHERE `tool_code` = 'doc-keypoint-extract';

UPDATE `sys_aitools_tool` SET
    component_type = 'office',
    description = '把零散工作记录整理成结构化工作总结'
WHERE `tool_code` = 'work-summary';

UPDATE `sys_aitools_tool` SET
    component_type = 'office',
    description = '把一周工作记录整理成重点突出的工作周报'
WHERE `tool_code` = 'weekly-report';

-- ---------- 5. 清理测试期间建的临时工作流（名字以「测试流-」开头）----------
UPDATE `sys_workflow` SET `dr` = 1 WHERE `name` LIKE '测试流-%' AND `dr` = 0;
