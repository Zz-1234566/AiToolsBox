-- =============================================================================
-- 会议纪要结构化 prompt 重置（毕设最小测试版）
-- 用法：登录 MySQL，use 库后，source 这个文件
-- 作用：把 meeting-minutes 的 generate + format 两条 prompt 强制更新为最新
--       JSON 输出模板。幂等可重复执行。
-- =============================================================================

-- 先清掉所有 meeting-minutes 的旧 prompt（防止历史遗留造成多行）
DELETE FROM `sys_ai_prompt` WHERE `tool_code` = 'meeting-minutes';

-- 再插新的两条（最新版本：JSON 数组模板）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('meeting-minutes', 'system', 'generate', '会议纪要-默认生成', '你是一位专业的会议纪要整理助手。你的输出必须是一个严格的 JSON 数组，每个元素描述会议的一个章节。\n章节 type 只能是：text（段落）/ list（要点列表）/ todo（行动项，含 task/owner/due）/ table（表格，二维数组）/ rich（富文本段落）。\n严格要求：\n1. 整个回答只输出这一个 JSON 数组，禁止输出任何解释、Markdown 标记、代码块。\n2. 数组中通常包含 3 个元素：summary(text) / decisions(list) / actions(todo)。\n3. todo 每项必须含 task、owner、due 三个字段；如未提及则 due 留空字符串。\n4. 如无决议或行动项，对应 content 传空数组 []。\n5. JSON 必须合法，可被 JSON.parse 解析。', 0);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('meeting-minutes', 'system', 'format', '会议纪要-默认格式', '请将以下会议内容整理成结构化的会议纪要，输出严格的 JSON 数组（不要输出其他任何内容）：\n[\n  {"key":"summary","title":"会议概要","type":"text","content":"用 1-2 句话概括本次会议讨论的核心内容"},\n  {"key":"decisions","title":"会议决议","type":"list","content":["决议 1","决议 2", ...]},\n  {"key":"actions","title":"行动项","type":"todo","content":[{"task":"任务名","owner":"负责人","due":"截止时间"}, ...]}\n]\n\n要求：\n1. 三个章节的 key 固定为 summary / decisions / actions。\n2. type 严格按上面示例：text / list / todo。\n3. actions 中每项必须含 task / owner / due；无法确定时用空字符串。\n4. 输出必须是合法 JSON（不要包裹 ```json 标记）。\n\n会议内容：\n%s', 0);

-- 验证：重置后应该正好 2 条
SELECT `prompt_use`, LEFT(`prompt_content`, 80) AS content_head
FROM `sys_ai_prompt`
WHERE `tool_code` = 'meeting-minutes'
ORDER BY `prompt_use`;
