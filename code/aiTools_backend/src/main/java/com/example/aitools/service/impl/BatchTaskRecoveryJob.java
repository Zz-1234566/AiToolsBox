package com.example.aitools.service.impl;

import com.example.aitools.common.BatchTaskStatusEnum;
import com.example.aitools.common.Constants;
import com.example.aitools.entity.BatchTask;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.service.BatchUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量任务「僵尸恢复」扫描。
 *
 * <p><b>要解决的问题</b>：批量任务在**进程崩溃 / OOM / 被 kill** 时，
 * 正在跑的任务永远停在 PENDING 或 RUNNING —— 数据库里没有「已死」这种状态，
 * 只有「没人再碰它了」。后果：
 * <ul>
 *   <li>前端轮询 {@code /batch/{id}/completed} 永远拿不到终态 → 卡片一直转圈；</li>
 *   <li>僵尸行在 {@code sys_batch_task} 里永久堆积。</li>
 * </ul>
 *
 * <p><b>判据</b>：{@code update_time} 早于「阈值时间」。
 * {@code sys_batch_task.update_time} 由 MySQL 的 {@code ON UPDATE CURRENT_TIMESTAMP}
 * 自动维护（见 sql/init.sql），所以「很久没被更新过」就等价于「没人再处理它了」。
 *
 * <p><b>为什么用 scheduled 扫描而不是给任务加心跳</b>：心跳要覆盖所有任务
 * （写库频率 = 任务数 × 心跳周期），而扫描只读「已经僵死的那几条」，
 * 数据量与在跑任务数无关，成本恒定。
 *
 * <p><b>多实例部署</b>：本实现是**单实例**语义。多实例时多个节点会同时扫到同一条，
 * 但兜底动作是幂等的（{@code WHERE status IN (0,1)} + 只会把状态推到终态），
 * 因此重复执行不会产生脏数据，只是会多打一条日志。
 * 若后续要多实例部署且要求「同一任务只由一个节点恢复」，
 * 需要在此处引入分布式锁（Redis SETNX）—— 属于独立改造。
 */
@Slf4j
@Component
public class BatchTaskRecoveryJob {

    /** 状态推进失败的原因（写入 error_msg / 日志） */
    private static final String RECOVERY_REASON = "服务重启或任务进程异常中断，任务已自动置为失败，请重新提交";

    private final BatchTaskService batchTaskService;
    private final BatchUploadService batchUploadService;

    /**
     * RUNNING 状态的超时阈值（分钟）。
     *
     * <p>RUNNING 说明任务确实在跑。阈值要**大于单批最长处理时间**，
     * 否则会把正常跑得慢的任务误杀。默认 30 分钟：按单文件 AI 处理 5-30 秒、
     * 单批 20 个文件估算，30 分钟有充足余量。
     */
    @Value("${batch.recovery.running-timeout-minutes:30}")
    private long runningTimeoutMinutes;

    /**
     * PENDING 状态的超时阈值（分钟）。
     *
     * <p>PENDING 说明任务已建好但**还没被线程池取走**（在排队）。
     * 阈值要比 RUNNING 更宽松 —— 线程池排队本身是正常现象，
     * 只有「排了太久还没轮到」才说明进程已经不在了。默认 60 分钟。
     */
    @Value("${batch.recovery.pending-timeout-minutes:60}")
    private long pendingTimeoutMinutes;

    /**
     * 批量临时文件的保留时长（分钟）。默认 120 分钟。
     *
     * <p>正常路径下临时文件在异步任务结束时就被删掉了；这个阈值是给
     * 「进程被 kill，finally 没执行」兜底的 —— 超过这个时长的残留文件会被扫描删掉。
     * 设得比最长批量任务耗时大得多，避免删掉还在处理中的文件。
     */
    @Value("${batch.recovery.temp-file-retention-minutes:120}")
    private long tempFileRetentionMinutes;

    /**
     * 扫描间隔（毫秒）。默认 5 分钟。
     *
     * <p>用 fixedDelay（任务结束到下次开始）而不是 fixedRate（固定间隔），
     * 避免上一轮没跑完就叠加下一轮。
     */
    @Value("${batch.recovery.scan-interval-ms:300000}")
    private long scanIntervalMs;

    public BatchTaskRecoveryJob(BatchTaskService batchTaskService,
                                BatchUploadService batchUploadService) {
        this.batchTaskService = batchTaskService;
        this.batchUploadService = batchUploadService;
    }

    /**
     * 扫描并兜底僵死任务。
     *
     * <p>分两轮处理（PENDING / RUNNING 各一轮），因为两者的超时阈值不同。
     */
    @Scheduled(fixedDelayString = "${batch.recovery.scan-interval-ms:300000}",
            initialDelayString = "${batch.recovery.initial-delay-ms:60000}")
    public void recoverStaleTasks() {
        try {
            recoverStaleTasks(BatchTaskStatusEnum.PENDING.getCode(), pendingTimeoutMinutes, "PENDING");
            recoverStaleTasks(BatchTaskStatusEnum.RUNNING.getCode(), runningTimeoutMinutes, "RUNNING");
        } catch (Exception e) {
            // 扫描本身失败不能影响主流程（也不能让后续调度停掉）
            log.error("[B2-Recovery] 僵尸任务扫描异常", e);
        }
        // 顺手清掉残留的批量临时文件（进程被 kill 时 finally 不会执行，文件会留在磁盘上）
        try {
            batchUploadService.purgeStaleTempFiles(
                    Path.of(System.getProperty("java.io.tmpdir"), Constants.BATCH_TEMP_DIR_NAME),
                    tempFileRetentionMinutes * 60L * 1000L);
        } catch (Exception e) {
            log.warn("[B2-Recovery] 清理残留批量临时文件失败", e);
        }
    }

    /**
     * 兜底某一类状态的僵死任务。
     *
     * @param status          目标状态（PENDING / RUNNING）
     * @param timeoutMinutes  超时阈值（分钟）
     * @param statusLabel     状态名（仅用于日志）
     */
    private void recoverStaleTasks(int status, long timeoutMinutes, String statusLabel) {
        if (timeoutMinutes <= 0) {
            return; // 阈值配成 0 视为关闭该类恢复
        }
        LocalDateTime before = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<BatchTask> stale = batchTaskService.listStaleTasks(List.of(status), before);
        if (stale == null || stale.isEmpty()) {
            return;
        }
        for (BatchTask t : stale) {
            String batchId = t.getBatchId();
            int fileCount = t.getFileCount() == null ? 0 : t.getFileCount();
            int success = t.getSuccessCount() == null ? 0 : t.getSuccessCount();
            int fail = t.getFailCount() == null ? 0 : t.getFailCount();
            // 只把「没处理完」的文件补成失败，已完成的保留
            int remaining = Math.max(0, fileCount - success - fail);
            log.warn("[B2-Recovery] 回收僵死任务 batchId={} status={} toolCode={} fileCount={} success={} fail={} 补失败={}",
                    batchId, statusLabel, t.getToolCode(), fileCount, success, fail, remaining);
            try {
                // failRemaining 内部会读当前计数、保留 result_summary，并按差额补失败数
                batchTaskService.failRemaining(batchId, fileCount, RECOVERY_REASON);
            } catch (Exception e) {
                log.error("[B2-Recovery] 回收失败 batchId={}", batchId, e);
            }
        }
    }
}
