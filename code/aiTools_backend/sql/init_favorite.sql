-- ============================================================================
-- 收藏功能 DDL（与 init.sql 隔离，可独立重复执行）
--
-- 说明：本表仅用于「我的收藏」页面的数据展示（工具收藏 + 提示词收藏），
--      不含复杂的收藏交互逻辑。用户、工具、提示词均复用现有表。
--
-- 设计：
--   target_type = tool   → target_id 存 sys_aitools_tool.tool_code
--   target_type = prompt → target_id 存 sys_ai_user_prompt.id（字符串形式）
--   唯一键 (user_id, target_type, target_id) 防止重复收藏
-- ============================================================================

USE ai_toolbox;

CREATE TABLE IF NOT EXISTS `sys_user_favorite` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户 ID',
  `target_type` VARCHAR(16)  NOT NULL COMMENT '收藏类型：tool 工具 / prompt 提示词',
  `target_id`   VARCHAR(64)  NOT NULL COMMENT '收藏目标 ID（tool: tool_code；prompt: prompt_id）',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `dr`          TINYINT      DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_user_type` (`user_id`, `target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

-- 验证
SELECT COUNT(*) AS favorite_count FROM `sys_user_favorite`;
