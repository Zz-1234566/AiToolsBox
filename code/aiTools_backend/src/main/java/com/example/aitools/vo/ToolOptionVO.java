package com.example.aitools.vo;

import lombok.Data;

@Data
public class ToolOptionVO {

    private Long id;

    private String toolCode;

    private String toolName;

    private String toolType;

    /** 输入类型（逗号分隔）：none/text/audio/document/image/file —— 工作流节点连线用 */
    private String inputType;

    /** 输出类型（逗号分隔）：none/text/image —— 工作流节点连线用 */
    private String outputType;
}
