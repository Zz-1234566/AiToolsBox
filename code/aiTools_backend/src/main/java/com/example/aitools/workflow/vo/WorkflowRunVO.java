package com.example.aitools.workflow.vo;

import lombok.Data;

import java.util.Map;

import com.example.aitools.workflow.dto.WorkflowNodeResult;

/**
 * 工作流运行历史出参。
 */
@Data
public class WorkflowRunVO {

    private String runId;

    private String workflowId;

    private String workflowName;

    /** 0待运行 1运行中 2成功 3部分失败 4失败 5已取消 */
    private Integer status;

    /** 状态中文（来自 WorkflowRunStatusEnum，前端直接用，不在前端硬编码映射） */
    private String statusLabel;

    /** 每节点结果：nodeId → {status,inputs[],outputs[],costMs,errorMsg} */
    private Map<String, WorkflowNodeResult> nodeResults;

    private Integer successCount;

    private Integer failCount;

    private Integer maxDepth;

    private Integer duration;

    private String errorMsg;

    private String createTime;

    private String finishedAt;
}
