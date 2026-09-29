package com.example.aitools.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流节点（存于 sys_workflow.nodes JSON 数组中的元素）。
 * <p>
 * 说明：不含 nodeType 字段——节点角色（源节点 / 有上游节点）由 deps 是否为空推导，
 * 避免冗余字段与自相矛盾的数据。
 */
@Data
public class WorkflowNode {

    /** 节点 ID（工作流内唯一，如 n1 / n2） */
    private String nodeId;

    /** 引用的工具编码（sys_aitools_tool.tool_code） */
    private String nodeRef;

    /** 节点显示名 */
    private String name;

    /** 上游节点 ID 列表；为空 = 源节点（运行时需用户提供输入） */
    private List<String> deps;

    /** 节点参数（promptFormat / promptGenerate / promptId 等） */
    private Map<String, String> params;
}
