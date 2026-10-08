-- =============================================================================
-- sys_ai_prompt 去重（按 tool_code + prompt_use + prompt_type 保留最早一条）
-- 毕设 init.sql 历史遗留：多次跑会插出重复行（缺少唯一键约束）
-- 幂等：可以反复跑
-- =============================================================================

-- 1. 先看现状：每组有多少行
SELECT tool_code, prompt_type, prompt_use, COUNT(*) AS cnt
FROM sys_ai_prompt
GROUP BY tool_code, prompt_type, prompt_use
HAVING cnt > 1
ORDER BY tool_code, prompt_type, prompt_use;

-- 2. 删除"重复"行：每组保留 id 最小的一条，其余删掉
-- 警告：此操作会删除 prompt_content，运行前请确认第一条 SELECT 输出的就是你想要的
DELETE p1 FROM sys_ai_prompt p1
INNER JOIN sys_ai_prompt p2
  ON p1.tool_code = p2.tool_code
  AND p1.prompt_type = p2.prompt_type
  AND p1.prompt_use = p2.prompt_use
  AND p1.id > p2.id;

-- 3. 验证：每组应该正好 1 行
SELECT tool_code, prompt_type, prompt_use, COUNT(*) AS cnt
FROM sys_ai_prompt
GROUP BY tool_code, prompt_type, prompt_use
ORDER BY tool_code, prompt_type, prompt_use;
