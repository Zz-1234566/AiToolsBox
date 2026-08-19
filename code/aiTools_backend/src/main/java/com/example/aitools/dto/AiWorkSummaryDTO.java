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
}
