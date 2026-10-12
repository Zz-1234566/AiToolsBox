-- =====================================================================
-- AI 工具种子：image-bg-changer（图片换背景图）
-- 适用版本：v1 (2026-10-12)
-- 说明：
--   1) 新增 1 条 sys_aitools_tool 工具记录，tool_code=image-bg-changer
--   2) 复用 SegmentationService.changeBackground（fg 去背景 + 叠到用户上传背景图 / 纯色）
--   3) 支持批量（1-10 张），HTTP 同步建任务 + 异步处理
--   4) 输出 JPG（fg+bg 路径）或 PNG（纯色路径，与 id-photo-bg-change 一致）
--   5) 不涉及 API Key；纯本地 ONNX + 用户上传图片
-- =====================================================================

INSERT INTO sys_aitools_tool
    (tool_code, tool_type, tool_name, component_type, description, icon, sort_no, status, dr)
VALUES
    ('image-bg-changer', '图片创意工具', '图片换背景图', 'image',
     '上传图片和背景图，自动合成前景+背景；或选择纯色背景（red/blue/white）', '', 9, 1, 0)
ON DUPLICATE KEY UPDATE
    tool_name      = VALUES(tool_name),
    tool_type      = VALUES(tool_type),
    component_type = VALUES(component_type),
    description    = VALUES(description),
    sort_no        = VALUES(sort_no),
    status         = VALUES(status);
