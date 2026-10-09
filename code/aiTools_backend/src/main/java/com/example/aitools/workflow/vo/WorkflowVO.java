package com.example.aitools.workflow.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流详情 / 列表出参。
 */
@Data
public class WorkflowVO {

    private String workflowId;

    private String name;

    private String description;

    private List<WorkflowNodeVO> nodes;

    private Integer nodeCount;

    private Integer maxDepth;

    private Integer status;

    /** 创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;

    /** 最近一次运行状态（列表页展示用，可空） */
    private Integer lastRunStatus;

    /**
     * 节点出参：在存储结构基础上补充工具展示信息（前端无需再查工具表）。
     */
    @Data
    public static class WorkflowNodeVO {
        private String nodeId;
        private String nodeRef;
        private String name;
        private List<String> deps;
        private Map<String, String> params;

        /** 工具显示名 */
        private String toolName;
        /** 工具输入类型（逗号分隔） */
        private String inputType;
        /** 工具输出类型（逗号分隔） */
        private String outputType;
        /** 工具描述（节点卡副标题，如「提取文档/图片中的文字」） */
        private String description;
        /** 工具图标标识（前端按此映射通用图标，如 file-text / mic / meeting） */
        private String icon;
        /** 是否源节点（deps 为空）——运行时需用户提供输入 */
        private Boolean isSource;
    }
}
