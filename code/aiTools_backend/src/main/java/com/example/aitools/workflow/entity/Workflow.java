package com.example.aitools.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流（用户保存的编排），对应 sys_workflow。
 */
@Data
@TableName("sys_workflow")
public class Workflow implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外 workflowId（UUID） */
    private String workflowId;

    private Long userId;

    private String name;

    private String description;

    /** 节点列表 JSON：[{nodeId,nodeRef,name,deps,params}] */
    private String nodes;

    /** 节点数（冗余） */
    private Integer nodeCount;

    /** 保存时计算的层数（冗余） */
    private Integer maxDepth;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer dr;
}
