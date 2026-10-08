package com.example.aitools.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流运行历史，对应 sys_workflow_run。
 */
@Data
@TableName("sys_workflow_run")
public class WorkflowRun implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外 runId（UUID） */
    private String runId;

    private String workflowId;

    private Long userId;

    /** 0待运行 1运行中 2成功 3部分失败 4失败 5已取消 */
    private Integer status;

    /** 每节点结果 JSON：{nodeId:{status,inputs[],outputs[],costMs,errorMsg}} */
    private String nodeResults;

    /** 运行时输入快照 JSON：{nodeId:{text|files[]}} */
    private String inputSnapshot;

    private Integer successCount;

    private Integer failCount;

    /** 本次实际层数 */
    private Integer maxDepth;

    /** 总耗时（毫秒） */
    private Integer duration;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime finishedAt;

    @TableLogic
    private Integer dr;
}
