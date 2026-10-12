package com.example.aitools.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiWorkSummaryDTO {

    @NotBlank(message = "工作内容不能为空")
    private String content;

    /** 用户自定义提示词（兼容旧前端，未按用途拆分时按生成内容处理） */
    private String prompt;

    /** 系统提示词ID（可选，前端选择系统提示词后传） */
    private Long promptId;

    /** 用户自定义格式提示词（可选，format） */
    private String promptFormat;

    /** 用户自定义生成内容提示词（可选，generate） */
    private String promptGenerate;

    /**
     * 调用方临时传入的 API Key（BYOK 覆盖）。可选；不传走用户配置 / 系统默认。
     * <p>仅请求体使用，<b>不会</b>回传给前端。
     */
    private String apiKey;

    /**
     * 用户选定的 AI 模型编码（来自 /api/user-ai-model/list）。
     * 可选；不传走 Resolver 默认（用户默认 / 系统默认）。
     * <p>后端会校验该 modelCode 的 capability 与工具所需的 capability 匹配，不匹配 → 4001。
     */
    private String modelCode;
}
