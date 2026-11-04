-- ============================================================
-- sys_agent_run —— 智能体运行记录表
-- ============================================================
-- 定位：智能体（agent）每次运行的完整过程记录。
--       「用户给目标 → AI 自己调工具 → 出答案」这个过程天然是逐帧的，
--       steps 字段把每一步（想什么 / 调了什么 / 结果）存下来，
--       既是回放依据，也是排查「为什么这么答」的线索
--       （工作流那边还缺逐帧日志，见 docs/handover 的 §7.4 —— 本表只解决智能体这条链路）。
--
-- 为什么步骤存 JSON 数组而不是单独建一张步骤表：
--   与 sys_workflow_run.node_results 同构（项目既有风格），
--   步骤只在「读某次运行的详情」时整体取出，不存在按步骤查询的场景，
--   拆表反而多一次 join。
--
-- ⚠️ 模型文件不入库；本表也不含任何密钥 / 用户隐私内容
--    （steps 里的工具结果是工具产物本身，与普通工具历史一致）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_agent_run` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id`       VARCHAR(64)  NOT NULL COMMENT '对外 runId（UUID）',
  `user_id`      BIGINT       NOT NULL COMMENT '所属用户 ID',
  `goal`         TEXT         NOT NULL COMMENT '用户目标',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待运行 1运行中 2成功 3失败 4已取消',
  `steps`        MEDIUMTEXT   COMMENT '步骤 JSON 数组：[{seq,thought,tool,args,output,outputTruncated,costMs,errorMsg}]',
  `final_output` MEDIUMTEXT   COMMENT '最终答案',
  `step_count`   INT          DEFAULT 0 COMMENT '实际步数（工具调用次数）',
  `duration`     INT          DEFAULT 0 COMMENT '总耗时（毫秒）',
  `error_msg`    VARCHAR(500) DEFAULT NULL COMMENT '整体错误信息',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finished_at`  DATETIME     DEFAULT NULL COMMENT '完成时间',
  `dr`           TINYINT      DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_run_id` (`run_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体运行记录表';
