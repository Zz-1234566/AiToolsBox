package com.example.aitools.handler;

import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchProcessResult;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.AiOfficeToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 单文件 → SSE 长文本（现有 5 个工具的真实路径）
 * <p>
 * 委托 AiOfficeToolService 现有方法，不重写业务逻辑。Controller 通过 Factory 按 bean name "sse-text-single" 取。
 * <p>
 * 支持工具：work-summary / doc-keypoint-extract / weekly-report / ocr-recognize / ai-file-reader 的单文件路径
 */
@Slf4j
@Component("sse-text-single")
@RequiredArgsConstructor
public class SseTextSingleHandler implements AiToolHandler {

    private final AiOfficeToolService aiOfficeToolService;

    @Override
    public String handleSingleText(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId,
                                   Consumer<String> onChunk) {
        // 现有 Service 用 if-else 按 toolCode 分派；这里改用统一的 switch
        return switch (toolCode) {
            case "work-summary" -> aiOfficeToolService.aiWorkSummaryStream(
                    userId, content, promptFormat, promptGenerate, promptId, onChunk);
            case "weekly-report" -> aiOfficeToolService.aiWeeklyReportStream(
                    userId, content, promptFormat, promptGenerate, promptId, onChunk);
            case "meeting-minutes" -> aiOfficeToolService.aiMeetingMinutesStream(
                    userId, content, promptFormat, promptGenerate, promptId, onChunk);
            case "ocr-recognize" -> throw new BusinessException(
                    "ocr-recognize 单文件入口在 Controller 单独处理（要先调 OCR），请改用 ocr/stream 端点");
            case "doc-keypoint-extract" -> throw new BusinessException(
                    "doc-keypoint-extract 单文件入口在 Controller 单独处理（要先解析文档），请改用 document-summary/stream 端点");
            default -> throw new BusinessException(
                    "toolCode=" + toolCode + " 不支持 sse-text-single 处理方式");
        };
    }

    @Override
    public String handleSingleJson(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId) {
        throw new UnsupportedOperationException(
                "sse-text-single handler 不支持 JSON 输出，请用 json-single");
    }

    @Override
    public BatchProcessResult handleBatchText(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        throw new UnsupportedOperationException(
                "sse-text-single handler 不支持批量，请用 sse-text-batch");
    }

    @Override
    public BatchProcessResult handleBatchJson(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        throw new UnsupportedOperationException(
                "sse-text-single handler 不支持批量 JSON，请用 json-batch");
    }
}