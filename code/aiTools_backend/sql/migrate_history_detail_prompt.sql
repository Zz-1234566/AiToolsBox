-- ============================================================
-- 历史明细表：补 prompt_format / prompt_generate 两列
-- ============================================================
-- 背景：entity/HistoryDetail.java 定义了 promptFormat / promptGenerate 两个字段，
--      用于「历史回填原参数重发」——保存用户当时在格式提示词、生成提示词
--      textarea 里的原始内容（resolve 前），历史记录回显时能还原当时的输入。
--
-- 问题：这两列此前是用 ALTER TABLE 手工加到已有库上的，没有沉淀回 init.sql，
--      导致按脚本建库的新环境缺列。MyBatis-Plus 会按实体自动生成 SELECT
--      （HistoryServiceImpl#listRecent 里的 historyDetailMapper.selectList），
--      缺列即抛 Unknown column，整条历史链路全线 500：
--      GET /api/history/list 500，同一 Mapper 的写入侧
--      （record / appendDetail / markFailed）同样失败。
--
-- 与 init.sql 的关系：
--   新建库    → 执行 sql/init.sql 即可，建表语句已含这两列，无需本脚本。
--   已有库    → 执行本脚本补列；已存在则跳过，重复执行不报错。
--   升级记录  → init.sql 表下的「升级记录」注释块也记录了这两条 ALTER。
--
-- 兼容性：MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，
--      故先用 information_schema.COLUMNS 判断列是否存在再 ALTER
--      （与 sql/init_tool_output.sql 里 migrate_sys_tool_output 同一套写法）。
--      说明：DELIMITER 是 mysql 客户端指令，用 mysql 命令行 / Navicat 执行本脚本即可，
--      若用 JDBC/程序批量执行请去掉 DELIMITER。
-- ============================================================

-- ---------- 1. 幂等补列 ----------
DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_sys_aitools_history_detail $$
CREATE PROCEDURE migrate_sys_aitools_history_detail()
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    -- 1) prompt_format
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history_detail'
       AND COLUMN_NAME = 'prompt_format';
    IF v_exists = 0 THEN
        ALTER TABLE `sys_aitools_history_detail`
            ADD COLUMN `prompt_format` TEXT
                COMMENT '用户当时在格式提示词 textarea 里的内容（resolve 前）' AFTER `error_msg`;
    END IF;

    -- 2) prompt_generate
    SELECT COUNT(*) INTO v_exists
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history_detail'
       AND COLUMN_NAME = 'prompt_generate';
    IF v_exists = 0 THEN
        ALTER TABLE `sys_aitools_history_detail`
            ADD COLUMN `prompt_generate` TEXT
                COMMENT '用户当时在生成提示词 textarea 里的内容（resolve 前）' AFTER `prompt_format`;
    END IF;
END $$

CALL migrate_sys_aitools_history_detail() $$
DROP PROCEDURE IF EXISTS migrate_sys_aitools_history_detail $$

DELIMITER ;

-- ---------- 2. 结果自检（两条都应为 1）----------
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_aitools_history_detail'
   AND COLUMN_NAME IN ('prompt_format', 'prompt_generate')
 ORDER BY ORDINAL_POSITION;
