package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.dto.AiSummaryDTO;
import com.example.aitools.dto.AiWorkSummaryDTO;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchUploadResponse;
import com.example.aitools.entity.BatchTask;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.AiOfficeToolService;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.AiWorkSummaryVO;
import com.example.aitools.vo.BatchStatusVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai-office")
@RequiredArgsConstructor
@Slf4j
public class AiOfficeToolController {

    private final AiOfficeToolService aiOfficeToolService;
    private final AuthUtil authUtil;
    private final BatchTaskService batchTaskService;

    /** SSE 流式任务线程池（毕设简化：单线程串行执行流式调用） */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 工作总结
     */
    @PostMapping("/work-summary")
    public Result<AiWorkSummaryVO> aiWorkSummary(@Valid @RequestBody AiWorkSummaryDTO dto,
                                                  HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        String result = aiOfficeToolService.aiWorkSummary(userId, dto.getContent(),
                dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId());
        return Result.success("生成成功", new AiWorkSummaryVO(result));
    }

    /**
     * 工作总结（SSE 流式）：逐块推送 AI 生成内容，内部统一管理历史记录
     */
    @PostMapping(value = "/work-summary/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter workSummaryStream(@Valid @RequestBody AiWorkSummaryDTO dto,
                                        HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        // 异步执行流式，不阻塞请求线程
        executor.execute(() -> {
            try {
                aiOfficeToolService.aiWorkSummaryStream(userId, dto.getContent(),
                        dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 周报生成（SSE 流式）：逐块推送 AI 生成内容，内部统一管理历史记录
     */
    @PostMapping(value = "/weekly-report/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter weeklyReportStream(@Valid @RequestBody AiWorkSummaryDTO dto,
                                         HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        // 异步执行流式，不阻塞请求线程
        executor.execute(() -> {
            try {
                aiOfficeToolService.aiWeeklyReportStream(userId, dto.getContent(),
                        dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 会议纪要（SSE 流式）：逐块推送 AI 生成内容，内部统一管理历史记录
     */
    @PostMapping(value = "/meeting-minutes/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter meetingMinutesStream(@Valid @RequestBody AiWorkSummaryDTO dto,
                                            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        // 异步执行流式，不阻塞请求线程
        executor.execute(() -> {
            try {
                aiOfficeToolService.aiMeetingMinutesStream(userId, dto.getContent(),
                        dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 文档重点提取（SSE 流式，multipart 上传文档）：解析文档后流式调 AI 提炼重点
     */
    @PostMapping(value = "/document-summary/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter documentSummaryStream(@RequestParam("file") MultipartFile file,
                                            AiSummaryDTO dto,
                                            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        // 异步执行流式，不阻塞请求线程
        executor.execute(() -> {
            try {
                aiOfficeToolService.aiDocumentSummaryStream(userId, file,
                        dto.getPromptFormat(), dto.getPromptGenerate(), dto.getPromptId(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * OCR 智能识别（SSE 流式，multipart 上传图片）：腾讯云 OCR 提取文字 → 调 AI 整理成结构化结果
     */
    @PostMapping(value = "/ocr-recognize/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter ocrRecognizeStream(@RequestParam("file") MultipartFile file,
                                         AiSummaryDTO dto,
                                         HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时（OCR + AI 两步）

        executor.execute(() -> {
            try {
                aiOfficeToolService.aiOcrStream(userId, file,
                        dto.getPromptFormat(), dto.getPromptGenerate(), dto.getPromptId(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 兼容旧前端：未按用途拆分 prompt 时，将其作为生成内容提示词（generate），格式提示词走系统默认
     */
    private String resolvePromptGenerate(AiWorkSummaryDTO dto) {
        if (dto.getPromptGenerate() != null && !dto.getPromptGenerate().isBlank()) {
            return dto.getPromptGenerate();
        }
        return dto.getPrompt();
    }

    // ==================== 批量文档处理（逐文件入库 + 前端轮询方案） ====================
    // 流程：upload 端点同步建任务 + 启异步线程跑批（不再开 SSE）
    //      service 内每文件完成立即调 batchTaskService.appendItem 入库
    //      全部跑完调 completeBatch 写终态
    //      前端用 GET /batch/{batchId}/completed?since=N 轮询拉增量 items

    /** 批量上传文档（1-10 个）：立即建任务 + 异步处理 + 立即返回 batchId */
    @PostMapping(value = "/document-summary/batch-upload", produces = "application/json;charset=UTF-8")
    public Result<BatchUploadResponse> batchDocumentUpload(@RequestParam("files") List<MultipartFile> files,
                                          @RequestParam(value = "promptFormat", required = false) String promptFormat,
                                          @RequestParam(value = "promptGenerate", required = false) String promptGenerate,
                                          @RequestParam(value = "promptId", required = false) Long promptId,
                                          HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (files == null || files.isEmpty()) {
            throw new com.example.aitools.exception.BusinessException("请至少上传 1 个文件");
        }
        if (files.size() > 10) {
            throw new com.example.aitools.exception.BusinessException("单次最多上传 10 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 200L * 1024 * 1024) {
            throw new com.example.aitools.exception.BusinessException("批量文件总大小超过 200MB");
        }

        // 1) 立即建任务（HTTP 同步返回的关键）
        String batchId = batchTaskService.createTask(userId, "doc-keypoint-extract", files.size());
        log.info("[B2] 创建批量任务 batchId={} userId={} fileCount={}", batchId, userId, files.size());

        // 2) 同步阶段先把每个 MultipartFile 读到 byte[]（关键！避开 Tomcat 异步线程跑批时临时文件已被清理）
        java.util.List<BatchFilePayload> payloads;
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

        // 3) 异步跑批量处理（service 内部单文件完即 appendItem，全部跑完调 completeBatch）
        executor.execute(() -> {
            try {
                batchTaskService.markRunning(batchId);
                com.example.aitools.dto.BatchProcessResult result = aiOfficeToolService.aiDocumentSummaryBatchStream(
                        userId, payloads, promptFormat, promptGenerate, promptId, batchId);
                // 全部跑完入库（终态 + result_summary）
                batchTaskService.completeBatch(batchId, result.getSuccessCount(), result.getFailCount(), result.getResultJson());
                log.info("[B2] 批量任务完成 batchId={} success={} fail={}", batchId, result.getSuccessCount(), result.getFailCount());
            } catch (Exception e) {
                log.error("[B2] 批量任务异常 batchId={}", batchId, e);
                try {
                    batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
                } catch (Exception ignore) {}
            }
        });

        // 3) 同步返回 batchId（前端拿这个去轮询 /completed）
        BatchUploadResponse resp = new BatchUploadResponse();
        resp.setBatchId(batchId);
        resp.setFileCount(files.size());
        return Result.success("任务已创建", resp);
    }

    /**
     * 批量 OCR 智能识别（1-10 张图片/PDF）：立即建任务 + 异步处理 + 立即返回 batchId
     */
    @PostMapping(value = "/ocr-recognize/batch-upload", produces = "application/json;charset=UTF-8")
    public Result<BatchUploadResponse> batchOcrUpload(@RequestParam("files") List<MultipartFile> files,
                                     @RequestParam(value = "promptFormat", required = false) String promptFormat,
                                     @RequestParam(value = "promptGenerate", required = false) String promptGenerate,
                                     HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (files == null || files.isEmpty()) {
            throw new com.example.aitools.exception.BusinessException("请至少上传 1 个文件");
        }
        if (files.size() > 10) {
            throw new com.example.aitools.exception.BusinessException("单次最多上传 10 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 200L * 1024 * 1024) {
            throw new com.example.aitools.exception.BusinessException("批量文件总大小超过 200MB");
        }

        String batchId = batchTaskService.createTask(userId, "ocr-recognize", files.size());
        log.info("[OCR-B2] 创建批量任务 batchId={} userId={} fileCount={}", batchId, userId, files.size());

        // 同步阶段先把每个 MultipartFile 读到 byte[]（关键！避开 Tomcat 异步线程跑批时临时文件已被清理）
        java.util.List<BatchFilePayload> payloads;
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
                com.example.aitools.dto.BatchProcessResult result = aiOfficeToolService.aiOcrBatchStream(
                        userId, payloads, promptFormat, promptGenerate, batchId);
                batchTaskService.completeBatch(batchId, result.getSuccessCount(), result.getFailCount(), result.getResultJson());
                log.info("[OCR-B2] 完成 batchId={} success={} fail={}", batchId, result.getSuccessCount(), result.getFailCount());
            } catch (Exception e) {
                log.error("[OCR-B2] 异常 batchId={}", batchId, e);
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

    /** 拉取批量任务增量完成项（since=已拉取数，返回 since 之后的新 items） */
    @GetMapping("/batch/{batchId}/completed")
    public Result<BatchStatusVO> batchCompleted(@PathVariable("batchId") String batchId,
                                                @RequestParam(value = "since", defaultValue = "0") int since,
                                                HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        BatchTask task = batchTaskService.getByBatchId(batchId);
        if (task == null) {
            return Result.fail("任务不存在或已过期");
        }
        if (!task.getUserId().equals(userId)) {
            return Result.fail("无权访问此任务");
        }
        return Result.success(BatchStatusVO.from(task, since));
    }

    /** 兼容旧前端：批量任务 SSE 端点保留（仅做"补发已完成结果"），新前端可忽略 */
    @GetMapping(value = "/document-summary/batch-stream/{batchId}", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter batchDocumentStream(@PathVariable("batchId") String batchId, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        BatchTask task = batchTaskService.getByBatchId(batchId);
        if (task == null) {
            throw new com.example.aitools.exception.BusinessException("任务不存在或已过期");
        }
        if (!task.getUserId().equals(userId)) {
            throw new com.example.aitools.exception.BusinessException("无权访问此任务");
        }
        SseEmitter emitter = new SseEmitter(60000L);
        executor.execute(() -> batchTaskService.subscribeProgress(task, emitter));
        return emitter;
    }

    /** 兼容旧前端：批量任务状态查询（带 items 全量） */
    @GetMapping("/document-summary/batch-status/{batchId}")
    public Result<BatchStatusVO> batchStatus(@PathVariable("batchId") String batchId, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        BatchTask task = batchTaskService.getByBatchId(batchId);
        if (task == null) {
            return Result.fail("任务不存在或已过期");
        }
        if (!task.getUserId().equals(userId)) {
            return Result.fail("无权访问此任务");
        }
        // 旧端点返回全部 items（since=0）
        return Result.success(BatchStatusVO.from(task, 0));
    }
}
