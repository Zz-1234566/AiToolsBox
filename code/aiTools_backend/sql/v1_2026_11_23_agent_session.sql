-- ============================================================
-- 智能体会话 / 消息 / 用户画像
-- 说明：会话 = 一次连续的对话窗口；消息 = 会话内的逐条记录；
--       画像 = 从对话中抽取的用户偏好与画像摘要（全局，按 user_id 唯一）。
-- 幂等：全部 IF NOT EXISTS，可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_agent_session` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`        VARCHAR(64)  NOT NULL COMMENT '对外会话ID（UUID）',
  `user_id`           BIGINT       NOT NULL COMMENT '所属用户',
  `title`             VARCHAR(64)  NOT NULL DEFAULT '新对话' COMMENT '会话标题（首条消息截取，可重命名）',
  `summary`           TEXT         NULL COMMENT '历史上下文摘要（压缩产物）',
  `summarized_seq`    INT          NOT NULL DEFAULT 0 COMMENT '已压缩到的消息序号（含）',
  `message_count`     INT          NOT NULL DEFAULT 0 COMMENT '消息条数',
  `last_active_time`  DATETIME     NULL COMMENT '最后活跃时间',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `dr`                TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_active` (`user_id`, `last_active_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体会话';

CREATE TABLE IF NOT EXISTS `sys_agent_message` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id`  VARCHAR(64) NOT NULL COMMENT '所属会话ID',
  `user_id`     BIGINT      NOT NULL COMMENT '所属用户',
  `seq`         INT         NOT NULL COMMENT '会话内序号（从 1 递增）',
  `role`        VARCHAR(16) NOT NULL COMMENT 'user / assistant',
  `content`     MEDIUMTEXT  NULL COMMENT '消息正文',
  `meta`        TEXT        NULL COMMENT '附加信息 JSON（步骤/附件/耗时/runId）',
  `token_est`   INT         NOT NULL DEFAULT 0 COMMENT '估算 token 数',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `dr`          TINYINT     NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_session_seq` (`session_id`, `seq`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体会话消息';

CREATE TABLE IF NOT EXISTS `sys_user_profile` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`           BIGINT      NOT NULL COMMENT '用户ID',
  `preferences`       TEXT        NULL COMMENT '结构化偏好 JSON（语言/风格/常用工具等）',
  `profile_text`      TEXT        NULL COMMENT 'AI 生成的画像摘要',
  `source_session_id` VARCHAR(64) NULL COMMENT '最近一次产出画像的会话',
  `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `dr`                TINYINT     NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像与偏好';
