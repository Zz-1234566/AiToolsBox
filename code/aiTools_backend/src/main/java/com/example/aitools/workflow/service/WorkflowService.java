package com.example.aitools.workflow.service;

import com.example.aitools.workflow.dto.WorkflowRunRequest;
import com.example.aitools.workflow.vo.WorkflowRunVO;
import com.example.aitools.workflow.vo.WorkflowVO;

import java.util.List;

/**
 * 工作流服务：CRUD + 运行 + 运行历史。
 */
public interface WorkflowService {

    /** 保存（新建或覆盖）工作流，返回 workflowId */
    String save(Long userId, String workflowId, String name, String description, String nodesJson);

    /** 列表（当前用户，按更新时间倒序） */
    List<WorkflowVO> list(Long userId);

    /** 详情（校验归属） */
    WorkflowVO detail(Long userId, String workflowId);

    /** 删除（逻辑删除，校验归属） */
    void delete(Long userId, String workflowId);

    /** 运行（同步执行，返回运行结果） */
    WorkflowRunVO run(Long userId, String workflowId, WorkflowRunRequest request);

    /**
     * 流式运行工作流（SSE）：立即返回 emitter，后台按文件逐个执行，
     * 每个节点开始/片段/完成实时推送，前端可边跑边展示。
     * <p>执行过程中会持续写库（RUNNING → 终态），断线后仍可在运行历史查看结果。
     *
     * @param onEmitter 由调用方创建并配置好的 SseEmitter（超时、错误回调已设置）
     */
    void runStream(Long userId, String workflowId, WorkflowRunRequest request,
                   org.springframework.web.servlet.mvc.method.annotation.SseEmitter onEmitter);

    /**
     * <b>节点重跑</b>（流式，SSE）：以指定节点为起点，用它上游的**已有产物**
     * 重跑该节点及其下游；上游节点不重新执行。
     *
     * <p>多文件支持：上游产物按文件顺序存储，重跑按同样的文件顺序逐个进行。
     * <p>产生新的 run 记录（便于审计），node_results 自包含（含上游结果）。
     * <p>前提：上一轮必须已跑完并落库（node_results 只在整轮结束时一次性写入）。
     *
     * @param onEmitter 由调用方创建并配置好的 SseEmitter
     */
    void runStreamResume(Long userId, String workflowId,
                         com.example.aitools.workflow.dto.WorkflowResumeRequest request,
                         org.springframework.web.servlet.mvc.method.annotation.SseEmitter onEmitter);

    /** 运行历史列表（当前用户，按时间倒序） */
    List<WorkflowRunVO> listRuns(Long userId, String workflowId, int limit);

    /** 运行历史详情 */
    WorkflowRunVO runDetail(Long userId, String runId);
}
