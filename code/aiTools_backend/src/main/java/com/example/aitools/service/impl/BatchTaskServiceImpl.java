package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitools.common.BatchTaskStatusEnum;
import com.example.aitools.entity.BatchTask;
import com.example.aitools.mapper.BatchTaskMapper;
import com.example.aitools.service.BatchTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 批量任务服务实现（多文件上传 B2 方案用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTaskServiceImpl implements BatchTaskService {

    private final BatchTaskMapper batchTaskMapper;

    @Override
    public String createTask(Long userId, String toolCode, int fileCount) {
        BatchTask task = new BatchTask();
        task.setBatchId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setToolCode(toolCode);
        task.setFileCount(fileCount);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setStatus(STATUS_PENDING);
        task.setResultSummary("[]");
        task.setDr(0);
        batchTaskMapper.insert(task);
        return task.getBatchId();
    }

    @Override
    public boolean markRunning(String batchId) {
        LambdaUpdateWrapper<BatchTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BatchTask::getBatchId, batchId)
               .set(BatchTask::getStatus, STATUS_RUNNING);
        return batchTaskMapper.update(null, wrapper) > 0;
    }

    @Override
    public void completeBatch(String batchId, int successCount, int failCount, String resultSummaryJson) {
        int status;
        if (successCount == 0 && failCount > 0) {
            status = STATUS_FAILED;
        } else if (failCount > 0) {
            status = STATUS_PARTIAL;
        } else {
            status = STATUS_COMPLETED;
        }
        LambdaUpdateWrapper<BatchTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BatchTask::getBatchId, batchId)
               .set(BatchTask::getStatus, status)
               .set(BatchTask::getSuccessCount, successCount)
               .set(BatchTask::getFailCount, failCount)
               .set(BatchTask::getResultSummary, resultSummaryJson)
               .set(BatchTask::getFinishedAt, LocalDateTime.now());
        batchTaskMapper.update(null, wrapper);
    }

    /**
     * 单文件完成追加：先查当前 result_summary（旧数组字符串）+ success/fail + processedIndex，
     * 把新 item 追加到数组末尾，再原子 update。
     * 业务约束：调用方按单文件串行调用（service 线程池单线程），不需要担心并发。
     * JSON 处理策略：直接字符串拼接（item 内部由调用方保证是合法 JSON），不引 Jackson 增加复杂度。
     */
    @Override
    public void appendItem(String batchId, String itemJson, boolean ok) {
        // 1) 查当前状态（必须先查 processedIndex + resultSummary 才能原子追加）
        BatchTask current = batchTaskMapper.selectOne(
                new LambdaQueryWrapper<BatchTask>()
                        .eq(BatchTask::getBatchId, batchId)
                        .eq(BatchTask::getDr, 0));
        if (current == null) {
            log.warn("[B2] appendItem 任务不存在 batchId={}", batchId);
            return;
        }
        // 2) 拼新 result_summary：取旧数组去掉末尾 ]，追加 ",{item}]"（item 已含 { ... }）
        String oldJson = current.getResultSummary();
        String newJson;
        if (oldJson == null || oldJson.isBlank() || "[]".equals(oldJson)) {
            newJson = "[" + itemJson + "]";
        } else {
            // 旧数组去掉末尾 ']'
            int tail = oldJson.lastIndexOf(']');
            if (tail < 0) {
                // 异常状态：旧数据不是合法数组，重置为单元素数组
                log.warn("[B2] appendItem result_summary 异常，重置 batchId={} old={}", batchId, oldJson);
                newJson = "[" + itemJson + "]";
            } else {
                newJson = oldJson.substring(0, tail) + "," + itemJson + "]";
            }
        }
        // 3) 原子 update：processed_index + 1 / successCount + 1 or failCount + 1 / result_summary
        LambdaUpdateWrapper<BatchTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BatchTask::getBatchId, batchId)
               .set(BatchTask::getResultSummary, newJson)
               .set(BatchTask::getProcessedIndex, current.getProcessedIndex() == null ? 1 : current.getProcessedIndex() + 1)
               .set(BatchTask::getSuccessCount, ok ? (current.getSuccessCount() == null ? 1 : current.getSuccessCount() + 1) : current.getSuccessCount())
               .set(BatchTask::getFailCount, ok ? current.getFailCount() : (current.getFailCount() == null ? 1 : current.getFailCount() + 1));
        batchTaskMapper.update(null, wrapper);
    }

    @Override
    public BatchTask getByBatchId(String batchId) {
        LambdaQueryWrapper<BatchTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BatchTask::getBatchId, batchId)
               .eq(BatchTask::getDr, 0);
        return batchTaskMapper.selectOne(wrapper);
    }

    @Override
    public List<BatchTask> listByUser(Long userId, int limit) {
        LambdaQueryWrapper<BatchTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BatchTask::getUserId, userId)
               .eq(BatchTask::getDr, 0)
               .orderByDesc(BatchTask::getCreateTime)
               .last("LIMIT " + limit);
        return batchTaskMapper.selectList(wrapper);
    }

    /**
     * 订阅批量任务进度（SSE 帧推送）：状态判读 / 文案选择 / SSE 推送 全部下沉到 Impl
     * 终态：补发 result_summary（带 --- 标记），前端可整段渲染
     * 进行中：提示用户改用 batch-upload 订阅实时进度或用 batch-status 轮询
     */
    @Override
    public void subscribeProgress(BatchTask task, SseEmitter emitter) {
        BatchTaskStatusEnum status = BatchTaskStatusEnum.of(task.getStatus());
        try {
            if (status != null && status.isTerminal()) {
                send(emitter, "--- [任务已完成，状态：" + status.getLabel() + "] ---\n");
                send(emitter, (task.getResultSummary() == null ? "" : task.getResultSummary()) + "\n");
                send(emitter, "--- [全部完成] ---\n");
            } else {
                String label = status == null ? "未知" : status.getLabel();
                send(emitter, "--- [任务进行中，状态：" + label + "，请稍候] ---\n");
                send(emitter, "请通过 batch-upload 端点订阅实时进度，或用 batch-status 端点轮询\n");
            }
            emitter.complete();
        } catch (IOException e) {
            log.warn("[B2] 补发 SSE 失败 batchId={}", task.getBatchId(), e);
            emitter.completeWithError(e);
        }
    }

    /** SSE 单帧发送（出错只记日志，不抛） */
    private void send(SseEmitter emitter, String data) throws IOException {
        emitter.send(SseEmitter.event().data(data));
    }

    /**
     * 推"处理完成"结束帧：内部算终态 + 拼 label。
     * Controller 不应该自己判断 success/fail 选 status / 拼文案。
     */
    @Override
    public void emitFinishedFrame(SseEmitter emitter, int successCount, int failCount) {
        BatchTaskStatusEnum status;
        if (successCount == 0 && failCount > 0) {
            status = BatchTaskStatusEnum.FAILED;
        } else if (failCount > 0) {
            status = BatchTaskStatusEnum.PARTIAL;
        } else {
            status = BatchTaskStatusEnum.COMPLETED;
        }
        try {
            send(emitter, "--- [已入库，状态：" + status.getLabel() + "] ---\n");
        } catch (IOException e) {
            log.warn("[B2] 推送完成帧失败 success={} fail={}", successCount, failCount, e);
        }
    }
}
