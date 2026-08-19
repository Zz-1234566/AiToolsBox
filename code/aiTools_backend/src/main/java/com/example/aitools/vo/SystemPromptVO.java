package com.example.aitools.vo;

import lombok.Data;

@Data
public class SystemPromptVO {
    private Long id;
    private String promptName;
    private String promptType;
    /** 提示词用途：format格式/generate生成内容 */
    private String promptUse;
    /** 提示词正文（选中后填入输入框） */
    private String promptText;
}
