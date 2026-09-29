-- ============================================================================
-- 工作流模块 DDL（与 init.sql 隔离，可独立重复执行）
--
-- 内容：
--   1. sys_workflow          用户保存的工作流
--   2. sys_workflow_run      工作流运行历史
--   3. sys_aitools_tool      加 input_type / output_type（节点类型匹配用）
--   4. 回填 14 个既有工具的类型
--   5. 新增 3 个节点用工具：audio-transcribe / doc-to-text / ai-file-reader
--
-- 设计要点：
--   - 节点间传递为「数组」语义：N 个输入 → N 个输出，逐项独立处理，不混在一起
--   - 类型匹配规则：upstream.output_type ⊆ downstream.input_type（上游每种输出，下游都要能接住）
--   - input_type / output_type 为逗号分隔枚举集合（扁平集合，非嵌套结构，故不用 JSON）
--   - 层次约束：≤5 层（WORKFLOW_MAX_DEPTH），节点数量不限
--   - 幂等：全部 IF NOT EXISTS / 动态 SQL 判存在，可反复执行
-- ============================================================================

USE ai_toolbox;

-- ============================================================================
-- 1. sys_workflow —— 用户保存的工作流
-- ============================================================================
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

-- ============================================================================
-- 2. sys_workflow_run —— 工作流运行历史
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_workflow_run` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id`         VARCHAR(64)  NOT NULL COMMENT '对外 runId（UUID）',
  `workflow_id`    VARCHAR(64)  NOT NULL COMMENT '所属工作流 workflowId',
  `user_id`        BIGINT       NOT NULL COMMENT '所属用户 ID',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0待运行 1运行中 2成功 3部分失败 4失败 5已取消',
  `node_results`   MEDIUMTEXT   COMMENT '每节点结果 JSON：{nodeId:{status,inputs[],outputs[],costMs,errorMsg}}',
  `input_snapshot` MEDIUMTEXT   COMMENT '运行时输入快照 JSON：{nodeId:{text|files[]}}',
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

-- ============================================================================
-- 3. sys_aitools_tool 加 input_type / output_type（幂等 ALTER）
--    逗号分隔枚举集合，取值：none / text / audio / document / image / file
--    - file     任意文件（doc-keypoint-extract）
--    - document 文档类 pdf/word/txt（doc-to-text）
-- ============================================================================
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

-- ============================================================================
-- 4. 回填 14 个既有工具的类型
-- ============================================================================
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

-- ============================================================================
-- 5. 新增 3 个工具（sort_no 15/16/17）
--    audio-transcribe  录音转写    audio            → text   复用 TranscribeService（内部有内置提示词）
--    doc-to-text       文档转文本  document         → text   复用 DocumentParser（纯解析，不调 AI）
--    ai-file-reader    AI 文件解读 image,document,text → text  补 DB 漂移（前端 tools.js 已有、DB 缺）
-- ============================================================================
INSERT INTO `sys_aitools_tool`
    (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `input_type`, `output_type`, `sort_no`, `status`, `dr`)
VALUES
    ('audio-transcribe', 'AI办公助手', '录音转写', 'office', '上传录音自动转写为文字，供会议纪要等工具使用', '', 'audio', 'text', 15, 1, 0)
ON DUPLICATE KEY UPDATE
    `tool_name` = VALUES(`tool_name`), `input_type` = VALUES(`input_type`),
    `output_type` = VALUES(`output_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool`
    (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `input_type`, `output_type`, `sort_no`, `status`, `dr`)
VALUES
    ('doc-to-text', 'AI办公助手', '文档转文本', 'office', '上传文档（PDF/Word/TXT）提取纯文本，供会议纪要等工具使用', '', 'document', 'text', 16, 1, 0)
ON DUPLICATE KEY UPDATE
    `tool_name` = VALUES(`tool_name`), `input_type` = VALUES(`input_type`),
    `output_type` = VALUES(`output_type`), `sort_no` = VALUES(`sort_no`);

INSERT INTO `sys_aitools_tool`
    (`tool_code`, `tool_type`, `tool_name`, `component_type`, `description`, `icon`, `input_type`, `output_type`, `sort_no`, `status`, `dr`)
VALUES
    ('ai-file-reader', 'AI办公助手', 'AI 文件解读', 'office', '上传任意文件（图片/PDF/Word/TXT），AI 自动识别内容并解读', '', 'image,document,text', 'text', 17, 1, 0)
ON DUPLICATE KEY UPDATE
    `tool_name` = VALUES(`tool_name`), `input_type` = VALUES(`input_type`),
    `output_type` = VALUES(`output_type`), `sort_no` = VALUES(`sort_no`);

-- ============================================================================
-- 说明：提示词处理
--   audio-transcribe：提示词已在 TranscribeService.TRANSCRIBE_PROMPT（Java 常量）内置，
--                     用户无需填写。不重复写入 sys_ai_prompt，避免两处真相（见 code-review R3）。
--   doc-to-text：     纯解析，不调用 AI，无提示词。
--   两者前端均不渲染「格式/生成提示词」卡片。
-- ============================================================================

-- 验证：查看工具类型与 sort_no
SELECT `tool_code`, `tool_name`, `input_type`, `output_type`, `sort_no`
FROM `sys_aitools_tool`
WHERE `dr` = 0
ORDER BY `sort_no`;
