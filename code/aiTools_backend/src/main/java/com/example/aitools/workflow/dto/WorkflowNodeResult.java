package com.example.aitools.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 单节点运行结果（存于 sys_workflow_run.node_results JSON，按 nodeId 索引）。
 */
@Data
public class WorkflowNodeResult {

    /** 节点状态：0待运行 1运行中 2成功 4失败（与 WorkflowRunStatusEnum 的 0/1/2/4 对齐） */
    private Integer status;

    /** 本节点实际输入（数组，与输出一一对应） */
    private List<String> inputs;

    /** 本节点输出（数组；失败时为空） */
    private List<String> outputs;

    /** 耗时（毫秒） */
    private Integer costMs;

    /** 失败原因 */
    private String errorMsg;
}
