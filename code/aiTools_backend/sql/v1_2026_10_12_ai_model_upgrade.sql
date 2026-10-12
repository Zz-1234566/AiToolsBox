-- =====================================================================
-- AI 模型双表升级脚本
-- 适用版本：v1 (2026-10-12)
-- 作用：
--   1) 升级 sys_ai_model：补字段(provider/capability/api_key_encrypted/api_key_prefix/sort_no/update_time)
--   2) 新建 sys_user_ai_model：用户级 BYOK 配置表
--   3) 种子数据：把 .env 兜底迁移到 sys_ai_model（老字段 api_key 留明文兼容，新代码走 api_key_encrypted）
-- =====================================================================
-- 注意：执行前请先备份数据库
-- MySQL 5.7+ / 8.0 均兼容；utf8mb4 字符集
-- =====================================================================

-- -------------------------------
-- 1. 升级系统 AI 模型表
-- -------------------------------
ALTER TABLE sys_ai_model
    ADD COLUMN provider            VARCHAR(64)     DEFAULT NULL  COMMENT '服务商(minimax/tencent/openai)',
    ADD COLUMN api_key_encrypted   VARBINARY(512)  DEFAULT NULL  COMMENT 'AES-256-GCM 加密后的 Key',
    ADD COLUMN api_key_prefix      VARCHAR(16)     DEFAULT NULL  COMMENT 'Key 前 4 位(展示脱敏)',
    ADD COLUMN capability          VARCHAR(64)     DEFAULT NULL  COMMENT '能力标签(text/vision/asr/ocr)',
    ADD COLUMN sort_no             INT             DEFAULT 0      COMMENT '排序',
    ADD COLUMN update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP         COMMENT '更新时间',
    MODIFY COLUMN api_key          VARCHAR(255)    DEFAULT NULL  COMMENT '明文 Key(兼容旧字段,不再使用)',
    MODIFY COLUMN user_id          BIGINT          DEFAULT NULL  COMMENT '所属用户(NULL=系统级)',
    ADD KEY idx_capability_default (capability, is_default, status, dr);

-- -------------------------------
-- 2. 新建用户级 AI 模型表 (BYOK)
-- -------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_ai_model` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`           BIGINT       NOT NULL                COMMENT '所属用户',
  `model_code`        VARCHAR(64)  NOT NULL                COMMENT '引用 sys_ai_model.model_code',
  `api_key_encrypted` VARBINARY(512) NOT NULL              COMMENT 'AES-256-GCM 加密后的 Key',
  `api_key_prefix`    VARCHAR(16)  DEFAULT NULL            COMMENT '预留',
  `nickname`          VARCHAR(64)  DEFAULT NULL            COMMENT '用户别名',
  `capability`        VARCHAR(64)  DEFAULT NULL            COMMENT '冗余字段',
  `is_default`        TINYINT      DEFAULT 0               COMMENT '是否用户默认',
  `status`            TINYINT      DEFAULT 1               COMMENT '1启用 0停用',
  `last_used_at`      DATETIME     DEFAULT NULL            COMMENT '上次使用时间',
  `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `dr`                TINYINT      DEFAULT 0               COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_capability` (`user_id`, `capability`, `dr`),
  KEY `idx_user_default`    (`user_id`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自配 AI 模型(BYOK)';

-- -------------------------------
-- 3. 种子数据：把 .env 兜底迁到 sys_ai_model
--    说明：api_key 字段填环境变量占位符（部署环境必须注入同名 env var）；
--          api_key_encrypted 留空（生产由后端管理界面录入 / 启动时从 env 自动加密）
-- -------------------------------
INSERT INTO sys_ai_model (model_code, model_name, provider, capability, api_url, api_model, api_key, is_default, status, sort_no)
VALUES
    ('minimax-text',   'MiniMax 文本',    'minimax', 'text',
     'https://api.minimaxi.com/v1/text/chatcompletion_v2', 'MiniMax-M2.7',
     '${AI_MINIMAX_TEXT_API_KEY}', 1, 1, 10),
    ('minimax-vision', 'MiniMax 多模态',  'minimax', 'vision',
     'https://api.minimaxi.com/v1/chat/completions', 'MiniMax-M3',
     '${AI_MINIMAX_API_KEY}', 1, 1, 20),
    ('asr-minimax',    'MiniMax 语音识别','minimax', 'asr',
     'https://api.minimaxi.com/v1/speech_to_text', 'asr-1.0',
     '${AI_MINIMAX_API_KEY}', 1, 1, 30),
    ('asr-tencent',    '腾讯云 ASR',      'tencent', 'asr',
     NULL, '16k_zh',
     '${ASR_SECRET_ID}|${ASR_SECRET_KEY}|${ASR_APP_ID}', 1, 0, 40),
    ('tencent-ocr',    '腾讯云 OCR',       'tencent', 'ocr',
     NULL, 'GeneralAccurateOCR',
     '${COS_SECRET_ID}|${COS_SECRET_KEY}', 1, 0, 50)
ON DUPLICATE KEY UPDATE
    model_name = VALUES(model_name),
    provider   = VALUES(provider),
    capability = VALUES(capability),
    api_url    = VALUES(api_url),
    api_model  = VALUES(api_model),
    api_key    = VALUES(api_key),
    sort_no    = VALUES(sort_no);