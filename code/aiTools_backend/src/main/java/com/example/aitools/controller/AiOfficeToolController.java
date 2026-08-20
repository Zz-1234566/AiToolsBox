package com.example.aitools.controller;

import com.example.aitools.common.Result;
import com.example.aitools.dto.AiSummaryDTO;
import com.example.aitools.dto.AiWorkSummaryDTO;
import com.example.aitools.service.AiOfficeToolService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.AiWorkSummaryVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai-office")
@RequiredArgsConstructor
public class AiOfficeToolController {

    private final AiOfficeToolService aiOfficeToolService;
    private final AuthUtil authUtil;

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
}
