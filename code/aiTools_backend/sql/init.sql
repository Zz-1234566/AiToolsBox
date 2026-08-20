-- ============================================================
-- AI Toolbox 数据库初始化脚本
-- 首次部署执行：创建数据库、全部业务表与初始化数据
-- ============================================================

-- -------------------------------------------
-- 1. 创建数据库
-- -------------------------------------------
CREATE DATABASE IF NOT EXISTS `ai_toolbox` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `ai_toolbox`;

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
  `status` TINYINT DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`),
  UNIQUE KEY `uk_email_dr` (`email`, `dr`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 升级记录（历史，新装环境无需执行）：
-- 1) 新增 email 字段（邮箱注册/找回密码功能）
--    ALTER TABLE `sys_user` ADD COLUMN `email` VARCHAR(64) NOT NULL COMMENT '邮箱' AFTER `username`;
-- 2) 唯一索引由单列 uk_email 调整为复合 uk_email_dr(email, dr)，支持软删后邮箱复用
--    ALTER TABLE `sys_user` DROP INDEX `uk_email`, ADD UNIQUE KEY `uk_email_dr` (`email`, `dr`);

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
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI工具使用历史主表';

-- -------------------------------------------
-- 6. AI 工具使用历史明细表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_aitools_history_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `history_id` BIGINT NOT NULL COMMENT '历史记录ID',
  `input_content` TEXT COMMENT '输入内容',
  `output_content` TEXT COMMENT '输出内容',
  `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息（失败时）',
  `dr` TINYINT DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_history_id` (`history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI工具使用历史明细表';

-- 升级记录（历史，新装环境无需执行）：
-- ALTER TABLE `sys_aitools_history_detail` ADD COLUMN `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息（失败时）' AFTER `output_content`;

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
  `role` TINYINT DEFAULT 0 COMMENT '文件角色：0输入 1输出',
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
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('work-summary', 'system', 'generate', '工作总结-默认生成', '你是一位严谨的工作整理助手，擅长把零散的工作记录整理成正式、规范、结构清晰的工作总结。\n严格要求：\n1. 只输出工作总结内容，禁止输出任何解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，语气规范，像职场日报/周报。\n4. 每个分点必须单独占一行，段落之间空一行，保证可读性。\n5. 直接给出总结结果，不要重复用户输入的内容。', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- work-summary 系统提示词（format 格式：四段式模板，%s 为工作内容占位）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('work-summary', 'system', 'format', '工作总结-默认格式', '请将以下工作记录整理成正式的工作总结，按以下四个部分输出：\n一、已完成事项\n二、进行中事项\n三、遇到的问题\n四、下一步计划\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n工作记录：\n%s', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- 旧数据清理：user 类型模板已迁移至用户自定义表（sys_ai_user_prompt），从系统表移除
DELETE FROM `sys_ai_prompt` WHERE `tool_code` = 'work-summary' AND `prompt_type` = 'user';

-- -------------------------------------------
-- 12. 初始化数据：工具（doc-keypoint-extract 文档重点提取）
-- -------------------------------------------
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('doc-keypoint-extract', 'AI办公助手', '文档重点提取', 'office', '上传文档自动提炼重点内容', '', 2, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

-- -------------------------------------------
-- 12.1 初始化数据：其余工具入库（共 10 个，sort_no 3-12）
-- -------------------------------------------

-- AI办公助手（已有序号1-2，从3开始）
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('weekly-report', 'AI办公助手', '周报生成', 'office', '输入工作内容生成周报', '', 3, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('meeting-minutes', 'AI办公助手', '会议纪要', 'office', '整理会议核心结论', '', 4, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('ocr-recognize', 'AI办公助手', '智能识别', 'office', '发票、名片、文字识别', '', 5, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

-- 图片创意工具（sort_no 6-9）
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('id-photo-bg-change', '图片创意工具', '证件照换背景色', 'image', '红蓝白底自由切换', '', 6, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('portrait-bg-replace', '图片创意工具', '人像换背景图', 'image', 'AI 抠图替换背景', '', 7, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('image-compress', '图片创意工具', '图片压缩', 'image', '压缩图片大小', '', 8, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('qr-code-gen', '图片创意工具', '二维码生成', 'image', '生成网址/名片二维码', '', 9, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

-- 效率小工具（sort_no 10-12）
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('todo-list', '效率小工具', '待办清单', 'efficiency', '记录每日待办事项', '', 10, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('pomodoro', '效率小工具', '番茄钟', 'efficiency', '专注工作学习', '', 11, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('password-gen', '效率小工具', '密码生成', 'efficiency', '生成安全随机密码', '', 12, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);

-- -------------------------------------------
-- 13. 初始化数据：系统提示词（doc-keypoint-extract）
-- -------------------------------------------

-- doc-keypoint-extract 系统提示词（generate 生成内容：人设）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('doc-keypoint-extract', 'system', 'generate', '文档重点提取-默认生成', '你是一位专业的文档分析助手，擅长从文档中准确提炼核心重点。\n严格要求：\n1. 只输出文档的重点内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用流畅的中文书面表达，条理清晰。\n4. 每个要点单独占一行，段落之间空一行。\n5. 直接给出提炼结果，不要重复文档原文。', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- doc-keypoint-extract 系统提示词（format 格式：结构化模板，%s 为文档内容占位）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('doc-keypoint-extract', 'system', 'format', '文档重点提取-默认格式', '请从以下文档中提炼核心重点，按以下结构输出：\n一、文档主题\n二、核心要点（3-8条）\n三、关键数据/结论\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n文档内容：\n%s', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- -------------------------------------------
-- 14. 初始化数据：系统提示词（weekly-report / meeting-minutes / ocr-recognize）
-- -------------------------------------------

-- weekly-report 系统提示词（generate 生成内容：人设）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('weekly-report', 'system', 'generate', '周报生成-默认生成', '你是一位专业的职场周报撰写助手，擅长将一周的工作内容整理成结构清晰、重点突出的工作周报。\n严格要求：\n1. 只输出周报内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，语气规范，像职场周报。\n4. 每个分点单独占一行，段落之间空一行。\n5. 直接给出周报结果，不要重复用户输入的内容。', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- weekly-report 系统提示词（format 格式：四段式模板，%s 为本周工作内容占位）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('weekly-report', 'system', 'format', '周报生成-默认格式', '请将以下本周工作内容整理成正式的工作周报，按以下结构输出：\n一、本周完成事项\n二、进行中事项\n三、遇到的问题\n四、下周计划\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n本周工作内容：\n%s', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- meeting-minutes 系统提示词（generate 生成内容：人设）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('meeting-minutes', 'system', 'generate', '会议纪要-默认生成', '你是一位专业的会议纪要整理助手，擅长从会议内容或语音转写文字中提炼核心结论和行动项。\n严格要求：\n1. 只输出会议纪要内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 使用正式的中文书面表达，条理清晰。\n4. 每个要点单独占一行，段落之间空一行。\n5. 直接给出纪要结果，不要重复用户输入的内容。', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- meeting-minutes 系统提示词（format 格式：四段式模板，%s 为会议内容占位）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('meeting-minutes', 'system', 'format', '会议纪要-默认格式', '请将以下会议内容整理成正式的会议纪要，按以下结构输出：\n一、会议主题\n二、讨论要点\n三、会议决议\n四、行动项（责任人+截止时间）\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 每个部分下的要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\n会议内容：\n%s', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- ocr-recognize 系统提示词（generate 生成内容：人设）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('ocr-recognize', 'system', 'generate', '智能识别-默认生成', '你是一位专业的文字识别助手，擅长从图片中准确识别文字内容并整理成结构化信息。\n严格要求：\n1. 只输出识别到的文字内容，禁止输出解释、说明、客套话或代码块。\n2. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）。\n3. 识别的文字保持原文，不要篡改或补充。\n4. 如果是发票、名片等结构化文档，按字段分行输出。\n5. 无法识别的部分标注"（无法识别）"。', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- ocr-recognize 系统提示词（format 格式：结构化模板，%s 为待识别内容占位）
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('ocr-recognize', 'system', 'format', '智能识别-默认格式', '请识别以下内容中的文字信息，按以下结构输出：\n一、识别内容类型（发票/名片/文档/其他）\n二、识别到的文字内容\n三、关键字段（如有）\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 文字内容按原文顺序逐行输出；\n3. 部分之间空一行。\n\n待识别内容：\n%s', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);


-- -------------------------------------------
-- 升级SQL（已建库环境执行）：工具按模块归类 + 提示词按工具隔离
-- -------------------------------------------
-- 1. 工具表加 tool_type 字段
ALTER TABLE `sys_aitools_tool` ADD COLUMN `tool_type` VARCHAR(32) DEFAULT NULL COMMENT '所属模块：AI办公助手/图片创意工具/效率小工具' AFTER `tool_code`;
-- 2. 现有工具补 tool_type
UPDATE `sys_aitools_tool` SET `tool_type` = 'AI办公助手' WHERE `tool_code` IN ('work-summary', 'ai-summary');
-- 3. ai-summary 改名 doc-keypoint-extract（工具表 + 系统提示词表）
UPDATE `sys_aitools_tool` SET `tool_code` = 'doc-keypoint-extract', `tool_name` = '文档重点提取' WHERE `tool_code` = 'ai-summary';
UPDATE `sys_ai_prompt` SET `tool_code` = 'doc-keypoint-extract' WHERE `tool_code` = 'ai-summary';
-- 4. 用户提示词表加 tool_code
ALTER TABLE `sys_ai_user_prompt` ADD COLUMN `tool_code` VARCHAR(32) NOT NULL COMMENT '所属工具编码（绑定具体工具）' AFTER `user_id`;
ALTER TABLE `sys_ai_user_prompt` ADD INDEX `idx_user_tool` (`user_id`, `tool_code`);
-- 5. 补全其余工具入库（已建库环境，sort_no 3-12）
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('weekly-report', 'AI办公助手', '周报生成', 'office', '输入工作内容生成周报', '', 3, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('meeting-minutes', 'AI办公助手', '会议纪要', 'office', '整理会议核心结论', '', 4, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('ocr-recognize', 'AI办公助手', '智能识别', 'office', '发票、名片、文字识别', '', 5, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('id-photo-bg-change', '图片创意工具', '证件照换背景色', 'image', '红蓝白底自由切换', '', 6, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('portrait-bg-replace', '图片创意工具', '人像换背景图', 'image', 'AI 抠图替换背景', '', 7, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('image-compress', '图片创意工具', '图片压缩', 'image', '压缩图片大小', '', 8, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('qr-code-gen', '图片创意工具', '二维码生成', 'image', '生成网址/名片二维码', '', 9, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('todo-list', '效率小工具', '待办清单', 'efficiency', '记录每日待办事项', '', 10, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('pomodoro', '效率小工具', '番茄钟', 'efficiency', '专注工作学习', '', 11, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('password-gen', '效率小工具', '密码生成', 'efficiency', '生成安全随机密码', '', 12, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`), `tool_type` = VALUES(`tool_type`);



-- -------------------------------------------
-- 16. 初始化数据：工具（ocr-recognize 智能识别 / OCR）
-- -------------------------------------------
INSERT INTO `sys_aitools_tool` (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `sort_no`, `status`, `dr`)
VALUES ('ocr-recognize', 'AI办公助手', '智能识别', 'office', '上传图片自动识别文字（OCR）', '', 5, 1, 0)
ON DUPLICATE KEY UPDATE `tool_name` = VALUES(`tool_name`);

-- -------------------------------------------
-- 17. 初始化数据：系统提示词（ocr-recognize）
-- -------------------------------------------
INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('ocr-recognize', 'system', 'generate', '默认整理', '你是一位严谨的文字整理助手，擅长把 OCR 识别出的原始文字整理成清晰、结构化的可读文本。\n严格要求：\n1. 保留原文所有关键信息（数字、日期、姓名、地址、金额等不得遗漏或编造）；\n2. 修正明显的 OCR 错字（根据上下文推断），但不要重写或扩展内容；\n3. 禁止使用 Markdown 格式（不要 ###、**、- 列表符号、表格、代码块等标记）；\n4. 使用流畅的中文书面表达，按原文逻辑分段，段落之间空一行；\n5. 直接给出整理结果，不要解释你做了什么。', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

INSERT INTO `sys_ai_prompt` (`tool_code`, `prompt_type`, `prompt_use`, `prompt_name`, `prompt_content`, `dr`)
VALUES ('ocr-recognize', 'system', 'format', '默认格式', '请将以下 OCR 识别出的原始文字整理成结构化可读文本，按以下结构输出：\n一、关键信息（如有：日期/编号/金额/姓名）\n二、正文内容（修正错字、按逻辑分段）\n三、备注（如有：识别不确定的部分）\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 要点用"1. 2. 3."编号，每个要点单独一行；\n3. 部分之间空一行。\n\nOCR 识别文字：\n%s', 0)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

-- -------------------------------------------
-- 18. 初始化数据：工具（bank-receipt-recognize 银行回单识别）
-- -------------------------------------------
INSERT INTO sys_aitools_tool (	tool_code, 	tool_type, 	tool_name, component_type, description, icon, sort_no, status, dr)
VALUES ('bank-receipt-recognize', 'AI办公助手', '银行回单识别', 'office', '上传银行回单图片自动识别并结构化整理', '', 6, 1, 0)
ON DUPLICATE KEY UPDATE 	tool_name = VALUES(	tool_name);

-- -------------------------------------------
-- 19. 初始化数据：系统提示词（bank-receipt-recognize）
-- -------------------------------------------
INSERT INTO sys_ai_prompt (	tool_code, prompt_type, prompt_use, prompt_name, prompt_content, dr)
VALUES ('bank-receipt-recognize', 'system', 'generate', '默认整理', '你是一位严谨的银行单据整理助手，擅长把 OCR 识别出的银行回单原始文字整理成结构化、字段清晰、可直接归档的标准格式。\n严格要求：\n1. 只整理 OCR 识别出的文字，不补充、不编造任何数字、日期、金额、账户、户名等信息；识别不到就标"未识别"；\n2. 数字必须保留原始精度（金额保留 2 位小数，账号/卡号保留所有位数，不四舍五入、不省略）；\n3. 修正明显的 OCR 错字（如"0/O"、"1/l/I"、"元/园"等），根据上下文合理推断，但不要重写或意译；\n4. 禁止使用 Markdown 格式（不要 ###、**、-、表格、代码块等任何标记）；\n5. 使用流畅的中文书面表达，按字段分类组织，字段之间空一行；\n6. 同一字段出现多次（如对手方信息）时按原文保留全部内容。', 0)
ON DUPLICATE KEY UPDATE prompt_content = VALUES(prompt_content);

INSERT INTO sys_ai_prompt (	tool_code, prompt_type, prompt_use, prompt_name, prompt_content, dr)
VALUES ('bank-receipt-recognize', 'system', 'format', '默认格式', '请将以下 OCR 识别出的银行回单原始文字整理为结构化格式，按以下结构输出：\n\n一、单据类型\n（自动识别：电子回单 / 业务回单 / 交易明细 / 进账单 / 跨行转账回单 等）\n\n二、关键信息\n1. 回单编号：\n2. 交易日期：\n3. 交易时间：\n4. 业务类型：（如：转账汇款、跨行实时汇出、货款结算等）\n5. 币种：（如：人民币 CNY）\n\n三、付款方信息\n1. 付款方名称：\n2. 付款方账号：\n3. 付款方开户行：\n\n四、收款方信息\n1. 收款方名称：\n2. 收款方账号：\n3. 收款方开户行：\n\n五、金额信息\n1. 小写金额：（格式：¥XXX.XX）\n2. 大写金额：（如：壹仟贰佰叁拾肆元伍角陆分）\n3. 手续费：（如有）\n4. 实际到账金额：（如有）\n\n六、附言与备注\n（保留原文所有备注信息）\n\n七、关键 OCR 修正说明\n（如有错字修正，简单列出"原文 X → 修正 Y"；无可不输出此段）\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 字段用"1. 2. 3."编号，每个字段单独一行；\n3. 字段值为空时写"未识别"（不要省略字段）；\n4. 部分之间空一行。\n\nOCR 识别文字：\n%s', 0)
ON DUPLICATE KEY UPDATE prompt_content = VALUES(prompt_content);

-- -------------------------------------------
-- 20. 初始化数据：工具（invoice-recognize 发票识别）
-- -------------------------------------------
INSERT INTO sys_aitools_tool (	tool_code, 	tool_type, 	tool_name, component_type, description, icon, sort_no, status, dr)
VALUES ('invoice-recognize', 'AI办公助手', '发票识别', 'office', '上传发票图片自动识别并结构化整理', '', 7, 1, 0)
ON DUPLICATE KEY UPDATE 	tool_name = VALUES(	tool_name);

-- -------------------------------------------
-- 21. 初始化数据：系统提示词（invoice-recognize）
-- -------------------------------------------
INSERT INTO sys_ai_prompt (	tool_code, prompt_type, prompt_use, prompt_name, prompt_content, dr)
VALUES ('invoice-recognize', 'system', 'generate', '默认整理', '你是一位严谨的财务单据整理助手，擅长把 OCR 识别出的发票原始文字整理成结构化、字段清晰、可直接用于报销和记账的标准格式。\n严格要求：\n1. 只整理 OCR 识别出的文字，不补充、不编造任何数字、金额、税率、税号等信息；识别不到就标"未识别"；\n2. 数字必须保留原始精度（金额保留 2 位小数，税率保留百分比格式，税号/发票号保留所有位数，不四舍五入、不省略）；\n3. 修正明显的 OCR 错字（如"0/O"、"1/l/I"、"元/园"、"税/悦"等），根据上下文合理推断，但不要重写或意译；\n4. 禁止使用 Markdown 格式（不要 ###、**、-、表格、代码块等任何标记）；\n5. 使用流畅的中文书面表达，按字段分类组织，字段之间空一行；\n6. 同类项有多个时（如多行明细）按原文顺序全部保留，不要合并或省略；\n7. 大写金额必须从数字金额换算后输出（壹贰叁肆伍陆柒捌玖零元角分），不要照搬 OCR 可能写错的大写。', 0)
ON DUPLICATE KEY UPDATE prompt_content = VALUES(prompt_content);

INSERT INTO sys_ai_prompt (	tool_code, prompt_type, prompt_use, prompt_name, prompt_content, dr)
VALUES ('invoice-recognize', 'system', 'format', '默认格式', '请将以下 OCR 识别出的发票原始文字整理为结构化格式，按以下结构输出：\n\n一、发票类型\n（自动识别：增值税专用发票 / 增值税普通发票 / 电子发票 / 卷式发票 / 定额发票 / 全电发票 等）\n\n二、发票基础信息\n1. 发票代码：\n2. 发票号码：\n3. 开票日期：\n4. 校验码：\n5. 发票章戳：（如"发票专用章"已盖、是否有电子签章）\n\n三、购买方信息\n1. 名称：\n2. 纳税人识别号（统一社会信用代码）：\n3. 地址、电话：\n4. 开户行及账号：\n\n四、销售方信息\n1. 名称：\n2. 纳税人识别号（统一社会信用代码）：\n3. 地址、电话：\n4. 开户行及账号：\n\n五、金额与税额\n1. 价税合计（小写）：¥\n2. 价税合计（大写）：零壹贰叁肆伍陆柒捌玖元角分\n3. 不含税金额（小写）：¥\n4. 税率：\n5. 税额（小写）：¥\n6. 税额（大写）：\n7. 税种：（如：增值税）\n\n六、明细项目\n（每行一项，格式："1. 商品名称 规格型号 单位 数量 单价 金额 税率 税额"）\n（如无明细写"未提供明细"）\n\n七、备注\n（保留原文所有备注、附加说明）\n\n八、关键 OCR 修正说明\n（如有错字修正，简单列出"原文 X → 修正 Y"；特别是大写金额；无可不输出此段）\n\n格式要求：\n1. 每个部分标题单独一行；\n2. 字段用"1. 2. 3."编号，每个字段单独一行；\n3. 字段值为空时写"未识别"（不要省略字段）；\n4. 明细项目每行独立，不要用表格符号；\n5. 部分之间空一行。\n\nOCR 识别文字：\n%s', 0)
ON DUPLICATE KEY UPDATE prompt_content = VALUES(prompt_content);



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

ALTER TABLE sys_batch_task ADD COLUMN processed_index INT DEFAULT 0 COMMENT '已处理文件数（成功+失败，用于前端轮询 since 增量）' AFTER fail_count;