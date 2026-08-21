package com.example.aitools.vo;

import lombok.Data;

/**
 * AI 生成提示词出参
 * 单字段 promptText，前端按 promptUse 填入对应 textarea
 */
@Data
public class PromptGenerateVO {

    /** 生成的提示词正文 */
    private String promptText;
}
