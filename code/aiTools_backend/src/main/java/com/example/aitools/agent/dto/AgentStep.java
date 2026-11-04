package com.example.aitools.agent.dto;

import lombok.Data;

/**
 * 智能体运行中的一步（存于 sys_agent_run.steps JSON 数组的元素）。
 *
 * <p>结构与 {@code sys_workflow_run.node_results} 同构（项目既有风格）。
 */
@Data
public class AgentStep {

    /** 序号（从 1 开始） */
    private Integer seq;

    /** 模型的思考（展示用，不参与执行） */
    private String thought;

    /** 调用的工具编码（模型直接给答案的那一步没有此字段） */
    private String tool;

    /** 工具参数（模型给的） */
    private String args;

    /** 工具执行结果（超长会被截断） */
    private String output;

    /** 工具结果是否被截断 */
    private Boolean outputTruncated;

    /** 本步耗时（毫秒） */
    private Long costMs;

    /** 本步错误信息（工具失败时） */
    private String errorMsg;
}
