package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_aitools_history")
public class History implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long toolId;

    private Long modelId;

    private String aiCode;

    /** 来源：1=单工具直接调用，2=工作流节点调用（见 Constants.HISTORY_SOURCE_*） */
    private Integer sourceType;

    /** 工作流运行 ID（sys_workflow_run.run_id）；单工具调用为 null */
    private String runId;

    /** 工作流节点 ID（node_results 的键，如 n1）；单工具调用为 null */
    private String nodeId;

    /** 节点名称快照（如「文档提取」）；工作流改名后仍显示当时的名称 */
    private String nodeName;

    private Integer status;

    private Integer duration;

    private LocalDateTime createTime;

    @TableLogic
    private Integer dr;
}
