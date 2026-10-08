package com.example.aitools.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 运行工作流请求。
 */
@Data
public class WorkflowRunRequest {

    /**
     * 运行时输入：nodeId → 输入数组。
     * <ul>
     *   <li>文本类源节点：数组元素为文本内容</li>
     *   <li>文件类源节点：数组元素为文件路径 或 data URL（H5 端）</li>
     * </ul>
     * 仅源节点（deps 为空）需要提供；有上游的节点忽略此项。
     */
    private Map<String, List<String>> inputs;
}
