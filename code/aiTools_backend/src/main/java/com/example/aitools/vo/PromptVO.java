package com.example.aitools.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromptVO {

    private Long id;

    private String promptText;

    /** 提示词名称（用户自定义命名，用于列表展示） */
    private String promptName;

    /** 提示词用途：format格式/generate生成内容 */
    private String promptUse;

    private String toolCode;

    private LocalDateTime createTime;
}