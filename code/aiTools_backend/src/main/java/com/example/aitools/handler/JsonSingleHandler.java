package com.example.aitools.handler;

import com.example.aitools.common.ResultCode;
import com.example.aitools.ai.AiClient;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchProcessResult;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.HistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单文件 → JSON 结构化（meeting-minutes 第一试点）
 * <p>
 * 流程：
 * <ol>
 *     <li>resolve 系统提示词（format + generate）</li>
 *     <li>组装 system + user prompt</li>
 *     <li>同步调 AiClient.chat 拿到完整响应（JSON 字符串）</li>
 *     <li>从响应里提取 JSON 子串（容忍 AI 输出前后有废话）</li>
 *     <li>Jackson 解析 + 校验结构（数组 + 每项含 key/title/type/content）</li>
 *     <li>失败则 silent 重试 N 次（默认 2）</li>
 *     <li>最终成功 → 写历史（content = 校验后的 JSON 字符串）</li>
 * </ol>
 */
@Slf4j
@Component("json-single")
@RequiredArgsConstructor
public class JsonSingleHandler implements AiToolHandler {

    private final AiClient aiClient;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final HistoryService historyService;
    private final ObjectMapper objectMapper;

    @Value("${ai.handler.json.max-retry:2}")
    private int maxRetry;

    /** 容忍 AI 在 JSON 前后输出解释性文本，用正则抠出 JSON 子串 */
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile(
            "\\[\\s*\\{.*?\\}\\s*(?:,\\s*\\{.*?\\})*\\s*\\]",
            Pattern.DOTALL
    );

    @Override
    public String handleSingleText(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId,
                                   java.util.function.Consumer<String> onChunk) {
        throw new UnsupportedOperationException(
                "json-single handler 不支持 SSE 长文本输出，请用 sse-text-single");
    }

    @Override
    public String handleSingleJson(String toolCode, Long userId, String content,
                                   String promptFormat, String promptGenerate, Long promptId) {
        long start = System.currentTimeMillis();
        Long toolId = aiPromptTemplateService.findToolIdByCode(toolCode);
        StringBuilder lastErr = new StringBuilder();

        // resolve 系统提示词
        String formatPrompt = aiPromptTemplateService.resolvePrompt(
                promptFormat, promptId, "format", toolCode);
        String generatePrompt = aiPromptTemplateService.resolvePrompt(
                promptGenerate, promptId, "generate", toolCode);
        String systemPrompt = buildSystemPrompt(formatPrompt, content);

        Long historyId = historyService.createPendingHistory(
                userId, toolId, null, toolCode,
                content == null ? "" : content.substring(0, Math.min(200, content.length())));

        // 尝试 N+1 次（首次 + retry）
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                String raw = aiClient.chat(systemPrompt, generatePrompt + "\n\n会议内容：\n" + content);
                String jsonStr = extractJson(raw);
                JsonNode parsed = validateAndParse(jsonStr, toolCode);

                String normalizedJson = objectMapper.writeValueAsString(parsed);
                long duration = System.currentTimeMillis() - start;
                historyService.completeHistory(historyId, normalizedJson, (int) duration);
                log.info("[json-single] tool={} attempt={} ok duration={}ms", toolCode, attempt, duration);
                return normalizedJson;
            } catch (Exception e) {
                lastErr.setLength(0);
                lastErr.append(e.getMessage());
                log.warn("[json-single] tool={} attempt={} failed: {}", toolCode, attempt, e.getMessage());
            }
        }
        historyService.failHistory(historyId, "JSON 解析重试耗尽：" + lastErr);
        throw new BusinessException(ResultCode.AI_TOOL_FAILED.getCode(), "AI 返回内容格式异常，请重试");
    }

    @Override
    public BatchProcessResult handleBatchText(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        throw new UnsupportedOperationException("json-single 不支持批量，请用 json-batch");
    }

    @Override
    public BatchProcessResult handleBatchJson(String toolCode, Long userId, List<BatchFilePayload> files,
                                             String promptFormat, String promptGenerate, Long promptId,
                                             String batchId) {
        throw new UnsupportedOperationException("json-single 不支持批量，请用 json-batch");
    }

    // ============ 私有方法 ============

    /** 与 AiOfficeToolServiceImpl.buildSystemPrompt 行为一致：format 原样下发 */
    private String buildSystemPrompt(String formatPrompt, String content) {
        return formatPrompt == null ? "" : formatPrompt;
    }

    /** 容忍 AI 输出前后废话：从响应中抠 JSON 数组子串 */
    private String extractJson(String raw) {
        if (raw == null) throw new BusinessException(ResultCode.AI_EMPTY_RESULT.getCode(), "AI 未返回内容，请重试");
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }
        Matcher m = JSON_ARRAY_PATTERN.matcher(trimmed);
        if (m.find()) {
            return m.group();
        }
        throw new BusinessException(ResultCode.AI_TOOL_FAILED.getCode(), "AI 返回内容格式异常，请重试");
    }

    /** 校验：必须是非空数组，每项含 key/title/type/content */
    private JsonNode validateAndParse(String jsonStr, String toolCode) {
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonStr);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.AI_TOOL_FAILED.getCode(), "AI 返回内容解析失败，请重试");
        }
        if (!root.isArray()) {
            throw new BusinessException(ResultCode.AI_TOOL_FAILED.getCode(), "AI 返回内容格式异常，请重试");
        }
        if (root.isEmpty()) {
            throw new BusinessException(ResultCode.AI_EMPTY_RESULT.getCode(), "AI 未返回有效内容，请重试");
        }
        for (JsonNode item : root) {
            if (!item.isObject()) {
                throw new BusinessException(ResultCode.AI_TOOL_FAILED.getCode(), "AI 返回内容格式异常，请重试");
            }
            for (String field : new String[]{"key", "title", "type", "content"}) {
                if (item.get(field) == null) {
                    throw new BusinessException(
                            "JSON 数组元素缺少必填字段：" + field + "（toolCode=" + toolCode + "）");
                }
            }
            String type = item.get("type").asText();
            if (!isValidType(type)) {
                throw new BusinessException(ResultCode.AI_TOOL_FAILED.getCode(), "AI 返回内容格式异常，请重试");
            }
        }
        return root;
    }

    /** 与 sys_ai_prompt.id=8（format）声明一致：text / list / todo / table / rich */
    private boolean isValidType(String type) {
        return "text".equals(type) || "list".equals(type) || "todo".equals(type)
                || "table".equals(type) || "rich".equals(type);
    }
}