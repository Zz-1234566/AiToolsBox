package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aitools.ai.AiClient;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.PromptGenerateRequest;
import com.example.aitools.dto.PromptRequest;
import com.example.aitools.entity.AiTool;
import com.example.aitools.entity.AiUserPrompt;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.mapper.AiUserPromptMapper;
import com.example.aitools.service.AiPromptService;
import com.example.aitools.vo.PromptGenerateVO;
import com.example.aitools.vo.PromptVO;
import com.example.aitools.vo.ToolOptionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptServiceImpl implements AiPromptService {

    private final AiUserPromptMapper aiUserPromptMapper;
    private final AiToolMapper aiToolMapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<PromptVO> list(Long userId, String toolCode) {
        LambdaQueryWrapper<AiUserPrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiUserPrompt::getUserId, userId)
                .eq(AiUserPrompt::getToolCode, toolCode)
                .eq(AiUserPrompt::getDr, Constants.DR_NORMAL)
                .orderByDesc(AiUserPrompt::getCreateTime);
        return aiUserPromptMapper.selectList(wrapper).stream().map(p -> {
            PromptVO vo = new PromptVO();
            vo.setId(p.getId());
            vo.setPromptText(p.getPromptText());
            vo.setPromptUse(p.getPromptUse());
            vo.setToolCode(p.getToolCode());
            vo.setCreateTime(p.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    public Long add(Long userId, PromptRequest request) {
        AiUserPrompt prompt = new AiUserPrompt();
        BeanUtils.copyProperties(request, prompt);
        prompt.setUserId(userId);
        prompt.setDr(Constants.DR_NORMAL);
        aiUserPromptMapper.insert(prompt);
        return prompt.getId();
    }

    @Override
    public void update(Long id, Long userId, PromptRequest request) {
        LambdaUpdateWrapper<AiUserPrompt> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiUserPrompt::getId, id)
                .eq(AiUserPrompt::getUserId, userId)
                .eq(AiUserPrompt::getDr, Constants.DR_NORMAL)
                .set(AiUserPrompt::getPromptText, request.getPromptText())
                .set(AiUserPrompt::getPromptUse, request.getPromptUse())
                .set(AiUserPrompt::getToolCode, request.getToolCode())
                .set(AiUserPrompt::getUpdateTime, LocalDateTime.now());
        if (aiUserPromptMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "提示词不存在或无权操作");
        }
    }

    @Override
    public void delete(Long id, Long userId) {
        LambdaUpdateWrapper<AiUserPrompt> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiUserPrompt::getId, id)
                .eq(AiUserPrompt::getUserId, userId)
                .eq(AiUserPrompt::getDr, Constants.DR_NORMAL)
                .set(AiUserPrompt::getDr, Constants.DR_DELETED);
        if (aiUserPromptMapper.update(null, wrapper) == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "提示词不存在或无权操作");
        }
    }

    @Override
    public List<ToolOptionVO> listTools() {
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiTool::getStatus, 1)
                .eq(AiTool::getDr, Constants.DR_NORMAL)
                .orderByAsc(AiTool::getSortNo);
        return aiToolMapper.selectList(wrapper).stream().map(t -> {
            ToolOptionVO vo = new ToolOptionVO();
            vo.setId(t.getId());
            vo.setToolCode(t.getToolCode());
            vo.setToolName(t.getToolName());
            vo.setToolType(t.getToolType());
            return vo;
        }).toList();
    }

    @Override
    public PromptGenerateVO generatePrompt(PromptGenerateRequest request) {
        // 1) 校验 promptUse 必须是 format / generate
        String promptUse = request.getPromptUse();
        if (!"format".equals(promptUse) && !"generate".equals(promptUse)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "promptUse 只能是 format 或 generate");
        }

        // 2) 拼 system prompt（约束 AI 输出 JSON）
        String useLabel = "format".equals(promptUse) ? "格式提示词（描述输出结构，如分几段、每段内容）" : "生成内容提示词（描述风格/人设/视角）";
        String toolDesc = request.getToolDesc() == null ? "" : request.getToolDesc();
        String systemPrompt = "你是一位专业的 AI 提示词工程师，擅长为 AI 工具撰写高质量的提示词。\n"
                + "工具场景：" + request.getToolName() + "（" + request.getToolCode() + "）\n"
                + "工具描述：" + toolDesc + "\n"
                + "本次撰写用途：" + useLabel + "\n"
                + "严格要求：\n"
                + "1. 只输出 promptText 字段，禁止输出任何解释、说明、客套话或代码块标记。\n"
                + "2. 必须严格按以下 JSON 格式返回，不要在 JSON 外面包任何字符：\n"
                + "{\"promptText\": \"你生成的提示词正文\"}\n"
                + "3. 提示词要清晰、具体、可直接被 AI 执行。\n"
                + "4. 如果是\"格式\"用途：提示词要描述输出结构，必要时使用 %s 作为内容占位符。\n"
                + "5. 如果是\"生成内容\"用途：提示词要描述风格/人设/语气/视角。";

        // 3) 拼 user prompt（带用户需求）
        String userPrompt = "用户需求：\n" + request.getRequirement();

        // 4) 调 AI
        String aiResponse;
        try {
            aiResponse = aiClient.chat(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("AI 生成提示词失败 toolCode={}", request.getToolCode(), e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "AI 服务暂时不可用，请稍后重试");
        }

        // 5) 解析 JSON 拿 promptText
        String promptText = parsePromptText(aiResponse);

        PromptGenerateVO vo = new PromptGenerateVO();
        vo.setPromptText(promptText);
        return vo;
    }

    /**
     * 从 AI 返回文本中解析出 promptText。
     * AI 偶尔会在 JSON 前后加 ```json ``` 等字符，做宽松处理；解析失败抛统一异常。
     */
    private String parsePromptText(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "AI 返回结果为空");
        }
        // 去掉可能的 markdown 代码块包裹
        String cleaned = aiResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```\\s*$", "");
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode node = root.get("promptText");
            if (node == null || node.isNull() || node.asText().isBlank()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "AI 返回内容缺少 promptText 字段");
            }
            return node.asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 返回 JSON 解析失败 rawResponse={}", aiResponse, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "AI 返回格式异常，请重试");
        }
    }
}