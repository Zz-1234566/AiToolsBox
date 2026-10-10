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

    /**
     * 本次运行的输入快照（JSON 字符串）。
     * <p>
     * 用途：详情页展示「用户当时传了什么」，便于失败排查后原样重跑。
     */
    private String inputSnapshot;

    /**
     * 源节点输入文件的原始文件名（JSON 字符串：{nodeId: [name]}）。
     * <p>
     * 与 {@link #inputSnapshot} 分工：后者存 URL（复现执行用），本字段存原始文件名（展示用）。
     * 老数据为 null，前端据此回落到从 URL 反解出 uuid 名。
     */
    private String inputFileNames;

    /**
     * 触发方式：manual 手动运行（当前唯一）/ auto / schedule。
     * <p>
     * 单独存字段而非固定写死，是为将来支持定时触发与被其他工作流调用时
     * 能区分来源，无需再改表。
     */
    private String triggerType;

    /** 触发方式中文名，前端直接显示 */
    private String triggerTypeLabel;

    /**
     * 文件类产物（sys_tool_output，全局工具产物表）。
     * <p>
     * 与 nodeResults 的分工：文本留在 nodeResults[nid].outputs，
     * 图片/视频/音频/文件放这里，前端按 outputType 分支渲染。
     * <p>
     * 本字段仅工作流来源有值；独立调用工具时产物写全局工具产物表
     * （runId/nodeId 为 null），通过工具自身的响应返回。
     */
    private java.util.List<com.example.aitools.vo.ToolOutputVO> outputs;
}
