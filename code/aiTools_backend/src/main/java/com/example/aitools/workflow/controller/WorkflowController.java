package com.example.aitools.workflow.controller;

import com.example.aitools.common.Result;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.workflow.dto.WorkflowRunRequest;
import com.example.aitools.workflow.service.WorkflowService;
import com.example.aitools.workflow.vo.WorkflowRunVO;
import com.example.aitools.workflow.vo.WorkflowVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流 Controller。
 * <p>
 * 只做：接收参数 + 鉴权 + 调 service + 返回包装结果（AGENTS.md 第 2 节分层要求）。
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final AuthUtil authUtil;

    /** 保存工作流（workflowId 为空 = 新建，否则覆盖） */
    @PostMapping("/save")
    public Result<String> save(@RequestBody SaveRequest body, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        String id = workflowService.save(userId, body.getWorkflowId(), body.getName(),
                body.getDescription(), body.getNodes());
        return Result.success("保存成功", id);
    }

    /** 工作流列表 */
    @GetMapping("/list")
    public Result<List<WorkflowVO>> list(HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(workflowService.list(userId));
    }

    /** 工作流详情 */
    @GetMapping("/{workflowId}")
    public Result<WorkflowVO> detail(@PathVariable String workflowId, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(workflowService.detail(userId, workflowId));
    }

    /** 删除工作流 */
    @DeleteMapping("/{workflowId}")
    public Result<Void> delete(@PathVariable String workflowId, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        workflowService.delete(userId, workflowId);
        return Result.success("删除成功", null);
    }

    /** 运行工作流（同步返回运行结果） */
    @PostMapping("/{workflowId}/run")
    public Result<WorkflowRunVO> run(@PathVariable String workflowId,
                                     @RequestBody(required = false) WorkflowRunRequest body,
                                     HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success("运行完成", workflowService.run(userId, workflowId, body));
    }

    /**
     * 流式运行工作流（SSE）：立即返回事件流，后台按文件逐个执行并实时推送进度。
     * <p>帧类型见 WorkflowService#runStream；前端逐帧渲染即可实现「边跑边展示」。
     */
    @PostMapping(value = "/{workflowId}/run/stream", produces = "text/event-stream;charset=UTF-8")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter runStream(
            @PathVariable String workflowId,
            @RequestBody(required = false) WorkflowRunRequest body,
            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(10 * 60 * 1000L);
        emitter.onTimeout(() -> {
            log.warn("[workflow] SSE 超时 workflowId={}", workflowId);
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                        .event().data("{\"type\":\"error\",\"message\":\"运行超时，请稍后重试\"}"));
                emitter.complete();
            } catch (Exception e) {
                log.warn("[workflow] SSE 超时收尾失败", e);
            }
        });
        emitter.onError(t -> log.warn("[workflow] SSE 连接异常: {}", t == null ? "unknown" : t.getMessage()));
        // 校验/建记录在请求线程内同步完成，异常由 GlobalExceptionHandler 处理
        workflowService.runStream(userId, workflowId, body, emitter);
        return emitter;
    }

    /** 运行历史列表（可按 workflowId 过滤） */
    @GetMapping("/runs")
    public Result<List<WorkflowRunVO>> runs(@RequestParam(required = false) String workflowId,
                                            @RequestParam(required = false, defaultValue = "20") Integer limit,
                                            HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(workflowService.listRuns(userId, workflowId, limit == null ? 20 : limit));
    }

    /** 运行历史详情 */
    @GetMapping("/runs/{runId}")
    public Result<WorkflowRunVO> runDetail(@PathVariable String runId, HttpServletRequest request) {
        Long userId = authUtil.getUserIdFromRequest(request);
        return Result.success(workflowService.runDetail(userId, runId));
    }

    /** 保存请求体 */
    @lombok.Data
    public static class SaveRequest {
        private String workflowId;
        private String name;
        private String description;
        /** 节点列表 JSON 字符串 */
        private String nodes;
    }
}
