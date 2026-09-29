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
 * 多文件 → SSE 长文本（ai-file-reader 等批量路径）
 * <p>
 * 委托 AiOfficeToolService 现有批量方法：aiDocumentSummaryBatchStream / aiOcrBatchStream / aiFileReaderBatchStream
 * 注意：ai-file-reader 入口参数形态不同（仅 prompt，无 format/generate）
 */
@Slf4j
@Component("sse-text-batch")
@RequiredArgsConstructor
public class SseTextBatchHandler implements AiToolHandler {

    private final AiOfficeToolService aiOfficeToolService;

    @Override
    public String handleSingleText(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId,
                                   Consumer<String> onChunk) {
        throw new UnsupportedOperationException("sse-text-batch 不支持单文件，请用 sse-text-single");
    }

    @Override
    public String handleSingleJson(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId) {
        throw new UnsupportedOperationException("sse-text-batch 不支持 JSON，请用 json-single");
    }

    @Override
    public BatchProcessResult handleBatchText(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        return switch (toolCode) {
            case "doc-keypoint-extract" -> aiOfficeToolService.aiDocumentSummaryBatchStream(
                    userId, files, promptFormat, promptGenerate, promptId, batchId);
            case "ocr-recognize" -> aiOfficeToolService.aiOcrBatchStream(
                    userId, files, promptFormat, promptGenerate, promptId, batchId);
            case "ai-file-reader" -> aiOfficeToolService.aiFileReaderBatchStream(
                    userId, files, promptFormat, batchId);
            default -> throw new BusinessException(
                    "toolCode=" + toolCode + " 不支持 sse-text-batch 处理方式");
        };
    }

    @Override
    public BatchProcessResult handleBatchJson(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        throw new UnsupportedOperationException("sse-text-batch 不支持 JSON 批量，请用 json-batch");
    }
}