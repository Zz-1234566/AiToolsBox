-- ============================================================
-- 工具运行历史：新增「来源」字段，支持工作流调用的工具记录
-- ============================================================
-- 背景：工具拆分为解析层/加工层后，一个工作流会串联多个工具节点，
--      但 sys_aitools_history 原本只记录「谁调了哪个工具」，
--      无法回答「这次记录是哪条工作流、哪个节点产生的」。
--
-- 设计：加可空字段而非联合主键 —— 因为「单独跑工具」时 run_id 为空，
--      主键就失去意义；且一条工作流跑 5 个节点会产生 5 条历史、
--      run_id 相同，联合主键必然冲突。
--
-- 两种记录共用本表：
--   单独跑工具：source_type=1, run_id=NULL, node_id=NULL
--   工作流节点：source_type=2, run_id=<本次运行ID>, node_id=<节点ID>
-- ============================================================

-- ---------- 1. 新增字段 ----------
ALTER TABLE `sys_aitools_history`
    ADD COLUMN `source_type` tinyint NOT NULL DEFAULT 1
        COMMENT '来源：1=单工具直接调用 2=工作流节点调用' AFTER `ai_code`,
    ADD COLUMN `run_id` varchar(64) DEFAULT NULL
        COMMENT '工作流运行ID（sys_workflow_run.run_id）；单工具调用为 NULL' AFTER `source_type`,
    ADD COLUMN `node_id` varchar(32) DEFAULT NULL
        COMMENT '工作流节点ID（node_results 的键，如 n1）；单工具调用为 NULL' AFTER `run_id`,
    ADD COLUMN `node_name` varchar(64) DEFAULT NULL
        COMMENT '节点名称快照（如「文档提取」）；工作流改名后仍显示当时的名称' AFTER `node_id`,
    ADD INDEX `idx_run_id` (`run_id`),
    ADD INDEX `idx_user_source` (`user_id`, `source_type`);

-- ---------- 2. 存量数据标记为「单工具调用」 ----------
UPDATE `sys_aitools_history` SET `source_type` = 1 WHERE `source_type` IS NULL OR `source_type` = 0;

-- ---------- 3. 给初始化脚本补同样的字段（新建库时用）----------
-- 说明：init.sql 中的 CREATE TABLE sys_aitools_history 需追加以下列定义：
--   source_type tinyint NOT NULL DEFAULT 1 COMMENT '来源：1=单工具 2=工作流节点',
--   run_id      varchar(64) DEFAULT NULL COMMENT '工作流运行ID，单工具为 NULL',
--   node_id     varchar(32) DEFAULT NULL COMMENT '工作流节点ID，单工具为 NULL',
--   node_name   varchar(64) DEFAULT NULL COMMENT '节点名称快照',
-- 本项目采用「init.sql + 迁移脚本」的方式初始化，故这里只做注释提示。
