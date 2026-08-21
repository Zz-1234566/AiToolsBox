package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_ai_user_prompt")
public class AiUserPrompt implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 所属工具编码（绑定具体工具） */
    private String toolCode;

    private String promptText;

    /** 提示词名称（用户自定义命名，用于列表展示） */
    private String promptName;

    /** 提示词用途：format格式/generate生成内容 */
    private String promptUse;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer dr;
}