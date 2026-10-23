-- =====================================================================
-- AI 工具种子：image-bg-remover（图片去/换背景，3 模式合一）
-- 适用版本：v1 (2026-10-12)
-- 说明：
--   1) 旧 tool_code=image-bg-changer 已废弃（端点 / 工具名合并入 image-bg-remover）
--   2) 新增 1 条 sys_aitools_tool 工具记录，tool_code=image-bg-remover
--   3) 3 模式合一（mode 决定行为）：
--        - transparent（默认）：单纯去背景，输出透明 PNG
--        - color               ：换底色（red/blue/white），输出 PNG
--        - image               ：换背景图（fg + 用户上传 bg），输出 JPG
--   4) 端点：POST /api/ai-office/image-bg-remover
--   5) 支持批量（1-10 张），HTTP 同步建任务 + 异步处理
--   6) 不涉及 API Key；纯本地 ONNX + 用户上传图片
-- =====================================================================

-- 1) 旧记录下线（保留行，便于按外键/历史回溯；如果要硬删可改为 DELETE）
UPDATE sys_aitools_tool
   SET status = 0
 WHERE tool_code = 'image-bg-changer';

-- 2) 新工具上线
-- ⚠️ input_type / output_type 必须一起写入：
--    这两列决定「工作流节点执行时选哪个能力」(NodeExecutor.executeOne 按 input_type 分派)
--    与「上下游能否连线」(NodeIoTypeEnum.canConnect 比对 output_type ⊆ 下游 input_type)。
--    缺这两列 → 新装库该工具 input_type 为 NULL → 放进工作流直接抛「不支持的节点输入类型」。
INSERT INTO sys_aitools_tool
    (tool_code, tool_type, tool_name, component_type, description, icon, input_type, output_type, sort_no, status, dr)
VALUES
    ('image-bg-remover', '图片创意工具', '图片背景去除', 'image',
     '一键去背景/换底色/换背景图，本地 ONNX 推理，无需联网', '',
     'image', 'image', 8, 1, 0)
ON DUPLICATE KEY UPDATE
    tool_name      = VALUES(tool_name),
    tool_type      = VALUES(tool_type),
    component_type = VALUES(component_type),
    description    = VALUES(description),
    input_type     = VALUES(input_type),
    output_type    = VALUES(output_type),
    sort_no        = VALUES(sort_no),
    status         = VALUES(status);

-- 3) 校验
SELECT tool_code, tool_name, status, sort_no
  FROM sys_aitools_tool
 WHERE tool_code IN ('image-bg-remover', 'image-bg-changer')
 ORDER BY sort_no;
