-- ============================================================
-- 技能表（Skills）
-- 设计目标：新增技能 = 放一个目录 / 插一行数据，不改任何基础配置
-- 文件（classpath:skills/*/SKILL.md）与表并存：
--   文件 = 技能内容的源（随代码版本管理）
--   表   = 运营态（启停、排序、可见范围、使用统计）
-- 启动时按 skill_code upsert，body_hash 变化才更新正文
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_skill (
  id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
  skill_code   VARCHAR(64)  NOT NULL                COMMENT '技能编码，= skills/ 下目录名',
  title        VARCHAR(64)  NOT NULL                COMMENT '展示名称',
  subtitle     VARCHAR(128) DEFAULT NULL            COMMENT '一句话说明（列表用）',
  icon         VARCHAR(128) DEFAULT NULL            COMMENT '图标相对路径，如 icon.svg',
  trigger_desc VARCHAR(500) DEFAULT NULL            COMMENT '何时使用（进系统提示的技能索引）',
  category     VARCHAR(32)  DEFAULT 'general'       COMMENT '分类：doc/data/img/video/general',
  tools        VARCHAR(512) DEFAULT NULL            COMMENT '依赖工具，逗号分隔（可留空=不限制）',
  outputs      VARCHAR(64)  DEFAULT NULL            COMMENT '产物类型：docx/xlsx/pptx/pdf/png/text',
  body         LONGTEXT     DEFAULT NULL            COMMENT '技能正文（文件同步进来，便于后台查看/微调）',
  body_hash    CHAR(32)     DEFAULT NULL            COMMENT '正文摘要，用于判断是否需要同步',
  version      VARCHAR(16)  DEFAULT '0.1.0',
  scope        VARCHAR(16)  DEFAULT 'system'        COMMENT 'system=内置；user=用户自建；agent=绑定某个智能体',
  owner_id     BIGINT       DEFAULT NULL            COMMENT 'scope=user 时的归属用户',
  agent_code   VARCHAR(64)  DEFAULT NULL            COMMENT 'scope=agent 时绑定的智能体编码',
  source       VARCHAR(16)  DEFAULT 'file'          COMMENT 'file=由文件同步；manual=后台手工创建',
  status       TINYINT      DEFAULT 1               COMMENT '1启用 0禁用',
  sort_no      INT          DEFAULT 100,
  use_count    INT          DEFAULT 0               COMMENT '被读取次数（运营参考）',
  extra        JSON         DEFAULT NULL            COMMENT '扩展字段，避免将来加列',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  dr           TINYINT      DEFAULT 0,
  UNIQUE KEY uk_skill_code (skill_code),
  KEY idx_scope_status (scope, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体技能';
