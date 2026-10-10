package com.example.aitools.controller;

import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.common.Result;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchUploadResponse;
import com.example.aitools.dto.TranscribeResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.AiOfficeToolService;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.service.TranscribeService;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.utils.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 录音转文本 Controller
 * <p>
 * 接收上传的音频文件（mp3/wav/m4a），调腾讯云一句话识别，返回识别文本
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-office/meeting-minutes")
@RequiredArgsConstructor
public class TranscribeController {

    private final TranscribeService transcribeService;
    private final AiOfficeToolService aiOfficeToolService;
    private final BatchTaskService batchTaskService;
    private final AuthUtil authUtil;

    /**
     * 批量转写任务线程池。
     * 原为 Controller 私有 {@code Executors.newSingleThreadExecutor()}：
     * 队列无界，池满时无限排队，且每任务持有文件字节数组；
     * 并发上传时足以把堆打满，且完全绕开 ExecutorConfig 的容量保护与停机等待。
     *
     * 改用统一的 batchExecutor（与 OCR 批处理、文件解读同池）：
     * 队列有界 + AbortPolicy，饱和时由本类显式转成"服务繁忙"，不再静默堆积。
     */
    @Qualifier(ExecutorConfig.BATCH_EXECUTOR)
    private final ExecutorService batchExecutor;

    /**
     * 单文件录音转写。
     *
     * @param engine 转写引擎：minimax | tencent。不传则用 {@code asr.engine} 配置值（默认 minimax）
     */
    @PostMapping(value = "/transcribe", consumes = "multipart/form-data")
    public Result<TranscribeResponse> transcribe(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "engine", required = false) String engine,
                                                HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        log.info("[meeting-minutes/transcribe] userId={} file={} size={} engine={}",
                userId, file.getOriginalFilename(), file.getSize(), engine);
        try {
            TranscribeResponse resp = transcribeService.transcribe(file, engine);
            return Result.success("转写成功", resp);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[meeting-minutes/transcribe] failed", e);
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
        }
    }

    /**
     * 批量录音转写（B2）：立即建任务 + 异步串行处理 + 同步返回 batchId，前端轮询进度。
     * <p>单文件失败不影响整体；每完成一个文件即写入结果，前端可增量展示。
     */
    @PostMapping(value = "/batch-transcribe", produces = "application/json;charset=UTF-8")
    public Result<BatchUploadResponse> batchTranscribe(@RequestParam("files") List<MultipartFile> files,
                                                       @RequestParam(value = "engine", required = false) String engine,
                                                       HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请至少上传 1 个录音文件");
        }
        if (files.size() > Constants.BATCH_MAX_FILE_COUNT) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(),
                    "单次最多上传 " + Constants.BATCH_MAX_FILE_COUNT + " 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > Constants.BATCH_MAX_TOTAL_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(), "批量文件总大小超过 200MB");
        }

        String batchId = batchTaskService.createTask(userId, "audio-transcribe", files.size());
        log.info("[B2-ASR] 创建批量任务 batchId={} userId={} fileCount={} engine={}",
                batchId, userId, files.size(), engine);

        // 同步读到内存（避开 Tomcat 异步线程跑批时临时文件已被清理）
        List<BatchFilePayload> payloads;
        try {
            payloads = files.stream().map(f -> {
                try {
                    return BatchFilePayload.from(f);
                } catch (java.io.IOException e) {
                    throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "读取录音文件失败，请重试");
                }
            }).toList();
        } catch (BusinessException e) {
            log.error("[B2-ASR] 读取上传文件失败，任务置为失败 batchId={}", batchId, e);
            try {
                batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
            } catch (Exception ex) {
                log.error("[B2-ASR] 失败标记写入失败 batchId={}", batchId, ex);
            }
            throw e;
        }

        batchExecutor.execute(() -> {
            try {
                batchTaskService.markRunning(batchId);
                var result = aiOfficeToolService.audioTranscribeBatchStream(userId, payloads, batchId, engine);
                batchTaskService.completeBatch(batchId, result.getSuccessCount(), result.getFailCount(), result.getResultJson());
                log.info("[B2-ASR] 完成 batchId={} success={} fail={}", batchId, result.getSuccessCount(), result.getFailCount());
            } catch (Exception e) {
                log.error("[B2-ASR] 异常 batchId={}", batchId, e);
                try {
                    batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
                } catch (Exception ex) {
                    log.error("[B2-ASR] 兜底 completeBatch 失败 batchId={}", batchId, ex);
                }
            }
        });

        BatchUploadResponse resp = new BatchUploadResponse();
        resp.setBatchId(batchId);
        resp.setFileCount(files.size());
        return Result.success("任务已创建", resp);
    }
}
