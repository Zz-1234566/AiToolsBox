-- =====================================================================
-- 批量任务表：新增「error_msg」字段，记录任务级失败原因
-- =====================================================================
-- 背景：新增了 BatchTaskRecoveryJob（僵尸任务定时回收），回收时需要写明
--      「为什么失败」（如「服务重启或任务进程异常中断」），前端据此告知用户。
--      字段加在 BatchTask 实体上后，**所有**对 sys_batch_task 的
--      INSERT / UPDATE 都会带上 error_msg —— 若线上库没有这一列，
--      批量任务会在落库时抛：
--        Unknown column 'error_msg' in 'field list'
--      （连带 completeBatch / failRemaining 全部失败 → 批量任务全线不可用）
--
-- 兼容性：可空字段，存量行不受影响；应用侧 errorMsg 为 null 时前端回退到通用文案。
--
-- 执行：幂等，已存在则跳过。全新安装走 init.sql（已含同样的语句），无需重复执行。
-- =====================================================================

-- ---------- 1. 新增字段（幂等：已存在则跳过）----------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_batch_task' AND COLUMN_NAME = 'error_msg');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE sys_batch_task ADD COLUMN error_msg VARCHAR(255) DEFAULT NULL COMMENT ''失败原因（如僵尸任务被自动回收、服务异常中断）'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. 校验 ----------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_batch_task'
  AND COLUMN_NAME = 'error_msg';
