-- ============================================================
-- 工具入库：证件照换背景色（id-photo-bg-change）
-- ============================================================
-- 用法：在 init.sql / init_tools_refactor.sql 之后执行本文件，可重复执行（幂等）。
--
-- 背景：init_tools_refactor.sql 曾把 id-photo-bg-change 连同 7 个未实现的工具
--       一起置 dr=1（逻辑删除）。本次本地 ONNX 分割能力落地后，
--       把它重新上架，并把 input_type / output_type 补齐
--       （工作流节点类型匹配依赖这两列，见 NodeIoTypeEnum）。
--
-- 幂等策略：tool_code 上有唯一索引 uk_tool_code，故用
--       INSERT ... ON DUPLICATE KEY UPDATE，避免重复执行插入两行。
--
-- 注意：component_type = 'image'（图片创意工具分组），
--       与办公类工具的 'office' 区分开，前端按此分栏渲染。
-- ============================================================

INSERT INTO `sys_aitools_tool`
    (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`,
     `input_type`, `output_type`, `sort_no`, `status`, `dr`)
VALUES
    ('id-photo-bg-change', '图片创意工具', '证件照换背景色', 'image',
     '自动识别人物轮廓，去除背景并替换为红/蓝/白底', 'bg-color',
     'image', 'image', 7, 1, 0)
ON DUPLICATE KEY UPDATE
    `tool_type`     = VALUES(`tool_type`),
    `tool_name`     = VALUES(`tool_name`),
    `component_type`= VALUES(`component_type`),
    `description`   = VALUES(`description`),
    `icon`          = VALUES(`icon`),
    `input_type`    = VALUES(`input_type`),
    `output_type`   = VALUES(`output_type`),
    `sort_no`       = VALUES(`sort_no`),
    `status`        = VALUES(`status`),
    `dr`            = VALUES(`dr`);