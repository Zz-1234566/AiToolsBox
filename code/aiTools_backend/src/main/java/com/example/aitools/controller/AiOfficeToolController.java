package com.example.aitools.controller;

import com.example.aitools.common.ResultCode;
import com.example.aitools.common.Constants;
import com.example.aitools.common.Result;
import com.example.aitools.common.StreamHelper;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.dto.AiSummaryDTO;
import com.example.aitools.dto.AiWorkSummaryDTO;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchUploadResponse;
import com.example.aitools.entity.BatchTask;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.handler.HandlerFactory;
import com.example.aitools.service.AiOfficeToolService;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.AiWorkSummaryVO;
import com.example.aitools.vo.BatchStatusVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/ai-office")
@RequiredArgsConstructor
@Slf4j
public class AiOfficeToolController {

    private final AiOfficeToolService aiOfficeToolService;
    private final AuthUtil authUtil;
    private final BatchTaskService batchTaskService;
    private final HandlerFactory handlerFactory;


    /** SSE 流式任务线程池（核心 8 / 最大 32 / 队列 100） */
    @Qualifier(ExecutorConfig.STREAM_EXECUTOR)
    private final Executor streamExecutor;

    /** 批量任务线程池（核心 4 / 最大 16 / 队列 50） */
    @Qualifier(ExecutorConfig.BATCH_EXECUTOR)
    private final Executor batchExecutor;

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
        return StreamHelper.stream(streamExecutor, emitter ->
                aiOfficeToolService.aiWorkSummaryStream(userId, dto.getContent(),
                        dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId(),
                        StreamHelper.asChunkConsumer(emitter)));
    }

    /**
     * 周报生成（SSE 流式）：逐块推送 AI 生成内容，内部统一管理历史记录
     */
    @PostMapping(value = "/weekly-report/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter weeklyReportStream(@Valid @RequestBody AiWorkSummaryDTO dto,
                                         HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return StreamHelper.stream(streamExecutor, emitter ->
                aiOfficeToolService.aiWeeklyReportStream(userId, dto.getContent(),
                        dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId(),
                        StreamHelper.asChunkConsumer(emitter)));
    }

    /**
     * 会议纪要（SSE 流式）：调 sse-text-single handler 委托 aiOfficeToolService.aiMeetingMinutesStream
     * <p>前端按 /meeting-minutes/decide-route 返回的 route 选择调 /stream (sse) 或 /json (sync)
     */
    @PostMapping(value = "/meeting-minutes/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter meetingMinutesStream(@Valid @RequestBody AiWorkSummaryDTO dto,
                                            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return StreamHelper.stream(streamExecutor, emitter ->
                handlerFactory.get("sse-text-single").handleSingleText("meeting-minutes", userId,
                        dto.getContent(),
                        dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId(),
                        StreamHelper.asChunkConsumer(emitter)));
    }

    /**
     * 会议纪要 AI 路由决策：让 AI 判断本次会议内容适合 SSE 流式还是 JSON 结构化
     * <p>前端按返回的 route 选择调 /stream 或 /json 端点
     */
    @PostMapping("/meeting-minutes/decide-route")
    public Result<String> meetingMinutesDecideRoute(@Valid @RequestBody AiWorkSummaryDTO dto) {
        // 路由决策同时考虑 promptFormat 和 promptGenerate（用户可能在提示词里明确要求 JSON/Markdown）
String route = handlerFactory.get("sse-text-single").decideRoute(
                "meeting-minutes", dto.getContent(), dto.getPromptFormat(), resolvePromptGenerate(dto), null);
        log.info("[meeting-minutes/decide-route] route={}", route);
        return Result.success(route);
    }

    /**
     * 会议纪要（JSON 同步）：调 json-single handler 委托 aiOfficeToolService 同步返回结构化 JSON
     * <p>前端发起请求后等待 AI 完全返回（必须等完整 JSON），一次性拿到结构化数据
     */
    @PostMapping("/meeting-minutes/json")
    public Result<String> meetingMinutesJson(@Valid @RequestBody AiWorkSummaryDTO dto,
                                             HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        String json = handlerFactory.get("json-single").handleSingleJson(
                "meeting-minutes", userId, dto.getContent(),
                dto.getPromptFormat(), resolvePromptGenerate(dto), dto.getPromptId());
        return Result.success(json);
    }

    /**
     * 文档重点提取（SSE 流式，multipart 上传文档）：解析文档后流式调 AI 提炼重点
     */
    @PostMapping(value = "/document-summary/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter documentSummaryStream(@RequestParam("file") MultipartFile file,
                                            AiSummaryDTO dto,
                                            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return StreamHelper.stream(streamExecutor, emitter ->
                aiOfficeToolService.aiDocumentSummaryStream(userId, file,
                        dto.getPromptFormat(), dto.getPromptGenerate(), dto.getPromptId(),
                        StreamHelper.asChunkConsumer(emitter)));
    }

    /**
     * 重点提取 · 纯文字输入（SSE 流式）。
     * <p>
     * 与 {@code /document-summary/stream}（文件输入）的区别：后者接收 MultipartFile 并在
     * 服务端解析；本端点面向「加工层只吃文本」的定位，文字由前端直接传入。
     * 文件请先用【文档提取】转为文字。
     */
    @PostMapping(value = "/document-summary/text-stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter documentSummaryTextStream(@RequestBody AiWorkSummaryDTO dto,
                                                HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return StreamHelper.stream(streamExecutor, emitter ->
                aiOfficeToolService.aiDocKeypointTextStream(userId,
                        dto.getContent(), dto.getPromptFormat(), dto.getPromptGenerate(), dto.getPromptId(),
                        StreamHelper.asChunkConsumer(emitter)));
    }

    /**
     * OCR 智能识别（SSE 流式，multipart 上传图片）：腾讯云 OCR 提取文字 → 调 AI 整理成结构化结果
     */
    @PostMapping(value = "/ocr-recognize/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter ocrRecognizeStream(@RequestParam("file") MultipartFile file,
                                         AiSummaryDTO dto,
                                         HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return StreamHelper.stream(streamExecutor, emitter ->
                aiOfficeToolService.aiOcrStream(userId, file,
                        dto.getPromptFormat(), dto.getPromptGenerate(), dto.getPromptId(),
                        StreamHelper.asChunkConsumer(emitter)));
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

    // 批量处理：upload 端点同步建任务 + 启异步线程跑批
    // service 内每文件完成立即 appendItem 入库，全部跑完 Controller 调 completeBatch
    // 前端用 GET /api/ai-office/batch/{batchId}/completed?since=N 轮询拉增量 items

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
        if (files.size() > Constants.BATCH_MAX_FILE_COUNT) {
            throw new com.example.aitools.exception.BusinessException(
                    "单次最多上传 " + Constants.BATCH_MAX_FILE_COUNT + " 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > Constants.BATCH_MAX_TOTAL_SIZE) {
            throw new com.example.aitools.exception.BusinessException(
                    "批量文件总大小超过 " + (Constants.BATCH_MAX_TOTAL_SIZE / 1024 / 1024) + "MB");
        }

        // 1) 同步建任务（HTTP 必须立即返回 batchId，前端拿去轮询）
        String batchId = batchTaskService.createTask(userId, "doc-keypoint-extract", files.size());
        log.info("[B2] 创建批量任务 batchId={} userId={} fileCount={}", batchId, userId, files.size());

        // 2) 同步把 MultipartFile 读到 byte[]（避开 Tomcat 异步线程跑批时临时文件已被清理）
        java.util.List<BatchFilePayload> payloads;
        try {
            payloads = files.stream().map(f -> {
                try {
                    return BatchFilePayload.from(f);
                } catch (java.io.IOException e) {
                    throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "读取文件失败，请重试");
                }
            }).toList();
        } catch (BusinessException e) {
            // 读文件失败：刚建的任务必须置为失败，否则永久停留在 PENDING，
            // 前端若已拿到 batchId 会一直轮询等不到终态，且任务表堆积孤儿数据
            log.error("[B2] 读取上传文件失败，任务置为失败 batchId={}", batchId, e);
            safeFailBatch(batchId, files.size());
            throw e;  /* P2-B4 */
        }

        // 3) 异步跑批（service 内每文件完即 appendItem，全部跑完 Controller 调 completeBatch）
        batchExecutor.execute(() -> {
            try {
                batchTaskService.markRunning(batchId);
                com.example.aitools.dto.BatchProcessResult result = aiOfficeToolService.aiDocumentSummaryBatchStream(
                        userId, payloads, promptFormat, promptGenerate, promptId, batchId);
                batchTaskService.completeBatch(batchId, result.getSuccessCount(), result.getFailCount(), result.getResultJson());
                log.info("[B2] 批量任务完成 batchId={} success={} fail={}", batchId, result.getSuccessCount(), result.getFailCount());
            } catch (Exception e) {
                log.error("[B2] 批量任务异常 batchId={}", batchId, e);
                try {
                    batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
                } catch (Exception ignore) {
                    // P2-B1: 不再静默吞，至少 log 出来便于排查（best-effort completeBatch，失败也不能让上层 catch 再抛）
                    log.error("[B2] 兜底 completeBatch 失败 batchId={}", batchId, ignore);
                }
            }
        });

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
                                     @RequestParam(value = "promptId", required = false) Long promptId,
                                     HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        if (files == null || files.isEmpty()) {
            throw new com.example.aitools.exception.BusinessException("请至少上传 1 个文件");
        }
        if (files.size() > Constants.BATCH_MAX_FILE_COUNT) {
            throw new com.example.aitools.exception.BusinessException(
                    "单次最多上传 " + Constants.BATCH_MAX_FILE_COUNT + " 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > Constants.BATCH_MAX_TOTAL_SIZE) {
            throw new com.example.aitools.exception.BusinessException(
                    "批量文件总大小超过 " + (Constants.BATCH_MAX_TOTAL_SIZE / 1024 / 1024) + "MB");
        }

        String batchId = batchTaskService.createTask(userId, "ocr-recognize", files.size());
        log.info("[OCR-B2] 创建批量任务 batchId={} userId={} fileCount={}", batchId, userId, files.size());

        // 同步阶段先把每个 MultipartFile 读到 byte[]（避开 Tomcat 异步线程跑批时临时文件已被清理）
        java.util.List<BatchFilePayload> payloads;
        try {
            payloads = files.stream().map(f -> {
                try {
                    return BatchFilePayload.from(f);
                } catch (java.io.IOException e) {
                    throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "读取文件失败，请重试");
                }
            }).toList();
        } catch (BusinessException e) {
            // 读文件失败：刚建的任务必须置为失败，否则永久停留在 PENDING，
            // 前端若已拿到 batchId 会一直轮询等不到终态，且任务表堆积孤儿数据
            log.error("[B2] 读取上传文件失败，任务置为失败 batchId={}", batchId, e);
            safeFailBatch(batchId, files.size());
            throw e;  /* P2-B4 */
        }

        batchExecutor.execute(() -> {
            try {
                batchTaskService.markRunning(batchId);
                com.example.aitools.dto.BatchProcessResult result = aiOfficeToolService.aiOcrBatchStream(
                        userId, payloads, promptFormat, promptGenerate, promptId, batchId);
                batchTaskService.completeBatch(batchId, result.getSuccessCount(), result.getFailCount(), result.getResultJson());
                log.info("[OCR-B2] 完成 batchId={} success={} fail={}", batchId, result.getSuccessCount(), result.getFailCount());
            } catch (Exception e) {
                log.error("[OCR-B2] 异常 batchId={}", batchId, e);
                try {
                    batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
                } catch (Exception ignore) {
                    // P2-B1: 不再静默吞，至少 log 出来便于排查（best-effort completeBatch，失败也不能让上层 catch 再抛）
                    log.error("[B2] 兜底 completeBatch 失败 batchId={}", batchId, ignore);
                }
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
        // P0 用户反馈：这是 SSE 补发端点（断线重连后补 result_summary），应走 streamExecutor
        // 业务含义：SSE 流式输出，与批量任务"执行处理"是两件事
        streamExecutor.execute(() -> batchTaskService.subscribeProgress(task, emitter));
        return emitter;
    }

    /** 兼容旧前端：批量任务状态查询（带 items 全量） */
    @GetMapping("/document-summary/batch-status/{batchId}")
    public Result<BatchStatusVO> batchStatus(@PathVariable("batchId") String batchId, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        BatchTask task = batchTaskService.getByBatchId(batchId);
        if (task == null) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "任务不存在或已过期");
        }
        if (!task.getUserId().equals(userId)) {
            return Result.fail(ResultCode.FORBIDDEN.getCode(), "无权访问此任务");
        }
        // 旧端点返回全部 items（since=0）
        return Result.success(BatchStatusVO.from(task, 0));
    }

    /**
     * 兜底把批量任务标记为失败（用于「已建任务但后续同步步骤失败」的场景，避免孤儿 PENDING 任务）。
     * <p>自身异常只记日志，绝不掩盖调用处要抛出的原始异常。
     */
    private void safeFailBatch(String batchId, int fileCount) {
        try {
            batchTaskService.completeBatch(batchId, 0, fileCount, "[]");
        } catch (Exception e) {
            log.error("[B2] 兜底标记任务失败时出错 batchId={}", batchId, e);
        }
    }
}
