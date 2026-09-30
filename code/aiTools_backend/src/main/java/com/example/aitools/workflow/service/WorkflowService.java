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

    /** 运行历史列表（当前用户，按时间倒序） */
    List<WorkflowRunVO> listRuns(Long userId, String workflowId, int limit);

    /** 运行历史详情 */
    WorkflowRunVO runDetail(Long userId, String runId);
}
