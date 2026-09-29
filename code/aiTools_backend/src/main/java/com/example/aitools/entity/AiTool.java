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

    /** 输入类型（逗号分隔）：none/text/audio/document/image/file —— 工作流节点连线匹配用 */
    private String inputType;

    /** 输出类型（逗号分隔）：none/text/image */
    private String outputType;

    private Integer sortNo;

    private Integer status;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
