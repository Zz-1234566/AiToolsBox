package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_ai_model")
public class AiModel implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelCode;

    private String modelName;

    private String apiUrl;

    private String apiKey;

    private String apiModel;

    private Long userId;

    private Integer isDefault;

    private Integer status;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
