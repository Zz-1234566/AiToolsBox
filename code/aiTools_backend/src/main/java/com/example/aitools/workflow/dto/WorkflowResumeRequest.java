package com.example.aitools.workflow.dto;

import lombok.Data;

/**
 * 节点重跑请求（从某个节点开始，用上游已有产物重跑该节点及其下游）。
 *
 * <p><b>与「整轮重跑」的区别</b>：普通 {@code /run} 会从源节点开始把整条链跑一遍；
 * 本请求**跳过** {@code fromNodeId} 的所有上游节点，直接复用上一轮它们的产物。
 *
 * <p><b>多文件语义</b>：上游产物是**按文件顺序**的数组
 * （与 {@code node_results[nodeId].outputs} 一致），
 * 重跑会按同样的文件顺序逐个重跑，单文件 / 多文件都支持。
 */
@Data
public class WorkflowResumeRequest {

    /**
     * 从哪一轮运行续跑（必填）。
     * <p>该 run 必须属于当前用户与当前工作流，且**已跑完并落库**
     * （`node_results` 只在整轮结束时一次性落库，中途断掉的运行取不到中间产物）。
     */
    private String runId;

    /**
     * 从哪个节点开始重跑（必填）。
     * <p>该节点及其**所有下游**会被重新执行；它的上游只取产物、不再执行。
     */
    private String fromNodeId;

    /**
     * 源节点输入覆盖（可选）。
     * <p>默认为沿用上一轮的输入快照（{@code input_snapshot}）。
     * 只有当上一轮是「从源节点开始」且你想换输入时才需要传。
     * <p>结构：nodeId → 输入数组（每个元素 = 一个文件的输入）。
     */
    private java.util.Map<String, java.util.List<String>> inputs;
}
