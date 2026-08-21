package com.example.aitools.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptRequest {

    @NotBlank(message = "提示词内容不能为空")
    private String promptText;

    /** 提示词用途：format/generate */
    @NotBlank(message = "提示词用途不能为空")
    private String promptUse;

    /** 所属工具编码（绑定具体工具） */
    @NotBlank(message = "所属工具不能为空")
    private String toolCode;

    /** 提示词名称（用户自定义命名，可空；为空时后端存 NULL，前端展示"未命名"） */
    private String promptName;
}