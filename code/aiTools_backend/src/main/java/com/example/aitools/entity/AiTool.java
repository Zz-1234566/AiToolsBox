package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_aitools_tool")
public class AiTool implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolCode;

    private String toolName;

    private String toolType;

    private String componentType;

    private String description;

    private String icon;

    private Integer sortNo;

    private Integer status;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
