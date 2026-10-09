-- ============================================================
-- 工作流产物表
-- ============================================================
-- 背景：工作流节点目前只产出文本（node_results.outputs 为 List<String>），
--      但工具（证件照换背景色 / 人像换背景图等）会产出图片、视频等文件。
--      把文件塞进 node_results JSON 会导致：体积膨胀、无法按类型检索、
--      无法统计产物体积、无法独立下载。
--      故单独建表存文件类产物，文本仍留在 node_results。
--
-- 设计要点：
--   1. run_id + node_id + file_index 三者定位一个产物，
--      与 node_results.outputs 的下标一一对应（N 个输入 → N 个输出）。
--   2. output_type 区分文本/图片/视频/音频/文件，
--      前端按类型分支渲染（文本直接渲染，其余给预览+下载）。
--   3. 文件实体存 COS，表里只存 URL + 元信息，
--      避免大文件占用数据库；私有文件走签名 URL（默认 5 分钟有效）。
--
-- 配套：node_results 增加 outputIds 字段（数组，与 outputs 同序），
--      值为本表 id 的字符串形式，避免在 outputs 里塞标记串污染文本。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_workflow_output` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`        varchar(64)  NOT NULL COMMENT 'sys_workflow_run.run_id',
    `node_id`       varchar(32)  NOT NULL COMMENT 'nodeId，如 n1',
    `node_name`     varchar(64)           DEFAULT NULL COMMENT '节点名称快照',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流产物表（文本留 node_results，文件类产物存此表）';
