-- ============================================================
-- AI Toolbox 数据库初始化脚本
-- 首次部署执行：创建数据库、全部业务表与初始化数据
-- ============================================================

-- -------------------------------------------
-- 1. 创建数据库
--
-- ⚠️ 执行前必须先选定目标库，本脚本**不固定库名**：
--    mysql --default-character-set=utf8mb4 -uroot -p -D <目标库> < init.sql
--
--    下面两行默认执行：**只在明确要写入 ai_toolbox 时才放开**。
--    若只想在临时库验证（如 -D ai_toolbox_verify），请保持注释状态，
--    否则脚本会无视命令行指定的库、强行切到 ai_toolbox 写进去。
--
--    真实事故记录：本脚本早期版本在此处硬编码 USE ai_toolbox，
--    导致「在临时库验证 init.sql」的命令直接写进了现网库，
--    误插 16 条提示词 + 8 条工具并改坏 1 行既有数据。故改为不写死库名。
-- -------------------------------------------
-- CREATE DATABASE IF NOT EXISTS `ai_toolbox` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- USE `ai_toolbox`;

-- -------------------------------------------
-- 2. 用户表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account` VARCHAR(32) NOT NULL COMMENT '账号（系统生成，唯一）',
  `username` VARCHAR(32) NOT NULL COMMENT '用户名',
  `email` VARCHAR(64) NOT NULL COMMENT '邮箱',
  `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt加密）',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `role` VARCHAR(16) NOT NULL DEFAULT 'user' COMMENT '用户角色：admin管理员/user普通用户',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`),
  UNIQUE KEY `uk_email_dr` (`email`, `dr`),
  UNIQUE KEY `uk_username_dr` (`username`, `dr`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 升级记录（历史，新装环境无需执行）：
-- 1) 新增 email 字段（邮箱注册/找回密码功能）
--    ALTER TABLE `sys_user` ADD COLUMN IF NOT EXISTS `email` VARCHAR(64) NOT NULL COMMENT '邮箱' AFTER `username`;
-- 2) 唯一索引由单列 uk_email 调整为复合 uk_email_dr(email, dr)，支持软删后邮箱复用
--    ALTER TABLE `sys_user` DROP INDEX `uk_email`, ADD UNIQUE KEY `uk_email_dr` (`email`, `dr`);
-- 3) 加 username 复合唯一索引 uk_username_dr，支持软删后用户名复用
--    ALTER TABLE `sys_user` ADD UNIQUE KEY IF NOT EXISTS `uk_username_dr` (`username`, `dr`);
--    -- 注意：升级前需确认 dr=0 的 username 字段无重复；如有重复需先 UPDATE 修复，否则 ALTER 会失败

-- -------------------------------------------
-- 3. 工具表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_aitools_tool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tool_code` VARCHAR(32) NOT NULL COMMENT '工具编码（唯一）',
  `tool_type` VARCHAR(32) DEFAULT NULL COMMENT '所属模块：AI办公助手/图片创意工具/效率小工具',
  `tool_name` VARCHAR(64) NOT NULL COMMENT '工具名称',
  `component_type` VARCHAR(32) DEFAULT NULL COMMENT '组件类型：office等',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '工具描述',
  `icon` VARCHAR(255) DEFAULT NULL COMMENT '图标',
  `sort_no` INT DEFAULT 0 COMMENT '排序号',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tool_code` (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具表';

-- -------------------------------------------
-- 4. AI 模型配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_model` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `model_code` VARCHAR(64) NOT NULL COMMENT '模型编码',
  `model_name` VARCHAR(64) DEFAULT NULL COMMENT '模型名称',
  `api_url` VARCHAR(255) DEFAULT NULL COMMENT 'API地址',
  `api_key` VARCHAR(255) DEFAULT NULL COMMENT 'API密钥',
  `api_model` VARCHAR(64) DEFAULT NULL COMMENT 'API模型名',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认：1是 0否',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置表';

-- -------------------------------------------
-- 5. AI 工具使用历史主表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_aitools_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `tool_id` BIGINT DEFAULT NULL COMMENT '工具ID',
  `model_id` BIGINT DEFAULT NULL COMMENT '模型ID',
  `ai_code` VARCHAR(64) DEFAULT NULL COMMENT 'AI会话编码',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0处理中 1成功 2失败',
  `duration` INT DEFAULT 0 COMMENT '耗时（毫秒）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_ai_code` (`ai_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI工具使用历史主表';

-- 升级记录（已建库环境）：补 ai_code 索引
-- P0-D3 兼容老版本 MySQL（8.0.29 之前不支持 ADD INDEX IF NOT EXISTS）：
-- 用 information_schema + 动态 SQL 兜底
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history' AND INDEX_NAME = 'idx_ai_code');
SET @sql := IF(@idx_exists = 0, 'ALTER TABLE `sys_aitools_history` ADD INDEX `idx_ai_code` (`ai_code`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------
-- 6. AI 工具使用历史明细表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_aitools_history_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `history_id` BIGINT NOT NULL COMMENT '历史记录ID',
  `input_content` TEXT COMMENT '输入内容',
  `output_content` TEXT COMMENT '输出内容',
  `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息（失败时）',
  `prompt_format` TEXT COMMENT '用户当时在格式提示词 textarea 里的内容（resolve 前）',
  `prompt_generate` TEXT COMMENT '用户当时在生成提示词 textarea 里的内容（resolve 前）',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_history_id` (`history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI工具使用历史明细表';

-- 升级记录（历史，新装环境无需执行）：
-- ALTER TABLE `sys_aitools_history_detail` ADD COLUMN `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息（失败时）' AFTER `output_content`;
-- ALTER TABLE `sys_aitools_history_detail` ADD COLUMN `prompt_format` TEXT COMMENT '用户当时在格式提示词 textarea 里的内容（resolve 前）' AFTER `error_msg`;
-- ALTER TABLE `sys_aitools_history_detail` ADD COLUMN `prompt_generate` TEXT COMMENT '用户当时在生成提示词 textarea 里的内容（resolve 前）' AFTER `prompt_format`;

-- -------------------------------------------
-- 7. AI 工具使用历史文件表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_aitools_history_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `history_id` BIGINT NOT NULL COMMENT '历史记录ID',
  `file_id` VARCHAR(64) DEFAULT NULL COMMENT '文件ID',
  `file_name` VARCHAR(255) DEFAULT NULL COMMENT '文件名',
  `file_url` VARCHAR(255) DEFAULT NULL COMMENT '文件URL',
  `file_type` VARCHAR(32) DEFAULT NULL COMMENT '文件类型',
  `role` TINYINT DEFAULT 0 COMMENT '文件角色：1输入 2输出',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_history_id` (`history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI工具使用历史文件表';

-- -------------------------------------------
-- 8. 用户自定义提示词表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_user_prompt` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `tool_code` VARCHAR(32) NOT NULL COMMENT '所属工具编码（绑定具体工具）',
  `prompt_text` TEXT NOT NULL COMMENT '提示词内容',
  `prompt_use` VARCHAR(16) DEFAULT NULL COMMENT '提示词用途：format格式/generate生成内容',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_tool` (`user_id`, `tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自定义提示词表';

-- 升级记录（历史，新装环境无需执行）：
-- 1) 新增 prompt_name 字段（用户自定义命名，用于前端列表展示）
--    ALTER TABLE sys_ai_user_prompt ADD COLUMN prompt_name VARCHAR(64) DEFAULT NULL COMMENT '提示词名称（用户自定义命名，用于列表展示）' AFTER prompt_text;
-- 2) 唯一性约束：同一用户同一工具下 prompt_name 不可重复（NULL 不参与）
--    ALTER TABLE sys_ai_user_prompt ADD UNIQUE KEY uk_user_tool_name (user_id, tool_code, prompt_name);
--    -- 配套：service.delete 软删时把 prompt_name 改为 {原名}__del_{id}，避免名字占位阻塞复用
--    -- 升级前需清理现有重名数据，否则 ALTER 会失败

-- -------------------------------------------
-- 9. 系统提示词库表（按工具+类型存提示词）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_prompt` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tool_code` VARCHAR(32) NOT NULL COMMENT '工具编码',
  `prompt_type` VARCHAR(16) NOT NULL COMMENT '提示词类型：system/user',
  `prompt_use` VARCHAR(16) DEFAULT NULL COMMENT '提示词用途：format格式/generate生成内容',
  `prompt_name` VARCHAR(64) DEFAULT NULL COMMENT '提示词名称（如默认/简洁版）',
  `prompt_content` TEXT NOT NULL COMMENT '提示词内容（user类型含%s占位符）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_tool_type` (`tool_code`, `prompt_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统提示词库';

-- -------------------------------------------
-- 10. 初始化数据：工具（work-summary 工作总结）
-- -------------------------------------------
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('work-summary', 'AI办公助手', '工作总结', 'office', '将零散的工作记录整理成结构化总结', '', 1, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

-- -------------------------------------------
-- 11. 初始化数据：系统提示词（work-summary）
-- -------------------------------------------

-- work-summary 系统提示词（generate 生成内容：人设）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '工作总结-默认生成',
       `prompt_content` = '你是一位严谨的工作整理助手，擅长把零散的工作记录整理成正式、规范、结构清晰的工作总结。\n严格要求：\n1. 只输出工作总结内容，禁止输出任何解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，语气规范，像职场日报/周报。\n4. 每个分点必须单独占一行，段落之间空一行，保证可读性。\n5. 直接给出总结结果，不要重复用户输入的内容。',
       `dr` = 0
 WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'work-summary', 'system', 'generate', '工作总结-默认生成',
       '你是一位严谨的工作整理助手，擅长把零散的工作记录整理成正式、规范、结构清晰的工作总结。\n严格要求：\n1. 只输出工作总结内容，禁止输出任何解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，语气规范，像职场日报/周报。\n4. 每个分点必须单独占一行，段落之间空一行，保证可读性。\n5. 直接给出总结结果，不要重复用户输入的内容。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');

-- work-summary 系统提示词（format 格式：四段式模板，%s 为工作内容占位）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '工作总结-默认格式',
       `prompt_content` = '请将以下工作记录整理成正式的工作总结，按以下四个部分输出：\n一、已完成事项\n二、进行中事项\n三、遇到的问题\n四、下一步计划\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n工作记录：\n%s',
       `dr` = 0
 WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'work-summary', 'system', 'format', '工作总结-默认格式',
       '请将以下工作记录整理成正式的工作总结，按以下四个部分输出：\n一、已完成事项\n二、进行中事项\n三、遇到的问题\n四、下一步计划\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n工作记录：\n%s',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'format');

-- 旧数据清理：user 类型模板已迁移至用户自定义表（sys_ai_user_prompt），从系统表移除
DELETE FROM `sys_ai_prompt` WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'user';

-- -------------------------------------------
-- 12. 初始化数据：工具（doc-keypoint-extract 文档重点提取）
-- -------------------------------------------
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('doc-keypoint-extract', 'AI办公助手', '文档重点提取', 'office', '上传文档自动提炼重点内容', '', 2, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

-- -------------------------------------------
-- 12.1 初始化数据：其余工具入库（共 10 个，sort_no 3-12 唯一连续）
-- 说明：bank-receipt-recognize / invoice-recognize 原仅作为「系统提示词」存在，
--       但其对应工具从未在工具表登记，属孤儿提示词，已按现网口径整体移除。
-- -------------------------------------------

-- AI办公助手（已有序号1-2，从3开始）
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('weekly-report', 'AI办公助手', '周报生成', 'office', '输入工作内容生成周报', '', 3, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('meeting-minutes', 'AI办公助手', '会议纪要', 'office', '整理会议核心结论', '', 4, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('ocr-recognize', 'AI办公助手', '智能识别', 'office', '发票、名片、文字识别', '', 5, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

-- 图片创意工具（sort_no 6-9）
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('id-photo-bg-change', '图片创意工具', '证件照换背景色', 'image', '红蓝白底自由切换', '', 6, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('portrait-bg-replace', '图片创意工具', '人像换背景图', 'image', 'AI 抠图替换背景', '', 7, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('image-compress', '图片创意工具', '图片压缩', 'image', '压缩图片大小', '', 8, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('qr-code-gen', '图片创意工具', '二维码生成', 'image', '生成网址/名片二维码', '', 9, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

-- 效率小工具（sort_no 10-12）
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('todo-list', '效率小工具', '待办清单', 'efficiency', '记录每日待办事项', '', 10, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('pomodoro', '效率小工具', '番茄钟', 'efficiency', '专注工作学习', '', 11, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('password-gen', '效率小工具', '密码生成', 'efficiency', '生成安全随机密码', '', 12, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`), `sort_no` = VALUES(`sort_no`);

-- -------------------------------------------
-- 13. 初始化数据：系统提示词（doc-keypoint-extract）
-- -------------------------------------------

-- doc-keypoint-extract 系统提示词（generate 生成内容：人设）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '文档重点提取-默认生成',
       `prompt_content` = '你是一位专业的文档分析助手，擅长从文档中准确提炼核心重点。\n严格要求：\n1. 只输出文档的重点内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用流畅的中文书面表达，条理清晰。\n4. 每个要点单独占一行，段落之间空一行。\n5. 直接给出提炼结果，不要重复文档原文。',
       `dr` = 0
 WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'doc-keypoint-extract', 'system', 'generate', '文档重点提取-默认生成',
       '你是一位专业的文档分析助手，擅长从文档中准确提炼核心重点。\n严格要求：\n1. 只输出文档的重点内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用流畅的中文书面表达，条理清晰。\n4. 每个要点单独占一行，段落之间空一行。\n5. 直接给出提炼结果，不要重复文档原文。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');

-- doc-keypoint-extract 系统提示词（format 格式：结构化模板，%s 为文档内容占位）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '文档重点提取-默认格式',
       `prompt_content` = '请从以下文档中提炼核心重点，按以下结构输出：\n一、文档主题\n二、核心要点（3-8条）\n三、关键数据/结论\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n文档内容：\n%s',
       `dr` = 0
 WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'doc-keypoint-extract', 'system', 'format', '文档重点提取-默认格式',
       '请从以下文档中提炼核心重点，按以下结构输出：\n一、文档主题\n二、核心要点（3-8条）\n三、关键数据/结论\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n文档内容：\n%s',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'format');

-- -------------------------------------------
-- 14. 初始化数据：系统提示词（weekly-report / meeting-minutes）
-- -------------------------------------------

-- weekly-report 系统提示词（generate 生成内容：人设）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '周报生成-默认生成',
       `prompt_content` = '你是一位专业的职场周报撰写助手，擅长将一周的工作内容整理成结构清晰、重点突出的工作周报。\n严格要求：\n1. 只输出周报内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，语气规范，像职场周报。\n4. 每个分点单独占一行，段落之间空一行。\n5. 直接给出周报结果，不要重复用户输入的内容。',
       `dr` = 0
 WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'weekly-report', 'system', 'generate', '周报生成-默认生成',
       '你是一位专业的职场周报撰写助手，擅长将一周的工作内容整理成结构清晰、重点突出的工作周报。\n严格要求：\n1. 只输出周报内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，语气规范，像职场周报。\n4. 每个分点单独占一行，段落之间空一行。\n5. 直接给出周报结果，不要重复用户输入的内容。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');

-- weekly-report 系统提示词（format 格式：四段式模板，%s 为本周工作内容占位）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '周报生成-默认格式',
       `prompt_content` = '请将以下本周工作内容整理成正式的工作周报，按以下结构输出：\n一、本周完成事项\n二、进行中事项\n三、遇到的问题\n四、下周计划\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n本周工作内容：\n%s',
       `dr` = 0
 WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'weekly-report', 'system', 'format', '周报生成-默认格式',
       '请将以下本周工作内容整理成正式的工作周报，按以下结构输出：\n一、本周完成事项\n二、进行中事项\n三、遇到的问题\n四、下周计划\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n本周工作内容：\n%s',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'format');

-- meeting-minutes 系统提示词（generate 生成内容：人设）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '会议纪要-默认生成',
       `prompt_content` = '你是一位专业的会议纪要整理助手，擅长从会议内容或语音转写文字中提炼核心结论和行动项。\n严格要求：\n1. 只输出会议纪要内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，条理清晰。\n4. 每个要点单独占一行，段落之间空一行。\n5. 直接给出纪要结果，不要重复用户输入的内容。',
       `dr` = 0
 WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'meeting-minutes', 'system', 'generate', '会议纪要-默认生成',
       '你是一位专业的会议纪要整理助手，擅长从会议内容或语音转写文字中提炼核心结论和行动项。\n严格要求：\n1. 只输出会议纪要内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，条理清晰。\n4. 每个要点单独占一行，段落之间空一行。\n5. 直接给出纪要结果，不要重复用户输入的内容。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');

-- meeting-minutes 系统提示词（format 格式：四段式模板，%s 为会议内容占位）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '会议纪要-默认格式',
       `prompt_content` = '请将以下会议内容整理成正式的会议纪要，按以下结构输出：\n一、会议主题\n二、讨论要点\n三、会议决议\n四、行动项（责任人+截止时间）\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n会议内容：\n%s',
       `dr` = 0
 WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'meeting-minutes', 'system', 'format', '会议纪要-默认格式',
       '请将以下会议内容整理成正式的会议纪要，按以下结构输出：\n一、会议主题\n二、讨论要点\n三、会议决议\n四、行动项（责任人+截止时间）\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n会议内容：\n%s',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'format');



-- -------------------------------------------
-- 升级SQL（已建库环境执行）：工具按模块归类 + 提示词按工具隔离
-- -------------------------------------------
-- 1. 工具表加 tool_type 字段
-- P0-D3 兼容老版本 MySQL：见上方 helper 模板
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_tool' AND COLUMN_NAME = 'tool_type');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `sys_aitools_tool` ADD COLUMN `tool_type` VARCHAR(32) DEFAULT NULL COMMENT ''所属模块：AI办公助手/图片创意工具/效率小工具'' AFTER `tool_code`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- 2. 现有工具补 tool_type
UPDATE `sys_aitools_tool` SET `tool_type` = 'AI办公助手' WHERE `tool_code` IN ('work-summary', 'ai-summary');
-- 3. ai-summary 改名 doc-keypoint-extract（工具表 + 系统提示词表）
UPDATE `sys_aitools_tool` SET `tool_code` = 'doc-keypoint-extract', `tool_name` = '文档重点提取' WHERE `tool_code` = 'ai-summary';
UPDATE `sys_ai_prompt` SET `tool_code` = 'doc-keypoint-extract' WHERE `tool_code` = 'ai-summary';
-- 4. 用户提示词表加 tool_code
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_user_prompt' AND COLUMN_NAME = 'tool_code');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `sys_ai_user_prompt` ADD COLUMN `tool_code` VARCHAR(32) NOT NULL COMMENT ''所属工具编码（绑定具体工具）'' AFTER `user_id`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4.1 配套索引 idx_user_tool
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_user_prompt' AND INDEX_NAME = 'idx_user_tool');
SET @sql := IF(@idx_exists = 0, 'ALTER TABLE `sys_ai_user_prompt` ADD INDEX `idx_user_tool` (`user_id`, `tool_code`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 补全其余工具入库（已建库环境）
-- 注意：12.1 块已用 sort_no 3-12 一次性写入新表；升级场景只补 tool_type / sort_no，不再重复插整行（避免 sort_no 冲突）
-- 旧库若已存在 row，本脚本升级后用 UPDATE 统一对齐：
-- 说明：bank-receipt-recognize / invoice-recognize 的孤儿提示词已移除（见 12.1 节说明），
--       此处亦不再对其工具行做 input_type / output_type 对齐。
UPDATE `sys_aitools_tool` SET `sort_no` = 3  WHERE `tool_code` = 'weekly-report';
UPDATE `sys_aitools_tool` SET `sort_no` = 4  WHERE `tool_code` = 'meeting-minutes';
UPDATE `sys_aitools_tool` SET `sort_no` = 5  WHERE `tool_code` = 'ocr-recognize';
UPDATE `sys_aitools_tool` SET `sort_no` = 6  WHERE `tool_code` = 'id-photo-bg-change';
UPDATE `sys_aitools_tool` SET `sort_no` = 7  WHERE `tool_code` = 'portrait-bg-replace';
UPDATE `sys_aitools_tool` SET `sort_no` = 8  WHERE `tool_code` = 'image-compress';
UPDATE `sys_aitools_tool` SET `sort_no` = 9  WHERE `tool_code` = 'qr-code-gen';
UPDATE `sys_aitools_tool` SET `sort_no` = 10 WHERE `tool_code` = 'todo-list';
UPDATE `sys_aitools_tool` SET `sort_no` = 11 WHERE `tool_code` = 'pomodoro';
UPDATE `sys_aitools_tool` SET `sort_no` = 12 WHERE `tool_code` = 'password-gen';


-- -------------------------------------------
-- 22. 初始化数据：批量任务表（多文件上传 B2 方案用）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS sys_batch_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  batch_id VARCHAR(64) NOT NULL COMMENT '对外 batchId（UUID）',
  user_id BIGINT NOT NULL COMMENT '所属用户 ID',
  tool_code VARCHAR(32) NOT NULL COMMENT '工具编码（如 doc-keypoint-extract）',
  file_count INT NOT NULL COMMENT '文件总数',
  success_count INT DEFAULT 0 COMMENT '成功数',
  fail_count INT DEFAULT 0 COMMENT '失败数',
  processed_index INT DEFAULT 0 COMMENT '已处理文件数（成功+失败，用于前端轮询 since 增量）',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0=PENDING 1=RUNNING 2=COMPLETED 3=PARTIAL 4=FAILED',
  result_summary MEDIUMTEXT COMMENT '汇总结果（所有文件 AI 输出拼接，JSON 数组）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  finished_at DATETIME DEFAULT NULL COMMENT '完成时间',
  dr TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_id (batch_id),
  KEY idx_user_id (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量任务表';

-- P0-D3 兼容老版本：动态 SQL 加列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_batch_task' AND COLUMN_NAME = 'processed_index');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE sys_batch_task ADD COLUMN processed_index INT DEFAULT 0 COMMENT ''已处理文件数（成功+失败，用于前端轮询 since 增量）'' AFTER fail_count', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_batch_task (
                                              id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                              batch_id VARCHAR(64) NOT NULL COMMENT '对外 batchId（UUID）',
                                              user_id BIGINT NOT NULL COMMENT '所属用户 ID',
                                              tool_code VARCHAR(32) NOT NULL COMMENT '工具编码',
                                              file_count INT NOT NULL COMMENT '文件总数',
                                              success_count INT DEFAULT 0 COMMENT '成功数',
                                              fail_count INT DEFAULT 0 COMMENT '失败数',
                                              processed_index INT DEFAULT 0 COMMENT '已处理文件数',
                                              status TINYINT NOT NULL DEFAULT 0 COMMENT '0=PENDING 1=RUNNING 2=COMPLETED 3=PARTIAL 4=FAILED',
                                              result_summary MEDIUMTEXT COMMENT '汇总结果',
                                              create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                              update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              finished_at DATETIME DEFAULT NULL,
                                              dr TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
                                              PRIMARY KEY (id),
                                              UNIQUE KEY uk_batch_id (batch_id),
                                              KEY idx_user_id (user_id),
                                              KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量任务表';

-- P0-D3 兼容老版本：动态 SQL 加列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_user_prompt' AND COLUMN_NAME = 'prompt_name');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE `sys_ai_user_prompt` ADD COLUMN `prompt_name` VARCHAR(64) DEFAULT NULL COMMENT ''提示词名称（用户自定义命名，用于列表展示）'' AFTER `prompt_text`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 升级记录：老数据暂不强制命名（应用层兜底"未命名"）

-- P0-D3 兼容老版本：动态 SQL 加唯一索引
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_user_prompt' AND INDEX_NAME = 'uk_user_tool_name');
SET @sql := IF(@idx_exists = 0, 'ALTER TABLE `sys_ai_user_prompt` ADD UNIQUE KEY `uk_user_tool_name` (`user_id`, `tool_code`, `prompt_name`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 注：此处原有一行 USE ai_toolbox; 已移除，理由同文件开头第 1 节。
--     库名一律由命令行 -D 指定，避免脚本擅自切换目标库。


-- ============================================================
-- [历史说明] 本节补丁原分散在 init_tools_refactor.sql / migrate_prompt_*.sql /
--   migrate_remove_tools.sql 五个文件中，现已全部并入本文件。
--   克隆本仓库后执行本文件即可，无需再执行其它 SQL 脚本。
-- ============================================================


-- ============================================================
-- 23. 工具重构收尾：合并工具/提示词补丁
--
-- 背景：上方第 10-22 节写入的是「工具重构前」状态（15 个工具 / 11 条提示词）。
--      工具随后被拆分为「解析层（无 AI）+ 加工层（只吃文本）」，最终形态是
--      6 个 dr=0 的工具（doc-to-text / audio-transcribe / meeting-minutes /
--      doc-keypoint-extract / work-summary / weekly-report）+ 对应的 8 条提示词。
--      补丁原分散在 init_tools_refactor.sql、migrate_prompt_*.sql、
--      migrate_remove_tools.sql 三个文件里；本文件是唯一建库入口，故在此合并收口。
--      合并来源（原脚本名，均已并入本文件，仓库中不再单独保留）：
--        - init_tools_refactor.sql                 （工具/提示词字段修正与提示词重设计）
--        - migrate_prompt_meeting_minutes.sql      （会议纪要提示词）
--        - migrate_prompt_processing_tools.sql     （其余三个加工工具提示词）
--        - migrate_prompt_weekly_strict.sql        （周报 format 章节约束强化）
--        - migrate_remove_tools.sql                （逻辑删除冗余工具 + 最终排序）
--      提示词正文过长，此处只保留「工具归属 + 迁移后结果」，
--      具体 JSON 模板仍以 sys_ai_prompt 表内容为准（迁移脚本已在上方写入）。
-- ============================================================

-- ---------- 23.1 工具表补 input_type / output_type（工作流节点类型匹配用） ----------
-- 取值：none / text / audio / document / image / file（逗号分隔的扁平集合）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_tool' AND COLUMN_NAME = 'input_type');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_aitools_tool` ADD COLUMN `input_type` VARCHAR(64) DEFAULT NULL COMMENT ''输入类型（逗号分隔）：none/text/audio/document/image/file'' AFTER `icon`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_tool' AND COLUMN_NAME = 'output_type');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_aitools_tool` ADD COLUMN `output_type` VARCHAR(64) DEFAULT NULL COMMENT ''输出类型（逗号分隔）：none/text/image'' AFTER `input_type`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 23.2 回填既有工具的类型 ----------
UPDATE `sys_aitools_tool` SET `input_type` = 'text,file', `output_type` = 'text' WHERE `tool_code` = 'work-summary';
UPDATE `sys_aitools_tool` SET `input_type` = 'file',      `output_type` = 'text' WHERE `tool_code` = 'doc-keypoint-extract';
UPDATE `sys_aitools_tool` SET `input_type` = 'text,file', `output_type` = 'text' WHERE `tool_code` = 'weekly-report';
-- meeting-minutes：移除录音入口后为纯文本输入
UPDATE `sys_aitools_tool` SET `input_type` = 'text',      `output_type` = 'text' WHERE `tool_code` = 'meeting-minutes';
UPDATE `sys_aitools_tool` SET `input_type` = 'image',     `output_type` = 'text' WHERE `tool_code` = 'ocr-recognize';
UPDATE `sys_aitools_tool` SET `input_type` = 'image',     `output_type` = 'text' WHERE `tool_code` = 'bank-receipt-recognize';
UPDATE `sys_aitools_tool` SET `input_type` = 'image',     `output_type` = 'text' WHERE `tool_code` = 'invoice-recognize';
UPDATE `sys_aitools_tool` SET `input_type` = 'image',     `output_type` = 'image' WHERE `tool_code` = 'id-photo-bg-change';
UPDATE `sys_aitools_tool` SET `input_type` = 'image',     `output_type` = 'image' WHERE `tool_code` = 'portrait-bg-replace';
UPDATE `sys_aitools_tool` SET `input_type` = 'image',     `output_type` = 'image' WHERE `tool_code` = 'image-compress';
UPDATE `sys_aitools_tool` SET `input_type` = 'text',      `output_type` = 'image' WHERE `tool_code` = 'qr-code-gen';
UPDATE `sys_aitools_tool` SET `input_type` = 'text',      `output_type` = 'text' WHERE `tool_code` = 'todo-list';
UPDATE `sys_aitools_tool` SET `input_type` = 'none',      `output_type` = 'none' WHERE `tool_code` = 'pomodoro';
UPDATE `sys_aitools_tool` SET `input_type` = 'none',      `output_type` = 'text' WHERE `tool_code` = 'password-gen';

-- ---------- 23.3 节点用工具入库（audio-transcribe / doc-to-text / ai-file-reader） ----------
-- 说明：ai-file-reader 最终被 migrate_remove_tools.sql 逻辑删除（见 23.5），
--      这里保留 INSERT 是为了与原脚本顺序一致；23.5 会把它置 dr=1。
INSERT INTO `sys_aitools_tool`
    (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `input_type`, `output_type`, `sort_no`, `status`, `dr`)
VALUES
    ('audio-transcribe', 'AI办公助手', '录音转写', 'office', '上传录音自动转写为文字，供会议纪要等工具使用', '', 'audio', 'text', 14, 1, 0)
ON DUPLICATE KEY UPDATE
    `tool_name` = VALUES(`tool_name`), `input_type` = VALUES(`input_type`),
    `output_type` = VALUES(`output_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool`
    (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `input_type`, `output_type`, `sort_no`, `status`, `dr`)
VALUES
    ('doc-to-text', 'AI办公助手', '文档转文本', 'office', '上传文档（PDF/Word/TXT）提取纯文本，供会议纪要等工具使用', '', 'document', 'text', 15, 1, 0)
ON DUPLICATE KEY UPDATE
    `tool_name` = VALUES(`tool_name`), `input_type` = VALUES(`input_type`),
    `output_type` = VALUES(`output_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool`
    (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `input_type`, `output_type`, `sort_no`, `status`, `dr`)
VALUES
    ('ai-file-reader', 'AI办公助手', 'AI 文件解读', 'office', '上传任意文件（图片/PDF/Word/TXT），AI 自动识别内容并解读', '', 'image,document,text', 'text', 13, 1, 0)
ON DUPLICATE KEY UPDATE
    `tool_name` = VALUES(`tool_name`), `input_type` = VALUES(`input_type`),
    `output_type` = VALUES(`output_type`);

-- ---------- 23.4 ai-summary 改名 doc-keypoint-extract（工具表 + 提示词表） ----------
UPDATE `sys_aitools_tool` SET `tool_code` = 'doc-keypoint-extract', `tool_name` = '文档重点提取' WHERE `tool_code` = 'ai-summary';
UPDATE `sys_ai_prompt` SET `tool_code` = 'doc-keypoint-extract' WHERE `tool_code` = 'ai-summary';

-- ---------- 23.5 删除冗余工具（逻辑删除，保留回滚能力） ----------
--   ocr-recognize      能力并入 doc-to-text
--   ai-file-reader     多模态，与加工层定位冲突
--   图片创意 / 效率小工具  一直未实现（realized: false），无后端接口
UPDATE `sys_aitools_tool` SET `dr` = 1
WHERE `tool_code` IN (
    'ocr-recognize', 'ai-file-reader', 'id-photo-bg-change',
    'portrait-bg-replace', 'image-compress', 'qr-code-gen',
    'todo-list', 'pomodoro', 'password-gen'
) AND `dr` = 0;

UPDATE `sys_ai_prompt` SET `dr` = 1
WHERE `tool_code` IN (
    'ocr-recognize', 'ai-file-reader', 'id-photo-bg-change',
    'portrait-bg-replace', 'image-compress', 'qr-code-gen',
    'todo-list', 'pomodoro', 'password-gen'
) AND `dr` = 0;

-- ---------- 23.6 最终排序：解析层在前，加工层在后 ----------
UPDATE `sys_aitools_tool` SET `sort_no` = 1 WHERE `tool_code` = 'doc-to-text';
UPDATE `sys_aitools_tool` SET `sort_no` = 2 WHERE `tool_code` = 'audio-transcribe';
UPDATE `sys_aitools_tool` SET `sort_no` = 3 WHERE `tool_code` = 'meeting-minutes';
UPDATE `sys_aitools_tool` SET `sort_no` = 4 WHERE `tool_code` = 'doc-keypoint-extract';
UPDATE `sys_aitools_tool` SET `sort_no` = 5 WHERE `tool_code` = 'work-summary';
UPDATE `sys_aitools_tool` SET `sort_no` = 6 WHERE `tool_code` = 'weekly-report';

-- ---------- 23.7 统一 component_type 与 description ----------
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


-- ============================================================
-- 24. 收藏功能表 sys_user_favorite
-- 合并来源：init_favorite.sql（已并入本文件，逐字照搬）
-- 说明：本表仅用于「我的收藏」页面的数据展示（工具收藏 + 提示词收藏），
--      用户、工具、提示词均复用前面已建的表。
--      target_type = tool   → target_id 存 sys_aitools_tool.tool_code
--      target_type = prompt → target_id 存 sys_ai_user_prompt.id（字符串形式）
-- ============================================================
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


-- ============================================================
-- 25. 工作流模块表 sys_workflow / sys_workflow_run
-- 合并来源：init_workflow.sql 第 1、2 节（已并入本文件，逐字照搬）
-- 设计要点：
--   - 节点间传递为「数组」语义：N 个输入 → N 个输出，逐项独立处理，不混在一起
--   - 层次约束：≤5 层（WORKFLOW_MAX_DEPTH），节点数量不限
--   - 幂等：IF NOT EXISTS，可反复执行
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_workflow` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workflow_id` VARCHAR(64)  NOT NULL COMMENT '对外 workflowId（UUID）',
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户 ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '工作流名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `nodes`       MEDIUMTEXT   COMMENT '节点列表 JSON：[{nodeId,nodeRef,name,deps,params}]（角色由 deps 推导，不存 nodeType）',
  `node_count`  INT          DEFAULT 0 COMMENT '节点数（冗余，列表展示用）',
  `max_depth`   TINYINT      DEFAULT 0 COMMENT '保存时计算的层数（冗余，校验 ≤5）',
  `status`      TINYINT      DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dr`          TINYINT      DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_id` (`workflow_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_time` (`user_id`, `dr`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流表';

CREATE TABLE IF NOT EXISTS `sys_workflow_run` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id`         VARCHAR(64)  NOT NULL COMMENT '对外 runId（UUID）',
  `workflow_id`    VARCHAR(64)  NOT NULL COMMENT '所属工作流 workflowId',
  `user_id`        BIGINT       NOT NULL COMMENT '所属用户 ID',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待运行 1运行中 2成功 3部分失败 4失败 5已取消',
  `node_results`   MEDIUMTEXT   COMMENT '每节点结果 JSON：{nodeId:{status,inputs[],outputs[],costMs,errorMsg}}',
  `input_snapshot` MEDIUMTEXT   COMMENT '运行时输入快照 JSON：{nodeId:{text|files[]}}',
  `input_file_names` MEDIUMTEXT COMMENT '源节点输入文件的原始文件名 JSON：{nodeId:[name]}；input_snapshot 只存 URL',
  `success_count`  INT          DEFAULT 0 COMMENT '成功节点数',
  `fail_count`     INT          DEFAULT 0 COMMENT '失败节点数',
  `max_depth`      TINYINT      DEFAULT 0 COMMENT '本次实际层数',
  `duration`       INT          DEFAULT 0 COMMENT '总耗时（毫秒）',
  `error_msg`      VARCHAR(500) DEFAULT NULL COMMENT '整体错误信息',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finished_at`    DATETIME     DEFAULT NULL COMMENT '完成时间',
  `dr`             TINYINT      DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  KEY `idx_workflow_id` (`workflow_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流运行历史表';


-- ============================================================
-- 26. 全局工具产物表 sys_tool_output
-- 合并来源：init_tool_output.sql（已并入本文件，逐字照搬）
-- 定位：文本类产物直接返回前端即可；图片/视频/音频/文件类产物统一存本表，
--      避免响应体膨胀。文件实体在 COS，表里只存 URL + 元信息。
--      适用场景：
--        来源                run_id / node_id / node_name
--        工作流节点产出      有值（有值时可按 run_id + node_id 定位）
--        独立调用工具产出    NULL（无工作流上下文）
--      output_type：1文本 2图片 3视频 4音频 5文件
--      node_results 配套增加 outputIds 字段（数组，与 outputs 同序）。
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_tool_output` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`        varchar(64)           DEFAULT NULL COMMENT 'sys_workflow_run.run_id；仅工作流来源有值，独立调用工具时为 NULL',
    `node_id`       varchar(32)           DEFAULT NULL COMMENT 'nodeId，如 n1；仅工作流来源有值，独立调用工具时为 NULL',
    `node_name`     varchar(64)           DEFAULT NULL COMMENT '节点名称快照；仅工作流来源有值，独立调用工具时为 NULL',
    `tool_code`     varchar(64)           DEFAULT NULL COMMENT '产出该产物的工具编码',
    `file_index`    int          NOT NULL DEFAULT 0 COMMENT '对应第几个输入（多文件并行时区分）',

    `output_type`   tinyint      NOT NULL DEFAULT 1 COMMENT '产物类型：1文本 2图片 3视频 4音频 5文件',
    `text_content`  mediumtext            DEFAULT NULL COMMENT '文本内容（output_type=1 时有值）',
    `file_name`     varchar(255)          DEFAULT NULL COMMENT '文件名（output_type!=1 时有值）',
    `file_url`      varchar(500)          DEFAULT NULL COMMENT '文件访问地址（COS 对象地址或签名 URL）',
    `cos_key`       varchar(255)          DEFAULT NULL COMMENT 'COS 对象 key（签名 URL 过期后可用此 key 重新签发）',
    `file_size`     bigint                DEFAULT NULL COMMENT '文件字节数',
    `mime_type`     varchar(128)          DEFAULT NULL COMMENT 'MIME，如 image/png',
    `width`         int                   DEFAULT NULL COMMENT '图片/视频宽度（像素）',
    `height`        int                   DEFAULT NULL COMMENT '图片/视频高度（像素）',
    `duration_ms`   int                   DEFAULT NULL COMMENT '音视频时长（毫秒）',

    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `dr`            tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    PRIMARY KEY (`id`),
    KEY `idx_run` (`run_id`),
    KEY `idx_run_node` (`run_id`, `node_id`),
    KEY `idx_run_node_idx` (`run_id`, `node_id`, `file_index`),
    KEY `idx_type` (`output_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局工具产物表（任何工具产出的文件类产物都存此表；仅工作流来源 run_id/node_id/node_name 有值，独立调用工具时为 NULL）';


-- ============================================================
-- 27. 工具运行历史补「来源」字段（工作流调用可反查）
-- 合并来源：migrate_history_source.sql（已并入本文件，逐字照搬）
-- 背景：工具拆分为解析层/加工层后，一个工作流会串联多个工具节点，
--      原表只记录「谁调了哪个工具」，无法反查是哪个工作流的第几个节点产生的，
--      导致工具使用历史与工作流运行历史对不上账。
--      source_type：1手工 2工作流；工作流来源同时记录 run_id / node_id / node_name。
-- ============================================================
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history' AND COLUMN_NAME = 'source_type');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_aitools_history` ADD COLUMN `source_type` TINYINT DEFAULT 1 COMMENT ''来源：1手工调用 2工作流调用'' AFTER `ai_code`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history' AND COLUMN_NAME = 'run_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_aitools_history` ADD COLUMN `run_id` VARCHAR(64) DEFAULT NULL COMMENT ''所属工作流运行 runId；仅工作流来源有值'' AFTER `source_type`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history' AND COLUMN_NAME = 'node_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_aitools_history` ADD COLUMN `node_id` VARCHAR(32) DEFAULT NULL COMMENT ''工作流节点 nodeId；仅工作流来源有值'' AFTER `run_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history' AND COLUMN_NAME = 'node_name');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `sys_aitools_history` ADD COLUMN `node_name` VARCHAR(64) DEFAULT NULL COMMENT ''节点名称快照；仅工作流来源有值'' AFTER `node_id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 28. 加工层工具提示词正文（重构后最终版）
--
-- 背景：工具拆分为「解析层（无 AI）+ 加工层（只吃文本）」后，4 个加工层工具的
--      提示词全部重新设计过，最终正文原先只散落在 migrate_prompt_*.sql /
--      refresh_meeting_minutes_prompts.sql 里。新建库只跑 init.sql 会拿到
--      重构前的旧正文，与现网口径不一致。本节把现网 dr=0 的 8 条正文
--      （4 工具 x format/generate）固化下来，内容逐字取自现网，未做改写。
--      合并来源：migrate_prompt_processing_tools.sql（重点提取/工作总结/周报）
--                migrate_prompt_meeting_minutes.sql + refresh_meeting_minutes_prompts.sql（会议纪要）
--                migrate_prompt_weekly_strict.sql（周报 format 章节硬性约束）
--
-- ⚠️ 幂等实现（与第 10-14 节写法不同，这里刻意不用 ON DUPLICATE KEY）：
--    sys_ai_prompt 只有 PRIMARY KEY(id) 与非唯一索引 idx_tool_type，
--    (tool_code, prompt_type, prompt_use) 上没有唯一约束（已用 SHOW INDEX 核实）。
--    因此 ON DUPLICATE KEY UPDATE 永远不会命中，重复执行 init.sql 会不断追加
--    重复行 —— 这正是 dedupe_sys_ai_prompt.sql 当年被禁用的原因。
--    本节改用「先 UPDATE（取该组最小 id 原地覆盖并复位 dr=0），未命中才 INSERT」：
--    反复执行 init.sql 行数不变，正文始终是最终版。
-- ============================================================

-- doc-keypoint-extract · format（重点提取-默认格式）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '重点提取-默认格式',
       `prompt_content` = '请将输入内容提炼为结构清晰的重点清单，严格按以下格式输出：\n\n# 重点提取\n\n## 核心要点\n按重要程度排列，每条以「1. 2. 3.」编号，每条独立成段。\n\n## 关键数据\n列出文中的关键数字、日期、金额、指标等硬信息，以 Markdown 表格呈现，列为：项目 | 数值\n无数据时该章节写「无」。\n\n## 结论与建议\n列出文中的明确结论、作者主张或可执行的建议，每条以「- 」开头。\n无此类内容时该章节写「无」。\n\n## 存疑与待确认\n列出信息不完整、自相矛盾或需进一步确认之处，以「- 」开头。\n无此类内容时该章节写「无」。\n\n输出要求：\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字或代码块包裹（禁止 ```markdown）。\n2. 必须使用上述二级标题，顺序不得调整，不得增删章节。\n3. 核心要点按重要性排序，最多 10 条；每条不超过 3 行。\n4. 全部使用简体中文。',
       `dr` = 0
 WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'doc-keypoint-extract', 'system', 'format', '重点提取-默认格式',
       '请将输入内容提炼为结构清晰的重点清单，严格按以下格式输出：\n\n# 重点提取\n\n## 核心要点\n按重要程度排列，每条以「1. 2. 3.」编号，每条独立成段。\n\n## 关键数据\n列出文中的关键数字、日期、金额、指标等硬信息，以 Markdown 表格呈现，列为：项目 | 数值\n无数据时该章节写「无」。\n\n## 结论与建议\n列出文中的明确结论、作者主张或可执行的建议，每条以「- 」开头。\n无此类内容时该章节写「无」。\n\n## 存疑与待确认\n列出信息不完整、自相矛盾或需进一步确认之处，以「- 」开头。\n无此类内容时该章节写「无」。\n\n输出要求：\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字或代码块包裹（禁止 ```markdown）。\n2. 必须使用上述二级标题，顺序不得调整，不得增删章节。\n3. 核心要点按重要性排序，最多 10 条；每条不超过 3 行。\n4. 全部使用简体中文。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'format');

-- doc-keypoint-extract · generate（重点提取-默认生成）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '重点提取-默认生成',
       `prompt_content` = '你是一位专业的文档分析助手，擅长从长文本中准确提炼核心重点。\n\n输入是一份文档内容，可能是报告、方案、合同、会议记录或学习笔记。请按以下步骤处理：\n1. 通读全文，先判断文档类型与主题，明确「这份文档到底在讲什么」。\n2. 识别文档的核心主张、关键论据与最终结论。\n3. 抽取所有硬信息：数字、比例、日期、金额、指标、专有名词，逐字核对不得改写。\n4. 找出信息不完整、自相矛盾或表述含糊之处，列入存疑。\n5. 按重要性排序，核心要点控制在 10 条以内。\n\n注意事项：\n- 严格基于原文提炼，禁止添加原文没有的结论、数据或建议。\n- 原文过长时优先保留结论性、决策性、风险性的内容，舍弃铺垫与例证。\n- 若输入内容明显不构成文档（如歌词、代码、乱码），在「核心要点」中如实说明「输入内容不具备可提炼的文档结构」，其余章节留空。',
       `dr` = 0
 WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'doc-keypoint-extract', 'system', 'generate', '重点提取-默认生成',
       '你是一位专业的文档分析助手，擅长从长文本中准确提炼核心重点。\n\n输入是一份文档内容，可能是报告、方案、合同、会议记录或学习笔记。请按以下步骤处理：\n1. 通读全文，先判断文档类型与主题，明确「这份文档到底在讲什么」。\n2. 识别文档的核心主张、关键论据与最终结论。\n3. 抽取所有硬信息：数字、比例、日期、金额、指标、专有名词，逐字核对不得改写。\n4. 找出信息不完整、自相矛盾或表述含糊之处，列入存疑。\n5. 按重要性排序，核心要点控制在 10 条以内。\n\n注意事项：\n- 严格基于原文提炼，禁止添加原文没有的结论、数据或建议。\n- 原文过长时优先保留结论性、决策性、风险性的内容，舍弃铺垫与例证。\n- 若输入内容明显不构成文档（如歌词、代码、乱码），在「核心要点」中如实说明「输入内容不具备可提炼的文档结构」，其余章节留空。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'doc-keypoint-extract' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');

-- meeting-minutes · format（会议纪要-默认格式）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '会议纪要-默认格式',
       `prompt_content` = '请将输入的会议内容整理为结构清晰的 Markdown 会议纪要，严格按以下结构输出：\n\n# 会议纪要\n\n## 会议概要\n用 2-3 句话概括本次会议的核心议题与总体结论。\n\n## 关键讨论\n按议题分点列出讨论要点，每点一行，以「- 」开头。\n\n## 会议决议\n列出会议达成的明确结论，以「1. 2. 3.」编号。\n\n## 行动项\n用 Markdown 表格呈现，列为：任务 | 负责人 | 截止时间\n\n## 风险与待办\n列出遗留问题、风险点与后续跟进事项；无内容时写「无」。\n\n输出要求：\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字或代码块包裹（禁止 ```markdown）。\n2. 必须使用上述二级标题，顺序不得调整，不得增删章节。\n3. 行动项必须用 Markdown 表格，无行动项时该章节写「无」。\n4. 全部使用简体中文，不输出英文标题。',
       `dr` = 0
 WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'meeting-minutes', 'system', 'format', '会议纪要-默认格式',
       '请将输入的会议内容整理为结构清晰的 Markdown 会议纪要，严格按以下结构输出：\n\n# 会议纪要\n\n## 会议概要\n用 2-3 句话概括本次会议的核心议题与总体结论。\n\n## 关键讨论\n按议题分点列出讨论要点，每点一行，以「- 」开头。\n\n## 会议决议\n列出会议达成的明确结论，以「1. 2. 3.」编号。\n\n## 行动项\n用 Markdown 表格呈现，列为：任务 | 负责人 | 截止时间\n\n## 风险与待办\n列出遗留问题、风险点与后续跟进事项；无内容时写「无」。\n\n输出要求：\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字或代码块包裹（禁止 ```markdown）。\n2. 必须使用上述二级标题，顺序不得调整，不得增删章节。\n3. 行动项必须用 Markdown 表格，无行动项时该章节写「无」。\n4. 全部使用简体中文，不输出英文标题。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'format');

-- meeting-minutes · generate（会议纪要-默认生成）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '会议纪要-默认生成',
       `prompt_content` = '你是一位专业的会议纪要整理助手。\n\n你的输入是一段会议内容，可能是语音转写文本、讨论记录或潦草笔记，存在口语化、重复、错别字与断句错误。\n\n请按以下步骤处理：\n1. 先通读全文，识别本次会议的真实议题（可能只有一个，也可能有多个）。\n2. 剔除寒暄、闲聊、无效重复与明显的转写噪声。\n3. 提炼「会议概要」：2-3 句话讲清讨论了什么、结论是什么。\n4. 归纳「关键讨论」：按议题分组，保留有信息量的观点与论据。\n5. 抽取「会议决议」：只写明确达成一致的内容，讨论中未决的不要写进决议。\n6. 抽取「行动项」：每项必须能落到「谁 / 做什么 / 何时之前完成」三元组；原文未指定负责人或时间时，写「待定」，不要编造。\n7. 归纳「风险与待办」：未决问题、潜在风险、需后续跟进的事项。\n\n注意事项：\n- 严格基于原文，不要添加原文没有的信息、结论或数据。\n- 涉及人名、数字、日期时逐字核对，转写文本常有同音错字。\n- 若输入明显不是会议内容（如歌词、纯代码、无意义文本），在「会议概要」中如实说明「输入内容不构成有效会议记录」，其余章节留空。',
       `dr` = 0
 WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'meeting-minutes', 'system', 'generate', '会议纪要-默认生成',
       '你是一位专业的会议纪要整理助手。\n\n你的输入是一段会议内容，可能是语音转写文本、讨论记录或潦草笔记，存在口语化、重复、错别字与断句错误。\n\n请按以下步骤处理：\n1. 先通读全文，识别本次会议的真实议题（可能只有一个，也可能有多个）。\n2. 剔除寒暄、闲聊、无效重复与明显的转写噪声。\n3. 提炼「会议概要」：2-3 句话讲清讨论了什么、结论是什么。\n4. 归纳「关键讨论」：按议题分组，保留有信息量的观点与论据。\n5. 抽取「会议决议」：只写明确达成一致的内容，讨论中未决的不要写进决议。\n6. 抽取「行动项」：每项必须能落到「谁 / 做什么 / 何时之前完成」三元组；原文未指定负责人或时间时，写「待定」，不要编造。\n7. 归纳「风险与待办」：未决问题、潜在风险、需后续跟进的事项。\n\n注意事项：\n- 严格基于原文，不要添加原文没有的信息、结论或数据。\n- 涉及人名、数字、日期时逐字核对，转写文本常有同音错字。\n- 若输入明显不是会议内容（如歌词、纯代码、无意义文本），在「会议概要」中如实说明「输入内容不构成有效会议记录」，其余章节留空。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'meeting-minutes' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');

-- weekly-report · format（周报生成-默认格式）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '周报生成-默认格式',
       `prompt_content` = '请将输入的工作记录整理为一份本周工作周报。\n\n## 一、必须输出的章节（硬性清单）\n\n输出的 Markdown 中**必须依次出现以下 5 个二级标题，一个都不能少、不能改名、不能换顺序**：\n\n1. `# 工作周报`            —— 一级标题\n2. `## 本周概要`          —— 用 2-3 句话概括本周主要工作方向与整体进展\n3. `## 本周进展`          —— 按项目或模块分点，每条以「- 」开头，写明「任务 + 当前状态 + 进展/结果」；状态只用「已完成」「进行中」「受阻」三种表述之一\n4. `## 关键成果`          —— 用 Markdown 表格呈现，列为：项目 | 成果 | 量化指标；无量化数据时本章写「无」\n5. `## 问题与风险`        —— 每条以「- 」开头；无此类内容时本章写「无」\n6. `## 下周计划`          —— 每条以「- 」开头\n\n## 二、各章内容要求\n\n- 本周进展最多 10 条；下周计划最多 5 条。\n- 状态判定必须准确：未完成的事不得写成「已完成」。\n\n## 三、输出前自检（必须执行）\n\n在给出最终答案前，逐条确认上述 6 个标题是否都已出现。**若有缺失，先补齐再输出。**\n确认齐全后才输出最终内容。\n\n## 四、禁止事项\n\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字、致歉或总结语。\n2. 禁止使用 ```markdown 或 ``` 代码块包裹。\n3. 禁止增删章节、禁止改名、禁止调整顺序。\n4. 全部使用简体中文，措辞书面化，不使用口语表达。',
       `dr` = 0
 WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'weekly-report', 'system', 'format', '周报生成-默认格式',
       '请将输入的工作记录整理为一份本周工作周报。\n\n## 一、必须输出的章节（硬性清单）\n\n输出的 Markdown 中**必须依次出现以下 5 个二级标题，一个都不能少、不能改名、不能换顺序**：\n\n1. `# 工作周报`            —— 一级标题\n2. `## 本周概要`          —— 用 2-3 句话概括本周主要工作方向与整体进展\n3. `## 本周进展`          —— 按项目或模块分点，每条以「- 」开头，写明「任务 + 当前状态 + 进展/结果」；状态只用「已完成」「进行中」「受阻」三种表述之一\n4. `## 关键成果`          —— 用 Markdown 表格呈现，列为：项目 | 成果 | 量化指标；无量化数据时本章写「无」\n5. `## 问题与风险`        —— 每条以「- 」开头；无此类内容时本章写「无」\n6. `## 下周计划`          —— 每条以「- 」开头\n\n## 二、各章内容要求\n\n- 本周进展最多 10 条；下周计划最多 5 条。\n- 状态判定必须准确：未完成的事不得写成「已完成」。\n\n## 三、输出前自检（必须执行）\n\n在给出最终答案前，逐条确认上述 6 个标题是否都已出现。**若有缺失，先补齐再输出。**\n确认齐全后才输出最终内容。\n\n## 四、禁止事项\n\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字、致歉或总结语。\n2. 禁止使用 ```markdown 或 ``` 代码块包裹。\n3. 禁止增删章节、禁止改名、禁止调整顺序。\n4. 全部使用简体中文，措辞书面化，不使用口语表达。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'format');

-- weekly-report · generate（周报生成-默认生成）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '周报生成-默认生成',
       `prompt_content` = '你是一位专业的职场周报撰写助手，擅长把一周的零散工作记录整理成重点突出、便于上级快速了解进展的周报。\n\n输入是本周的工作记录，可能是日报、任务清单、会议纪要或随手记。请按以下步骤处理：\n1. 明确本周主线：本周围绕哪几个项目或方向展开。\n2. 归并同类项：把同一事项的多条零散记录合并，避免重复罗列。\n3. 判断每条事项的状态：已完成 / 进行中 / 受阻，状态必须准确，不得把未完成的事写成完成。\n4. 提炼关键成果：优先选取有交付物、有数据支撑的产出。\n5. 梳理问题与风险：未完成的事、被阻塞的事、需要他人配合的事，都要如实列出。\n6. 推导下周计划：基于当前进展与未完成项，给出合理的下一步动作。\n\n注意事项：\n- 严格基于原始记录，禁止虚构未发生的工作、进度或数据。\n- 涉及数字、日期、人名时逐字核对，不得推测。\n- 措辞需体现汇报意识：简洁、客观、结论先行，但不得夸大或美化。\n- 若输入内容不具备周报属性（如歌词、代码、闲聊），在「本周概要」中如实说明「输入内容不构成有效工作记录」，其余章节留空。',
       `dr` = 0
 WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'weekly-report', 'system', 'generate', '周报生成-默认生成',
       '你是一位专业的职场周报撰写助手，擅长把一周的零散工作记录整理成重点突出、便于上级快速了解进展的周报。\n\n输入是本周的工作记录，可能是日报、任务清单、会议纪要或随手记。请按以下步骤处理：\n1. 明确本周主线：本周围绕哪几个项目或方向展开。\n2. 归并同类项：把同一事项的多条零散记录合并，避免重复罗列。\n3. 判断每条事项的状态：已完成 / 进行中 / 受阻，状态必须准确，不得把未完成的事写成完成。\n4. 提炼关键成果：优先选取有交付物、有数据支撑的产出。\n5. 梳理问题与风险：未完成的事、被阻塞的事、需要他人配合的事，都要如实列出。\n6. 推导下周计划：基于当前进展与未完成项，给出合理的下一步动作。\n\n注意事项：\n- 严格基于原始记录，禁止虚构未发生的工作、进度或数据。\n- 涉及数字、日期、人名时逐字核对，不得推测。\n- 措辞需体现汇报意识：简洁、客观、结论先行，但不得夸大或美化。\n- 若输入内容不具备周报属性（如歌词、代码、闲聊），在「本周概要」中如实说明「输入内容不构成有效工作记录」，其余章节留空。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'weekly-report' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');

-- work-summary · format（工作总结-默认格式）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '工作总结-默认格式',
       `prompt_content` = '请将输入的工作记录整理为一份正式、结构清晰的工作总结，严格按以下格式输出：\n\n# 工作总结\n\n## 工作概述\n用 3-5 句话说明本次工作的整体情况、主要方向与取得的整体成果。\n\n## 完成事项\n按业务模块或时间顺序分点列出，每条以「1. 2. 3.」编号，写明「做了什么 + 达到什么效果」。\n\n## 工作亮点\n列出有价值的成果、突破或超出预期的部分，每条以「- 」开头，并尽量给出量化结果。\n\n## 存在问题\n列出未完成事项、遇到的阻碍与需要协调的问题，每条以「- 」开头。\n无此类内容时该章节写「无」。\n\n## 下一步计划\n列出后续计划与建议动作，每条以「- 」开头，写明「做什么 + 预期目标」。\n\n输出要求：\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字或代码块包裹（禁止 ```markdown）。\n2. 必须使用上述二级标题，顺序不得调整，不得增删章节。\n3. 完成事项最多 10 条，亮点最多 5 条。\n4. 全部使用简体中文，不使用「总的来说」「希望领导批准」等套话。',
       `dr` = 0
 WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'format'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'format') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'work-summary', 'system', 'format', '工作总结-默认格式',
       '请将输入的工作记录整理为一份正式、结构清晰的工作总结，严格按以下格式输出：\n\n# 工作总结\n\n## 工作概述\n用 3-5 句话说明本次工作的整体情况、主要方向与取得的整体成果。\n\n## 完成事项\n按业务模块或时间顺序分点列出，每条以「1. 2. 3.」编号，写明「做了什么 + 达到什么效果」。\n\n## 工作亮点\n列出有价值的成果、突破或超出预期的部分，每条以「- 」开头，并尽量给出量化结果。\n\n## 存在问题\n列出未完成事项、遇到的阻碍与需要协调的问题，每条以「- 」开头。\n无此类内容时该章节写「无」。\n\n## 下一步计划\n列出后续计划与建议动作，每条以「- 」开头，写明「做什么 + 预期目标」。\n\n输出要求：\n1. 只输出 Markdown 正文，不得有任何开场白、说明文字或代码块包裹（禁止 ```markdown）。\n2. 必须使用上述二级标题，顺序不得调整，不得增删章节。\n3. 完成事项最多 10 条，亮点最多 5 条。\n4. 全部使用简体中文，不使用「总的来说」「希望领导批准」等套话。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'format');

-- work-summary · generate（工作总结-默认生成）
UPDATE `sys_ai_prompt`
   SET `prompt_name` = '工作总结-默认生成',
       `prompt_content` = '你是一位严谨的工作整理助手，擅长把零散的工作记录整理成正式、规范、结构清晰的工作总结。\n\n输入是个人或团队一段时间内的工作记录，可能是日报、周报、会议记录或流水账。请按以下步骤处理：\n1. 通读全部记录，识别这段时间的工作主线（在做哪几件事）。\n2. 归并同类项：把零散的、重复的记录合并成一条完整事项。\n3. 按「做了什么 + 达到什么效果」的句式重写每一条，剔除「参加了」「学习了」这类无产出的流水账。\n4. 区分「已完成」与「未完成/受阻」，后者必须进入「存在问题」，不得粉饰。\n5. 提炼亮点：优先选择有量化结果、有突破、有价值的产出。\n\n注意事项：\n- 严格基于原始记录，禁止虚构未发生的工作、成绩或数据。\n- 原文提到具体数字、项目名、人名时逐字核对。\n- 若输入内容不具备工作总结属性（如歌词、代码、闲聊），在「工作概述」中如实说明「输入内容不构成有效工作记录」，其余章节留空。',
       `dr` = 0
 WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'generate'
   AND `id` = (SELECT MIN(`t`.`id`) FROM (SELECT `id` FROM `sys_ai_prompt`
                    WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'generate') AS `t`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
SELECT 'work-summary', 'system', 'generate', '工作总结-默认生成',
       '你是一位严谨的工作整理助手，擅长把零散的工作记录整理成正式、规范、结构清晰的工作总结。\n\n输入是个人或团队一段时间内的工作记录，可能是日报、周报、会议记录或流水账。请按以下步骤处理：\n1. 通读全部记录，识别这段时间的工作主线（在做哪几件事）。\n2. 归并同类项：把零散的、重复的记录合并成一条完整事项。\n3. 按「做了什么 + 达到什么效果」的句式重写每一条，剔除「参加了」「学习了」这类无产出的流水账。\n4. 区分「已完成」与「未完成/受阻」，后者必须进入「存在问题」，不得粉饰。\n5. 提炼亮点：优先选择有量化结果、有突破、有价值的产出。\n\n注意事项：\n- 严格基于原始记录，禁止虚构未发生的工作、成绩或数据。\n- 原文提到具体数字、项目名、人名时逐字核对。\n- 若输入内容不具备工作总结属性（如歌词、代码、闲聊），在「工作概述」中如实说明「输入内容不构成有效工作记录」，其余章节留空。',
       0
 WHERE NOT EXISTS (SELECT 1 FROM `sys_ai_prompt` WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'system' AND `prompt_use` = 'generate');


-- ============================================================
-- 29. 演示管理员账号（系统必需，非业务数据）
--
-- 背景：新机器只跑 init.sql 建库后，sys_user 是空表 —— 无法登录、也无法
--      创建第一个用户，整个系统不可用。故必须随建库插入一个管理员。
--      这是「系统必需的初始账号」，与历史/运行/产物等业务数据不同类。
--
-- ⚠️ 演示账号：AIT00000000 / ZzAdmin
--    明文密码：asrtest123   —— 弱口令，**仅限本地开发调试**
--    公网 / 毕设答辩部署前，务必先登录修改密码，或直接删除本节 INSERT。
--
-- password 存的是 BCrypt 哈希（cost=10，60 字符，$2a$10$ 开头），
-- 不是明文。该哈希复用自已实测登录成功的现网账号，
-- 已用 POST /api/user/login 验证（HTTP 200 + 签发 token）。
--
-- 幂等：account 上有唯一键 uk_account，用 INSERT ... ON DUPLICATE KEY UPDATE
--       保证重复执行不报错、不产生重复行；并把 dr 复位为 0。
-- ============================================================
INSERT INTO `sys_user` (`account`, `username`, `email`, `password`, `avatar`, `role`, `status`, `dr`)
VALUES ('AIT00000000', 'ZzAdmin', 'admin@example.com',
        '$2a$10$kZ51bcHdsSEZoGy8E3TVMudpMFGFcDrlUdLDRywYkNeIXVcHzYpHC',
        '', 'admin', 1, 0)
ON DUPLICATE KEY UPDATE
    `username`  = VALUES(`username`),
    `email`     = VALUES(`email`),
    `role`      = VALUES(`role`),
    `status`    = VALUES(`status`),
    `dr`        = 0;


-- ============================================================
-- 建库自检：表数量应为 13 张（与现网 ai_toolbox 库一致）
-- ============================================================
SELECT COUNT(*) AS table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';

SELECT `tool_code`, `tool_name`, `input_type`, `output_type`, `sort_no`, `dr`
FROM `sys_aitools_tool`
ORDER BY `sort_no`;

