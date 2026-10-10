package com.example.aitools.controller;

import com.example.aitools.common.ResultCode;
import com.example.aitools.common.Result;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchUploadResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.AiOfficeToolService;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * AI 文件解读控制器（MiniMax M3 多模态）
 * <p>POST：批量上传 + 异步处理 + 同步返回 batchId
 * <p>GET completed：复用 AiOfficeToolController 的 /batch/{batchId}/completed 端点
 */
@RestController
@RequestMapping("/api/ai-office/ai-file-reader")
@RequiredArgsConstructor
@Slf4j
public class AiFileReaderController {

    private final AiOfficeToolService aiOfficeToolService;
    private final BatchTaskService batchTaskService;
    private final AuthUtil authUtil;

    /**
     * 批量解读任务线程池。
     * <p>
     * 原为 Controller 私有 {@code Executors.newSingleThreadExecutor()}：队列**无界**，
     * 池满时不会拒绝而是无限排队，而每个排队任务都持有最多 200MB 的文件字节数组，
     * 并发上传时足以把堆打满；且该池完全绕开了 ExecutorConfig 的容量保护与停机等待。
     * <p>
     * 改用统一的 batchExecutor（与 document-summary / OCR 批处理同池）：
     * 队列有界 + AbortPolicy，饱和时由本类显式转成「服务繁忙」，不再静默堆积。
     * <p>
     * 注：这里不用 streamExecutor —— 本任务是**批量异步处理**（结果写库、前端轮询），
     * 不是 SSE 流式推送，语义上属于 batchExecutor 的职责。
     */
    @org.springframework.beans.factory.annotation.Qualifier(ExecutorConfig.BATCH_EXECUTOR)
    private final Executor executor;

    /**
     * 批量上传文件（1-10 个）：立即建任务 + 异步处理 + 同步返回 batchId
     */
    @PostMapping(value = "/batch-upload", produces = "application/json;charset=UTF-8")
    public Result<BatchUploadResponse> batchUpload(@RequestParam("files") List<MultipartFile> files,
                                                   @RequestParam(value = "prompt", required = false) String prompt,
                                                   HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请至少上传 1 个文件");
        }
        if (files.size() > 10) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(), "单次最多上传 10 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 200L * 1024 * 1024) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(), "批量文件总大小超过 200MB");
        }

        String batchId = batchTaskService.createTask(userId, "ai-file-reader", files.size());
        log.info("[B2-FILE] 创建批量任务 batchId={} userId={} fileCount={}", batchId, userId, files.size());

        // 同步把 MultipartFile 读到 byte[]（避开 Tomcat 异步线程跑批时临时文件已被清理）
        List<BatchFilePayload> payloads;
        try {
            payloads = files.stream().map(f -> {
                try {
                    return BatchFilePayload.from(f);
                } catch (java.io.IOException e) {
                    throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "读取文件失败，请重试");
                }
            }).toList();
        } catch (BusinessException e) {
            throw e;
        }

        try {
            executor.execute(() -> {
            try {
                batchTaskService.markRunning(batchId);
                var result = aiOfficeToolService.aiFileReaderBatchStream(userId, payloads, prompt, batchId);
                batchTaskService.completeBatch(batchId, result.getSuccessCount(), result.getFailCount(), result.getResultJson());
                log.info("[B2-FILE] 完成 batchId={} success={} fail={}", batchId, result.getSuccessCount(), result.getFailCount());
            } catch (Exception e) {
                log.error("[B2-FILE] 异常 batchId={}", batchId, e);
                try {
                    batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
                } catch (Exception ex) {
                    // 不能静默吞：兜底 completeBatch 失败会让任务永久停留在 RUNNING，
                    // 前端轮询永远等不到终态，且服务端无任何痕迹
                    log.error("[B2-FILE] 兜底 completeBatch 失败 batchId={}", batchId, ex);
                }
            }
            });
        } catch (RejectedExecutionException e) {
            // 线程池饱和：任务未启动，但 batchId 已建，不置失败前端会一直轮询等终态
            log.error("[B2-FILE] 任务被拒（线程池饱和）batchId={}", batchId, e);
            try {
                batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
            } catch (Exception ex) {
                log.error("[B2-FILE] 兜底 completeBatch 失败 batchId={}", batchId, ex);
            }
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(),
                    ResultCode.SERVICE_UNAVAILABLE.getMessage());
        }

        BatchUploadResponse resp = new BatchUploadResponse();
        resp.setBatchId(batchId);
        resp.setFileCount(files.size());
        return Result.success("任务已创建", resp);
    }
}
