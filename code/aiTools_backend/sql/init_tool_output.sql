-- ============================================================
-- 工具产物表（全局）
-- ============================================================
-- 定位：本表是**全局工具产物表**，任何工具产出文件都写这一张表，
--      不仅限于工作流节点。
--
-- 背景：文本类产物（OCR 文字、翻译结果、摘要等）直接返回给前端即可；
--      但绝大多数工具会产出图片 / 视频 / 音频 / 文件。
--      把文件塞进响应体或 JSON 里会导致：体积膨胀、无法按类型检索、
--      无法统计产物体积、无法独立下载、无法复用历史产物。
--      故单独建表存文件类产物，文本类不入本表（output_type=1 预留）。
--
-- 适用场景（run_id / node_id / node_name 三者是否可空，取决于来源）：
--      来源                  run_id  node_id  node_name  tool_code
--      工作流节点产出         有值    有值     有值       有值
--      独立调用工具产出       NULL    NULL     NULL       有值
--   1. 工作流节点产物：可按 run_id + node_id 定位，
--      与 sys_workflow_run.node_results.outputs 的下标一一对应（N 个输入 → N 个输出）。
--   2. 独立调用工具（如「证件照换背景色」用户单独点按钮使用）：产物照样写本表，
--      只是没有工作流上下文，run_id / node_id / node_name 均为 NULL。
--
-- 设计要点：
--   1. tool_code + run_id + node_id + file_index 联合定位一个产物，
--      其中 run_id / node_id 可空（非工作流来源）。
--   2. output_type 区分文本/图片/视频/音频/文件，
--      前端按类型分支渲染（文本直接渲染，其余给预览+下载）。
--   3. 文件实体存 COS，表里只存 URL + 元信息，
--      避免大文件占用数据库；私有文件走签名 URL（默认 5 分钟有效）。
--
-- 配套：node_results 增加 outputIds 字段（数组，与 outputs 同序），
--      值为本表 id 的字符串形式，避免在 outputs 里塞标记串污染文本。
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
-- 兼容旧库：把工作流专用的三个字段改为可空（幂等迁移）
-- ============================================================
-- 早期版本 run_id / node_id 是 NOT NULL（只为工作流节点设计），
-- node_name 虽可空但注释也只提工作流。本表升级为全局工具产物表后，
-- 独立调用工具的产物没有工作流上下文，需要这三列能存 NULL。
--
-- 上面 CREATE TABLE 只对新建库生效，已存在的库需要执行下面这段。
-- MySQL 不支持 MODIFY COLUMN IF NOT EXISTS，
-- 故先用 information_schema.COLUMNS 判断该列是否仍为 NOT NULL
-- 或注释仍是旧文案，是则执行 ALTER。已是目标状态则跳过，重复执行不报错。
-- 说明：DELIMITER 是 mysql 客户端指令，用 mysql 命令行 / Navicat 执行本脚本即可，
--      若用 JDBC/程序批量执行请去掉 DELIMITER。

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_sys_tool_output $$
CREATE PROCEDURE migrate_sys_tool_output()
BEGIN
    DECLARE v_not_nullable INT DEFAULT 0;

    -- 1) run_id
    SELECT COUNT(*) INTO v_not_nullable
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tool_output'
       AND COLUMN_NAME = 'run_id'
       AND (IS_NULLABLE = 'NO'
            OR COLUMN_COMMENT <> 'sys_workflow_run.run_id；仅工作流来源有值，独立调用工具时为 NULL');
    IF v_not_nullable > 0 THEN
        ALTER TABLE `sys_tool_output`
            MODIFY COLUMN `run_id` varchar(64) DEFAULT NULL
            COMMENT 'sys_workflow_run.run_id；仅工作流来源有值，独立调用工具时为 NULL';
    END IF;

    -- 2) node_id
    SELECT COUNT(*) INTO v_not_nullable
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tool_output'
       AND COLUMN_NAME = 'node_id'
       AND (IS_NULLABLE = 'NO'
            OR COLUMN_COMMENT <> 'nodeId，如 n1；仅工作流来源有值，独立调用工具时为 NULL');
    IF v_not_nullable > 0 THEN
        ALTER TABLE `sys_tool_output`
            MODIFY COLUMN `node_id` varchar(32) DEFAULT NULL
            COMMENT 'nodeId，如 n1；仅工作流来源有值，独立调用工具时为 NULL';
    END IF;

    -- 3) node_name
    SELECT COUNT(*) INTO v_not_nullable
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tool_output'
       AND COLUMN_NAME = 'node_name'
       AND (IS_NULLABLE = 'NO'
            OR COLUMN_COMMENT <> '节点名称快照；仅工作流来源有值，独立调用工具时为 NULL');
    IF v_not_nullable > 0 THEN
        ALTER TABLE `sys_tool_output`
            MODIFY COLUMN `node_name` varchar(64) DEFAULT NULL
            COMMENT '节点名称快照；仅工作流来源有值，独立调用工具时为 NULL';
    END IF;
END $$

CALL migrate_sys_tool_output() $$
DROP PROCEDURE migrate_sys_tool_output $$
DELIMITER ;

-- 4) 旧库表注释仍是「工作流产物表」，同步为全局工具产物表语义（幂等，可重复执行）
ALTER TABLE `sys_tool_output`
    COMMENT = '全局工具产物表（任何工具产出的文件类产物都存此表；仅工作流来源 run_id/node_id/node_name 有值，独立调用工具时为 NULL）';
