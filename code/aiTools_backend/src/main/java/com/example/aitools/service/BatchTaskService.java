package com.example.aitools.service;

import com.example.aitools.common.BatchTaskStatusEnum;
import com.example.aitools.entity.BatchTask;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 批量任务服务接口（多文件上传 B2 方案用）
 * 实现类：service/impl/BatchTaskServiceImpl.java
 * 状态码字面量统一引用 BatchTaskStatusEnum，不在 interface 里手写
 */
public interface BatchTaskService {

    int STATUS_PENDING = BatchTaskStatusEnum.PENDING.getCode();
    int STATUS_RUNNING = BatchTaskStatusEnum.RUNNING.getCode();
    int STATUS_COMPLETED = BatchTaskStatusEnum.COMPLETED.getCode();
    int STATUS_PARTIAL = BatchTaskStatusEnum.PARTIAL.getCode();
    int STATUS_FAILED = BatchTaskStatusEnum.FAILED.getCode();

    /** 创建 PENDING 任务，返回 batchId（UUID） */
    String createTask(Long userId, String toolCode, int fileCount);

    /** PENDING -> RUNNING */
    boolean markRunning(String batchId);

    /** 设置 COMPLETED/PARTIAL/FAILED 状态 + 汇总结果 */
    void completeBatch(String batchId, int successCount, int failCount, String resultSummaryJson);

    /** 按 batchId 查（软筛 dr=0） */
    BatchTask getByBatchId(String batchId);

    /** 用户的任务列表（按 create_time 倒序） */
    List<BatchTask> listByUser(Long userId, int limit);

    /**
     * 订阅批量任务进度（SSE 帧推送）。由 Impl 负责：
     * 1) 判断状态终态 / 进行中
     * 2) 选文案 + 拼装 SSE 帧
     * 3) 推完 emitter.complete()
     *
     * 注意：调用方需先保证 task 存在 + userId 匹配（鉴权已在 Controller 做）
     * @param task 数据库里的任务（已通过鉴权）
     * @param emitter 已 new 出来的 SseEmitter（超时由调用方控制）
     */
    void subscribeProgress(BatchTask task, SseEmitter emitter);

    /**
     * 推"处理完成"结束帧：内部根据 success/fail 算终态 + 用 BatchTaskStatusEnum 拼 label。
     * 由 upload 端点在调 completeBatch 之后调用一次。
     */
    void emitFinishedFrame(SseEmitter emitter, int successCount, int failCount);
}
