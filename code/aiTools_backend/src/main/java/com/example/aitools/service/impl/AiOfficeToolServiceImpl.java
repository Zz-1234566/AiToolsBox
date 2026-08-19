package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.ai.AiClient;
import com.example.aitools.common.Constants;
import com.example.aitools.entity.AiPrompt;
import com.example.aitools.entity.AiTool;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.service.AiOfficeToolService;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.HistoryService;
import com.example.aitools.service.document.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    /** 提示词用途：格式 */
    private static final String PROMPT_USE_FORMAT = "format";

    /** 提示词用途：生成内容 */
    private static final String PROMPT_USE_GENERATE = "generate";

    private final AiClient aiClient;
    private final HistoryService historyService;
    private final AiToolMapper aiToolMapper;
    private final AiPromptTemplateService aiPromptTemplateService;
    private final DocumentParser documentParser;

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
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
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
        return formatPrompt.contains("%s") ? formatPrompt.replace("%s", content) : formatPrompt;
    }

    /**
     * 拼接 user 提示词：generate 提示词（规定生成内容）+ 实际工作记录
     */
    private String buildUserPrompt(String generatePrompt, String content) {
        return generatePrompt + "\n\n工作记录：\n" + content;
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
}
