package com.example.aitools.controller;

import com.example.aitools.common.Result;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 批量上传文件（1-10 个）：立即建任务 + 异步处理 + 同步返回 batchId
     */
    @PostMapping(value = "/batch-upload", produces = "application/json;charset=UTF-8")
    public Result<BatchUploadResponse> batchUpload(@RequestParam("files") List<MultipartFile> files,
                                                   @RequestParam(value = "prompt", required = false) String prompt,
                                                   HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请至少上传 1 个文件");
        }
        if (files.size() > 10) {
            throw new BusinessException("单次最多上传 10 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 200L * 1024 * 1024) {
            throw new BusinessException("批量文件总大小超过 200MB");
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
                    throw new BusinessException("读取文件失败：" + e.getMessage());
                }
            }).toList();
        } catch (BusinessException e) {
            throw e;
        }

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
                } catch (Exception ignore) {}
            }
        });

        BatchUploadResponse resp = new BatchUploadResponse();
        resp.setBatchId(batchId);
        resp.setFileCount(files.size());
        return Result.success("任务已创建", resp);
    }
}
