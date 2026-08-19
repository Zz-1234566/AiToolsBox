package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_ai_prompt")
public class AiPrompt implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolCode;

    private String promptType;

    /** 提示词名称（如默认/简洁版） */
    private String promptName;

    /** 提示词用途：format格式/generate生成内容 */
    private String promptUse;

    private String promptContent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer dr;
}
