package com.example.aitools.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 生成提示词请求入参
 * 前端从 tools.js 读出 toolName/toolDesc 直接传入，避免后端再查表
 */
@Data
public class PromptGenerateRequest {

    /** 工具编码（如 work-summary） */
    @NotBlank(message = "工具编码不能为空")
    private String toolCode;

    /** 工具名称（如 工作总结） */
    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    /** 工具描述（如 将零散的工作记录整理成结构化总结） */
    private String toolDesc;

    /** 提示词用途：format 格式 / generate 生成内容 */
    @NotBlank(message = "提示词用途不能为空")
    private String promptUse;

    /** 用户填的"参考示例/需求" */
    @NotBlank(message = "需求描述不能为空")
    private String requirement;
}
