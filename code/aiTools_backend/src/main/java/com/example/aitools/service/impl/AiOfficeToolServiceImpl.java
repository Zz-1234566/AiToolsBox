package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.ai.AiClient;
import com.example.aitools.common.Constants;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.entity.AiPrompt;
import com.example.aitools.entity.AiTool;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.service.AiFileReaderService;
import com.example.aitools.service.AiOfficeToolService;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.service.HistoryService;
import com.example.aitools.service.OcrService;
import com.example.aitools.service.document.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOfficeToolServiceImpl implements AiOfficeToolService {

    /** 工作总结工具编码 */
    private static final String TOOL_CODE_WORK_SUMMARY = "work-summary";

    /** 文档重点提取工具编码 */
    private static final String TOOL_CODE_DOC_SUMMARY = "doc-keypoint-extract";

    /** 周报生成工具编码 */
    private static final String TOOL_CODE_WEEKLY_REPORT = "weekly-report";

    /** 会议纪要工具编码 */
    private static final String TOOL_CODE_MEETING_MINUTES = "meeting-minutes";

    /** OCR 智能识别工具编码 */
    private static final String TOOL_CODE_AI_OCR = "ocr-recognize";

    /** AI 文件解读工具编码 */
    private static final String TOOL_CODE_AI_FILE_READER = "ai-file-reader";

    /** 提示词用途：格式 */
    private static final String PROMPT_USE_FORMAT = "format";

    /** 提示词用途：生成内容 */
    private static final String PROMPT_USE_GENERATE = "generate";

    private final AiClient aiClient;
    private final HistoryService historyService;
    private final AiToolMapper aiToolMapper;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final DocumentParser documentParser;
    private final OcrService ocrService;
    private final BatchTaskService batchTaskService;
    private final AiFileReaderService aiFileReaderService;

    /**
     * 工作总结
     */
    @Override
    public String aiWorkSummary(Long userId, String content, String promptFormat, String promptGenerate, Long promptId) {
        long start = System.currentTimeMillis();
        // 查询工作总结工具 ID（按 tool_code = work-summary）
        Long toolId = findToolIdByCode(TOOL_CODE_WORK_SUMMARY);
        try {
            String result = aiTextProcess(TOOL_CODE_WORK_SUMMARY, content, promptFormat, promptGenerate, promptId);
            long duration = System.currentTimeMillis() - start;
            historyService.record(userId, toolId, null, TOOL_CODE_WORK_SUMMARY, content, result, 1, (int) duration);
            return result;
        } catch (Exception e) {
            historyService.record(userId, toolId, null, TOOL_CODE_WORK_SUMMARY, content, null, 0, null);
            throw e;
        }
    }

    /**
     * 流式工作总结（SSE），内部统一管理历史记录
     */
    @Override
    public String aiWorkSummaryStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk) {
        return aiTextProcessStream(userId, TOOL_CODE_WORK_SUMMARY, content, promptFormat, promptGenerate, promptId, onChunk);
    }

    /**
     * 流式周报生成（SSE），内部统一管理历史记录
     */
    @Override
    public String aiWeeklyReportStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk) {
        return aiTextProcessStream(userId, TOOL_CODE_WEEKLY_REPORT, content, promptFormat, promptGenerate, promptId, onChunk);
    }

    /**
     * 流式会议纪要（SSE），内部统一管理历史记录
     */
    @Override
    public String aiMeetingMinutesStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk) {
        return aiTextProcessStream(userId, TOOL_CODE_MEETING_MINUTES, content, promptFormat, promptGenerate, promptId, onChunk);
    }

    /**
     * 流式文档重点提取（SSE）：解析上传文档后调 AI 提炼重点，成功/失败均记录历史。
     * 文档作为输入以"上传文档：文件名"占位记入历史 input，不与文件子表强关联。
     */
    @Override
    public String aiDocumentSummaryStream(Long userId, MultipartFile file, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk) {
        // 文档拦截：空文件直接抛业务异常，不写历史
        if (file == null || file.getSize() < 0 || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new BusinessException("请上传文档文件");
        }

        long start = System.currentTimeMillis();
        // 查询文档重点提取工具 ID（按 tool_code = doc-keypoint-extract）
        Long toolId = findToolIdByCode(TOOL_CODE_DOC_SUMMARY);
        String fileName = file.getOriginalFilename();
        // 先创建"处理中"历史记录，保证 AI 被调用后必有记录（文档名占位为输入）
        Long historyId = historyService.createPendingHistory(userId, toolId, null, TOOL_CODE_DOC_SUMMARY, "上传文档：" + fileName);
        StringBuilder sb = new StringBuilder();
        try {
            String docText = documentParser.parse(file);
            String formatPrompt = resolvePrompt(promptFormat, promptId, PROMPT_USE_FORMAT, TOOL_CODE_DOC_SUMMARY);
            String generatePrompt = resolvePrompt(promptGenerate, promptId, PROMPT_USE_GENERATE, TOOL_CODE_DOC_SUMMARY);
            validatePrompts(formatPrompt, generatePrompt);
            String systemPrompt = buildSystemPrompt(formatPrompt, docText);
            String userPrompt = generatePrompt + "\n\n文档内容：\n" + docText;
            aiClient.chatStream(systemPrompt, userPrompt, chunk -> {
                sb.append(chunk);
                onChunk.accept(chunk);
            });
            long duration = System.currentTimeMillis() - start;
            historyService.completeHistory(historyId, sb.toString(), (int) duration);
            return sb.toString();
        } catch (Exception e) {
            historyService.failHistory(historyId, e.getMessage());
            throw e;
        }
    }

    /**
     * 流式 OCR 智能识别（SSE）：上传图片 → 腾讯云 OCR 提取文字 → 调 AI 整理成结构化结果
     * 复用 aiTextProcessStream：把 OCR 出的文字当 content，prompt 走 ai-ocr 系统提示词
     */
    @Override
    public String aiOcrStream(Long userId, MultipartFile file, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk) {
        // 图片拦截
        if (file == null || file.getSize() < 0 || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new BusinessException("请上传图片文件");
        }

        long start = System.currentTimeMillis();
        Long toolId = findToolIdByCode(TOOL_CODE_AI_OCR);
        String fileName = file.getOriginalFilename();
        // 走通用方法：先 OCR 出文字作为 content，再走 aiTextProcessStream 流式输出
        String ocrText = ocrService.recognizeText(file);
        if (ocrText == null || ocrText.isBlank()) {
            throw new BusinessException("OCR 未识别出文字，请换一张更清晰的图片");
        }
        // 输入用 OCR 结果 + 文件名作为占位，便于历史回溯
        String input = "上传图片：" + fileName + "\n\nOCR 识别结果：\n" + ocrText;
        return aiTextProcessStream(userId, TOOL_CODE_AI_OCR, input, promptFormat, promptGenerate, promptId, onChunk);
    }

    /**
     * 批量 OCR 智能识别（B2）：逐文件 OCR + AI 整理
     * <p>单文件完成立即 appendItem 入库，最后由 Controller 调 completeBatch 写终态。
     * 单文件失败不影响整体。
     * @param batchId 批量任务 ID（必传）
     */
    @Override
    public com.example.aitools.dto.BatchProcessResult aiOcrBatchStream(Long userId, List<BatchFilePayload> files,
                                                String promptFormat, String promptGenerate, String batchId) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请至少上传 1 个文件");
        }
        if (files.size() > 10) {
            throw new BusinessException("单次最多上传 10 个文件");
        }

        Long toolId = findToolIdByCode(TOOL_CODE_AI_OCR);
        int successCount = 0;
        int failCount = 0;
        int processedIndex = 0;
        StringBuilder resultJson = new StringBuilder("[");
        log.info("[B2-OCR] 开始 userId={} fileCount={} batchId={}", userId, files.size(), batchId);

        for (int i = 0; i < files.size(); i++) {
            BatchFilePayload payload = files.get(i);
            String fileName = (payload != null && payload.getOriginalFilename() != null) ? payload.getOriginalFilename() : "未命名-" + (i + 1);
            String fileResultJson;
            boolean fileOk = false;
            String fileOutput = "";

            if (payload == null || payload.getContent() == null || payload.getContent().length == 0) {
                String err = "文件为空";
                failCount++;
                fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"failed\",\"errorMsg\":\"" + escapeJson(err) + "\",\"output\":\"\"}";
            } else {
                long start = System.currentTimeMillis();
                Long historyId = historyService.createPendingHistory(userId, toolId, null,
                        TOOL_CODE_AI_OCR, "批量上传（" + (i + 1) + "/" + files.size() + "）：" + fileName);
                StringBuilder fileOut = new StringBuilder();
                try {
                    String ocrText = ocrService.recognizeBytes(payload.getContent(), fileName);
                    if (ocrText == null || ocrText.isBlank()) {
                        throw new BusinessException("OCR 未识别出文字");
                    }
                    String input = "上传图片：" + fileName + "\n\nOCR 识别结果：\n" + ocrText;
                    aiTextProcessStream(userId, TOOL_CODE_AI_OCR, input, promptFormat, promptGenerate, null,
                            fileOut::append);
                    long duration = System.currentTimeMillis() - start;
                    fileOutput = fileOut.toString();
                    historyService.completeHistory(historyId, fileOutput, (int) duration);
                    successCount++;
                    fileOk = true;
                    fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"ok\",\"costMs\":" + duration + ",\"output\":\"" + escapeJson(fileOutput) + "\"}";
                } catch (Exception e) {
                    historyService.failHistory(historyId, e.getMessage());
                    String errMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    failCount++;
                    fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"failed\",\"costMs\":" + (System.currentTimeMillis() - start) + ",\"errorMsg\":\"" + escapeJson(errMsg) + "\",\"output\":\"\"}";
                    log.warn("[B2-OCR] 单文件失败 userId={} fileName={} err={}", userId, fileName, errMsg);
                }
            }

            if (i > 0) resultJson.append(",");
            resultJson.append(fileResultJson);

            try {
                batchTaskService.appendItem(batchId, fileResultJson, fileOk);
            } catch (Exception e) {
                log.error("[B2-OCR] appendItem 失败 batchId={} index={}", batchId, i + 1, e);
            }
            processedIndex++;
        }

        resultJson.append("]");
        log.info("[B2-OCR] 完成 userId={} batchId={} success={} fail={}", userId, batchId, successCount, failCount);

        return new com.example.aitools.dto.BatchProcessResult(successCount, failCount, processedIndex, resultJson.toString(), "");
    }

    /**
     * 批量流式文档重点提取（B2）：逐文件解析 + AI 整理
     * <p>单文件完成后立即 appendItem 入库（前端轮询可见），最后由 Controller 调 completeBatch 写终态。
     * 单文件失败不影响整体。
     * @param batchId 批量任务 ID（必传）
     */
    @Override
    public com.example.aitools.dto.BatchProcessResult aiDocumentSummaryBatchStream(Long userId, List<BatchFilePayload> files,
                                              String promptFormat, String promptGenerate, Long promptId,
                                              String batchId) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请至少上传 1 个文件");
        }
        if (files.size() > 10) {
            throw new BusinessException("单次最多上传 10 个文件");
        }

        Long toolId = findToolIdByCode(TOOL_CODE_DOC_SUMMARY);
        int successCount = 0;
        int failCount = 0;
        int processedIndex = 0;
        StringBuilder resultJson = new StringBuilder("[");
        log.info("[B2-DOC] 开始 userId={} fileCount={} batchId={}", userId, files.size(), batchId);

        for (int i = 0; i < files.size(); i++) {
            BatchFilePayload payload = files.get(i);
            String fileName = (payload != null && payload.getOriginalFilename() != null) ? payload.getOriginalFilename() : "未命名-" + (i + 1);
            String fileResultJson;
            boolean fileOk = false;
            String fileOutput = "";

            if (payload == null || payload.getContent() == null || payload.getContent().length == 0) {
                String err = "文件为空";
                failCount++;
                fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"failed\",\"errorMsg\":\"" + escapeJson(err) + "\",\"output\":\"\"}";
            } else {
                long start = System.currentTimeMillis();
                Long historyId = historyService.createPendingHistory(userId, toolId, null,
                        TOOL_CODE_DOC_SUMMARY, "批量上传（" + (i + 1) + "/" + files.size() + "）：" + fileName);
                StringBuilder fileOut = new StringBuilder();
                try {
                    String docText = documentParser.parse(payload.toMultipartFile());
                    String formatPrompt = resolvePrompt(promptFormat, promptId, PROMPT_USE_FORMAT, TOOL_CODE_DOC_SUMMARY);
                    String generatePrompt = resolvePrompt(promptGenerate, promptId, PROMPT_USE_GENERATE, TOOL_CODE_DOC_SUMMARY);
                    validatePrompts(formatPrompt, generatePrompt);
                    String systemPrompt = buildSystemPrompt(formatPrompt, docText);
                    String userPrompt = generatePrompt + "\n\n文档内容：\n" + docText;
                    aiClient.chatStream(systemPrompt, userPrompt, fileOut::append);
                    long duration = System.currentTimeMillis() - start;
                    fileOutput = fileOut.toString();
                    historyService.completeHistory(historyId, fileOutput, (int) duration);
                    successCount++;
                    fileOk = true;
                    fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"ok\",\"costMs\":" + duration + ",\"output\":\"" + escapeJson(fileOutput) + "\"}";
                } catch (Exception e) {
                    historyService.failHistory(historyId, e.getMessage());
                    String errMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    failCount++;
                    fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"failed\",\"costMs\":" + (System.currentTimeMillis() - start) + ",\"errorMsg\":\"" + escapeJson(errMsg) + "\",\"output\":\"\"}";
                    log.warn("[B2-DOC] 单文件失败 userId={} fileName={} err={}", userId, fileName, errMsg);
                }
            }

            if (i > 0) resultJson.append(",");
            resultJson.append(fileResultJson);

            try {
                batchTaskService.appendItem(batchId, fileResultJson, fileOk);
            } catch (Exception e) {
                log.error("[B2-DOC] appendItem 失败 batchId={} index={}", batchId, i + 1, e);
            }
            processedIndex++;
        }

        resultJson.append("]");
        log.info("[B2-DOC] 完成 userId={} batchId={} success={} fail={}", userId, batchId, successCount, failCount);

        return new com.example.aitools.dto.BatchProcessResult(successCount, failCount, processedIndex, resultJson.toString(), "");
    }

    /** JSON 字符串转义 */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * 通用文本处理（非流式）：凑齐 1 个 format + 1 个 generate 后调用 AI（不校验，由带 userId 的入口方法负责）
     */
    private String aiTextProcess(String toolCode, String content, String promptFormat, String promptGenerate, Long promptId) {
        String formatPrompt = resolvePrompt(promptFormat, promptId, PROMPT_USE_FORMAT, toolCode);
        String generatePrompt = resolvePrompt(promptGenerate, promptId, PROMPT_USE_GENERATE, toolCode);
        validatePrompts(formatPrompt, generatePrompt);
        String systemPrompt = buildSystemPrompt(formatPrompt, content);
        String userPrompt = buildUserPrompt(generatePrompt, content);
        return aiClient.chat(systemPrompt, userPrompt);
    }

    /**
     * 通用文本处理流式（工作总结/周报/会议纪要共用）：先建"处理中"历史，流式调 AI，
     * 成功 completeHistory、失败 failHistory
     */
    private String aiTextProcessStream(Long userId, String toolCode, String content,
            String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk) {
        long start = System.currentTimeMillis();
        Long toolId = findToolIdByCode(toolCode);
        Long historyId = historyService.createPendingHistory(userId, toolId, null, toolCode, content);
        StringBuilder sb = new StringBuilder();
        try {
            String formatPrompt = resolvePrompt(promptFormat, promptId, PROMPT_USE_FORMAT, toolCode);
            String generatePrompt = resolvePrompt(promptGenerate, promptId, PROMPT_USE_GENERATE, toolCode);
            validatePrompts(formatPrompt, generatePrompt);
            String systemPrompt = buildSystemPrompt(formatPrompt, content);
            String userPrompt = buildUserPrompt(generatePrompt, content);
            aiClient.chatStream(systemPrompt, userPrompt, chunk -> {
                sb.append(chunk);
                onChunk.accept(chunk);
            });
            long duration = System.currentTimeMillis() - start;
            historyService.completeHistory(historyId, sb.toString(), (int) duration);
            return sb.toString();
        } catch (Exception e) {
            historyService.failHistory(historyId, e.getMessage());
            throw e;
        }
    }

    /**
     * 解析指定用途的提示词：用户自定义优先，其次系统提示词（promptId 选中且用途匹配时），
     * 最后回退到该系统默认提示词；均无时返回 null，由校验兜底
     */
    private String resolvePrompt(String userProvided, Long promptId, String promptUse, String toolCode) {
        if (userProvided != null && !userProvided.isBlank()) {
            return userProvided.trim();
        }
        if (promptId != null) {
            AiPrompt selected = aiPromptTemplateService.getById(promptId);
            if (promptUse.equals(selected.getPromptUse())) {
                return selected.getPromptContent();
            }
            // 选中的系统提示词用途不匹配，回退到该系统默认
        }
        AiPrompt defaultPrompt = aiPromptTemplateService.getDefaultByUse(toolCode, promptUse);
        return defaultPrompt == null ? null : defaultPrompt.getPromptContent();
    }

    /**
     * 校验：format 与 generate 最终都必须有（用户自定义或系统默认均可）
     */
    private void validatePrompts(String formatPrompt, String generatePrompt) {
        if ((formatPrompt == null || formatPrompt.isBlank())
                || (generatePrompt == null || generatePrompt.isBlank())) {
            throw new BusinessException("缺少格式提示词或生成内容提示词");
        }
    }

    /**
     * 拼接 system 提示词：format 提示词即系统指令（规定输出结构），
     * 含 %s 占位符时用工作内容填充（兼容旧模板），否则原样下发
     */
    private String buildSystemPrompt(String formatPrompt, String content) {
        if (formatPrompt == null || formatPrompt.isBlank()) {
            return null;
        }
        // format prompt 原样下发，不替换 %s（避免多个占位符被同一段 OCR 文字重复填充破坏 prompt 结构）
        log.info("[DEBUG-PROMPT] systemPrompt 长度={} 前200字={}", formatPrompt.length(), formatPrompt.substring(0, Math.min(200, formatPrompt.length())).replace("\n", "\\n"));
        return formatPrompt;
    }

    /**
     * 拼接 user 提示词：generate 提示词（规定生成内容）+ 实际工作记录
     * 含 %s 占位符时用工作内容填充（与 buildSystemPrompt 行为一致）
     */
    private String buildUserPrompt(String generatePrompt, String content) {
        // generate prompt 原样下发（不替换 %s），末尾追加"原文"段做兜底：
        // 即使用户 prompt 不规范，AI 也能从"原文"段拿到 OCR/工作内容并提取
        String result = (generatePrompt != null ? generatePrompt : "")
                + "\n\n原文：\n" + content;
        log.info("[DEBUG-PROMPT] userPrompt 长度={} 前300字={}", result.length(), result.substring(0, Math.min(300, result.length())).replace("\n", "\\n"));
        return result;
    }

    /**
     * 按工具编码查工具ID（查不到返回 null，工具表可能未初始化）
     */
    private Long findToolIdByCode(String toolCode) {
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiTool::getToolCode, toolCode)
                .eq(AiTool::getDr, Constants.DR_NORMAL)
                .last("LIMIT 1");
        AiTool tool = aiToolMapper.selectOne(wrapper);
        return tool == null ? null : tool.getId();
    }

    @Override
    public com.example.aitools.dto.BatchProcessResult aiFileReaderBatchStream(Long userId, List<BatchFilePayload> files,
                                                                            String prompt, String batchId) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请至少上传 1 个文件");
        }
        if (files.size() > 10) {
            throw new BusinessException("单次最多上传 10 个文件");
        }

        int successCount = 0;
        int failCount = 0;
        StringBuilder resultJson = new StringBuilder("[");
        log.info("[B2-FILE] 开始 userId={} fileCount={} batchId={}", userId, files.size(), batchId);

        for (int i = 0; i < files.size(); i++) {
            BatchFilePayload payload = files.get(i);
            String fileName = (payload != null && payload.getOriginalFilename() != null)
                    ? payload.getOriginalFilename() : "未命名-" + (i + 1);
            String fileResultJson;
            boolean fileOk = false;

            if (payload == null || payload.getContent() == null || payload.getContent().length == 0) {
                failCount++;
                fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"failed\",\"errorMsg\":\"文件为空\",\"output\":\"\"}";
            } else {
                long start = System.currentTimeMillis();
                try {
                    String output = aiFileReaderService.readFile(payload, prompt);
                    long duration = System.currentTimeMillis() - start;
                    successCount++;
                    fileOk = true;
                    fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"ok\",\"costMs\":" + duration + ",\"output\":\"" + escapeJson(output) + "\"}";
                } catch (Exception e) {
                    String errMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    failCount++;
                    fileResultJson = "{\"index\":" + (i + 1) + ",\"fileName\":\"" + escapeJson(fileName) + "\",\"status\":\"failed\",\"errorMsg\":\"" + escapeJson(errMsg) + "\",\"output\":\"\"}";
                    log.warn("[B2-FILE] 单文件失败 userId={} fileName={} err={}", userId, fileName, errMsg);
                }
            }

            if (i > 0) resultJson.append(",");
            resultJson.append(fileResultJson);

            try {
                batchTaskService.appendItem(batchId, fileResultJson, fileOk);
            } catch (Exception e) {
                log.error("[B2-FILE] appendItem 失败 batchId={} index={}", batchId, i + 1, e);
            }
        }

        resultJson.append("]");
        log.info("[B2-FILE] 完成 userId={} batchId={} success={} fail={}", userId, batchId, successCount, failCount);
        return new com.example.aitools.dto.BatchProcessResult(successCount, failCount, files.size(), resultJson.toString(), "");
    }
}
