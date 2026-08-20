package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.dto.AiSummaryDTO;
import com.example.aitools.dto.AiWorkSummaryDTO;
import com.example.aitools.dto.BatchUploadResponse;
import com.example.aitools.entity.BatchTask;
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

    // ==================== 批量文档处理（B1+B2 混合方案） ====================
    // 流程：upload 端点内部直接开 SseEmitter 异步处理 + 推流，处理完入库
    //      stream 端点只用于：① 任务已完成时补发 result_summary；② 心跳保活；③ 断线重连

    /** 批量上传文档（1-10 个）+ 立即开 SSE 推流：返回 SseEmitter（HTTP 短连接立即返回） */
    @PostMapping(value = "/document-summary/batch-upload", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter batchDocumentUpload(@RequestParam("files") List<MultipartFile> files,
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

        // 1) 立即建任务（HTTP 短连接的关键）
        String batchId = batchTaskService.createTask(userId, "doc-keypoint-extract", files.size());
        log.info("[B2] 创建批量任务 batchId={} userId={} fileCount={}", batchId, userId, files.size());

        // 2) 立即返回 emitter（HTTP 短连接断开，service 在线程池里异步跑）
        SseEmitter emitter = new SseEmitter(600000L); // 10 分钟超时

        executor.execute(() -> {
            try {
                batchTaskService.markRunning(batchId);
                emitter.send(SseEmitter.event().data("--- [任务已创建 batchId=" + batchId + "，开始处理] ---\n"));

                // 3) 跑批量流式处理（返回封装结果：successCount / failCount / resultJson）
                com.example.aitools.dto.BatchProcessResult result = aiOfficeToolService.aiDocumentSummaryBatchStream(
                        userId, files, promptFormat, promptGenerate, promptId,
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

                // 4) 处理完入库
                batchTaskService.completeBatch(batchId, result.getSuccessCount(), result.getFailCount(), result.getResultJson());
                log.info("[B2] 批量任务完成 batchId={} success={} fail={}", batchId, result.getSuccessCount(), result.getFailCount());

                // 5) 推结束帧（状态判读 + 文案 在 service 里）
                batchTaskService.emitFinishedFrame(emitter, result.getSuccessCount(), result.getFailCount());
                emitter.complete();
            } catch (Exception e) {
                log.error("[B2] 批量任务异常 batchId={}", batchId, e);
                // 异常时也要入库（标记为 FAILED）
                try {
                    batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
                } catch (Exception ignore) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** 订阅批量任务进度（SSE）：仅做"补发已完成结果 / 心跳"（断线重连 / 后到客户端补看） */
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
        SseEmitter emitter = new SseEmitter(60000L); // 1 分钟超时（仅补发）
        executor.execute(() -> batchTaskService.subscribeProgress(task, emitter));
        return emitter;
    }

    /** 查询批量任务状态（断线重连 / 业务方轮询用） */
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
        return Result.success(BatchStatusVO.from(task));
    }
}
