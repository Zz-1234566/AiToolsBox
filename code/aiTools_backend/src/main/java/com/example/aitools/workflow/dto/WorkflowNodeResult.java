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

    /**
     * 节点显示名（如「文档提取」），由出参组装时按 nodeId 回填，不写回 node_results JSON。
     * <p>
     * 取值来源：sys_workflow.nodes 中该 nodeId 的 name，缺失时用工具名
     * （sys_aitools_tool.tool_name）兜底；都取不到时为 null，
     * 前端据此回退显示 nodeId，不使用「节点1」这类占位符。
     */
    private String nodeName;
}
