package com.example.aitools.handler;

import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchProcessResult;
import com.example.aitools.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 多文件 → JSON 结构化（当前无工具实际使用，留扩展位）
 * <p>
 * 行为：逐文件复用 json-single 的解析+重试逻辑，把每文件结果写到 batchId 增量项里。
 * 当前 throw 暂未启用 —— 等真正需要 JSON 批量的工具（invoice-recognize 等）上线时再实现。
 */
@Slf4j
@Component("json-batch")
public class JsonBatchHandler implements AiToolHandler {

    @Override
    public String handleSingleText(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId,
                                   Consumer<String> onChunk) {
        throw new UnsupportedOperationException("json-batch 不支持单文件，请用 json-single");
    }

    @Override
    public String handleSingleJson(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId) {
        throw new UnsupportedOperationException("json-batch 不支持单文件 JSON，请用 json-single");
    }

    @Override
    public BatchProcessResult handleBatchText(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        throw new UnsupportedOperationException("json-batch 不支持长文本批量，请用 sse-text-batch");
    }

    @Override
    public BatchProcessResult handleBatchJson(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        // 未来真正需要 JSON 批量的工具上线时实现：
        // 1) 逐文件调用 jsonSingleHandler.handleSingleJson
        // 2) 用 BatchTaskService.appendItem 增量入库
        // 3) 统计 success/fail
        throw new BusinessException(
                "toolCode=" + toolCode + " 当前未启用 json-batch 批量 JSON 处理方式（无对应工具）");
    }
}