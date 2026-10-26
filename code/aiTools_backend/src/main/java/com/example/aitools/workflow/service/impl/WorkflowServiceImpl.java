package com.example.aitools.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.common.Constants;
import com.example.aitools.common.WorkflowRunStatusEnum;
import com.example.aitools.entity.AiTool;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.common.ResultCode;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.workflow.dto.WorkflowNode;
import com.example.aitools.workflow.dto.WorkflowNodeResult;
import com.example.aitools.workflow.dto.WorkflowRunRequest;
import com.example.aitools.workflow.engine.WorkflowEngine;
import com.example.aitools.workflow.engine.WorkflowValidator;
import com.example.aitools.workflow.entity.Workflow;
import com.example.aitools.workflow.entity.WorkflowRun;
import com.example.aitools.workflow.mapper.WorkflowMapper;
import com.example.aitools.workflow.mapper.WorkflowRunMapper;
import com.example.aitools.workflow.service.WorkflowService;
import com.example.aitools.workflow.vo.WorkflowRunVO;
import com.example.aitools.workflow.vo.WorkflowVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.service.ToolOutputService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * 工作流服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 触发方式：手动运行（当前唯一支持的方式） */
    private static final String TRIGGER_MANUAL = "manual";

    private final WorkflowMapper workflowMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final ToolOutputService workflowOutputService;
    private final AiToolMapper aiToolMapper;
    private final WorkflowValidator workflowValidator;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    /** SSE 流式任务线程池（与工具流式接口共用） */
    @Qualifier(ExecutorConfig.STREAM_EXECUTOR)
    private final java.util.concurrent.Executor streamExecutor;

    // ==================== 保存 ====================

    @Override
    @Transactional
    public String save(Long userId, String workflowId, String name, String description, String nodesJson) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "工作流名称不能为空");
        }
        if (name.length() > Constants.WORKFLOW_NAME_MAX_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "工作流名称不能超过 " + Constants.WORKFLOW_NAME_MAX_LENGTH + " 字");
        }
        List<WorkflowNode> nodes = parseNodes(nodesJson);

        // 保存即校验：结构 / 环 / 深度≤5 / 连线类型匹配（与运行时同一套规则）
        WorkflowValidator.Plan plan = workflowValidator.validateAndPlan(nodes);

        Workflow wf;
        String resultId;
        if (workflowId == null || workflowId.isBlank()) {
            wf = new Workflow();
            wf.setWorkflowId(UUID.randomUUID().toString().replace("-", ""));
            wf.setUserId(userId);
            wf.setStatus(1);
            resultId = wf.getWorkflowId();
        } else {
            wf = getOwned(userId, workflowId);
            resultId = workflowId;
        }
        wf.setName(name.trim());
        wf.setDescription(description == null ? null : description.trim());
        wf.setNodes(writeJson(nodes));
        wf.setNodeCount(nodes.size());
        wf.setMaxDepth(plan.depth());

        if (wf.getId() == null) {
            workflowMapper.insert(wf);
        } else {
            workflowMapper.updateById(wf);
        }
        log.info("[workflow] saved userId={} workflowId={} nodes={} depth={}",
                userId, resultId, nodes.size(), plan.depth());
        return resultId;
    }

    // ==================== 查询 ====================

    @Override
    public List<WorkflowVO> list(Long userId) {
        LambdaQueryWrapper<Workflow> w = new LambdaQueryWrapper<>();
        w.eq(Workflow::getUserId, userId).orderByDesc(Workflow::getUpdateTime);
        List<Workflow> list = workflowMapper.selectList(w);
        List<WorkflowVO> out = new ArrayList<>();
        for (Workflow wf : list) {
            WorkflowVO vo = toVO(wf, true);
            // 最近一次运行状态
            WorkflowRun last = latestRun(userId, wf.getWorkflowId());
            if (last != null) vo.setLastRunStatus(last.getStatus());
            out.add(vo);
        }
        return out;
    }

    @Override
    public WorkflowVO detail(Long userId, String workflowId) {
        return toVO(getOwned(userId, workflowId), true);
    }

    @Override
    @Transactional
    public void delete(Long userId, String workflowId) {
        Workflow wf = getOwned(userId, workflowId);
        workflowMapper.deleteById(wf.getId());
        // 连带逻辑删除该工作流的运行历史
        LambdaQueryWrapper<WorkflowRun> w = new LambdaQueryWrapper<>();
        w.eq(WorkflowRun::getWorkflowId, workflowId).eq(WorkflowRun::getUserId, userId);
        workflowRunMapper.delete(w);
    }

    // ==================== 运行 ====================

    @Override
    public WorkflowRunVO run(Long userId, String workflowId, WorkflowRunRequest request) {
        Workflow wf = getOwned(userId, workflowId);
        List<WorkflowNode> nodes = parseNodes(wf.getNodes());
        WorkflowValidator.Plan plan = workflowValidator.validateAndPlan(nodes);

        // 源节点必须有运行时输入（文件已由前端上传为本地路径/dataURL，文本为原文）
        Map<String, List<String>> sourceInputs = request == null || request.getInputs() == null
                ? new HashMap<>() : request.getInputs();
        for (WorkflowNode n : nodes) {
            boolean isSource = n.getDeps() == null || n.getDeps().isEmpty();
            if (isSource) {
                List<String> in = sourceInputs.get(n.getNodeId());
                if (in == null || in.isEmpty()) {
                    throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "存在起始节点未填写输入，请先填写或上传");
                }
            }
        }

        // 建运行记录（RUNNING）
        WorkflowRun run = new WorkflowRun();
        run.setRunId(UUID.randomUUID().toString().replace("-", ""));
        run.setWorkflowId(workflowId);
        run.setUserId(userId);
        run.setStatus(WorkflowRunStatusEnum.RUNNING.getCode());
        run.setSuccessCount(0);
        run.setFailCount(0);
        run.setMaxDepth(plan.depth());
        run.setDuration(0);
        run.setInputSnapshot(writeJson(sourceInputs));
        run.setInputFileNames(writeFileNamesJson(sourceFileNames(request)));
        workflowRunMapper.insert(run);

        // 执行
        WorkflowEngine.ExecutionResult er;
        String fatal = null;
        try {
            er = workflowEngine.execute(plan, sourceInputs, userId, run.getRunId(), sourceFileNames(request));
        } catch (Exception e) {
            log.error("[workflow] 运行异常 workflowId={}", workflowId, e);
            // 脱敏：原始异常 message 可能含内部细节（如节点输入路径/上游地址），只进日志
            fatal = ResultCode.WORKFLOW_RUN_FAILED.getMessage();
            er = null;
        }

        // 落结果
        if (er != null) {
            run.setStatus(er.status);
            run.setNodeResults(writeJson(er.nodeResults));
            run.setSuccessCount(er.successCount);
            run.setFailCount(er.failCount);
            run.setMaxDepth(er.depth);
            run.setDuration(er.duration);
        } else {
            run.setStatus(WorkflowRunStatusEnum.FAILED.getCode());
            run.setErrorMsg(fatal);
        }
        run.setFinishedAt(LocalDateTime.now());
        workflowRunMapper.updateById(run);

        return toRunVO(run, wf.getName());
    }

    // ==================== 流式运行（SSE）====================

    @Override
    public void runStream(Long userId, String workflowId, WorkflowRunRequest request,
                          org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        // 1) 同步校验（在请求线程内完成，异常可直接被 GlobalExceptionHandler 捕获）
        Workflow wf = getOwned(userId, workflowId);
        List<WorkflowNode> nodes = parseNodes(wf.getNodes());
        WorkflowValidator.Plan plan = workflowValidator.validateAndPlan(nodes);

        Map<String, List<String>> sourceInputs = request == null || request.getInputs() == null
                ? new HashMap<>() : request.getInputs();
        for (WorkflowNode n : nodes) {
            boolean isSource = n.getDeps() == null || n.getDeps().isEmpty();
            if (isSource) {
                List<String> in = sourceInputs.get(n.getNodeId());
                if (in == null || in.isEmpty()) {
                    throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "存在起始节点未填写输入，请先填写或上传");
                }
            }
        }

        // 2) 建运行记录（RUNNING）
        WorkflowRun run = new WorkflowRun();
        run.setRunId(UUID.randomUUID().toString().replace("-", ""));
        run.setWorkflowId(workflowId);
        run.setUserId(userId);
        run.setStatus(WorkflowRunStatusEnum.RUNNING.getCode());
        run.setSuccessCount(0);
        run.setFailCount(0);
        run.setMaxDepth(plan.depth());
        run.setDuration(0);
        run.setInputSnapshot(writeJson(sourceInputs));
        run.setInputFileNames(writeFileNamesJson(sourceFileNames(request)));
        workflowRunMapper.insert(run);

        // 3) 异步执行 + 实时推送
        try {
            streamExecutor.execute(() -> {
            try {
                sendEvent(emitter, Map.of("type", "run_start", "runId", run.getRunId()));

                // nodeId → 节点中文名，整轮只建一次：SSE 逐节点逐文件推送，
                // 若每帧查库会退化成「文件数 × 节点数」次查询。nodes 上面已解析好，直接复用不额外查库。
                final Map<String, String> nodeNameMap = buildNodeNameMap(nodes, workflowId);

                // 本次运行内「已推送过 chunk 的节点」集合（局部变量，无并发共享问题）
                java.util.Set<String> chunkedNodeIds = java.util.Collections.newSetFromMap(
                        new java.util.concurrent.ConcurrentHashMap<>());

                WorkflowEngine.ExecutionResult er = workflowEngine.executeWithProgress(
                        plan, sourceInputs, buildProgressListener(emitter, nodeNameMap, chunkedNodeIds),
                        userId, run.getRunId(), sourceFileNames(request));

                // 4) 落库终态
                persistTerminal(run, er);

                Map<String, Object> done = new LinkedHashMap<>();
                done.put("type", "all_done");
                done.put("runId", run.getRunId());
                done.put("status", er.status);
                done.put("successCount", er.successCount);
                done.put("failCount", er.failCount);
                done.put("duration", er.duration);
                sendEvent(emitter, done);
                emitter.complete();
            } catch (Exception e) {
                log.error("[workflow] 流式运行异常 workflowId={}", workflowId, e);
                // 落库失败态
                try {
                    run.setStatus(WorkflowRunStatusEnum.FAILED.getCode());
                    run.setErrorMsg(ResultCode.WORKFLOW_RUN_FAILED.getMessage());
                    run.setFinishedAt(LocalDateTime.now());
                    workflowRunMapper.updateById(run);
                } catch (Exception ex) {
                    log.error("[workflow] 流式运行失败态落库异常 runId={}", run.getRunId(), ex);
                }
                try {
                    sendEvent(emitter, Map.of("type", "error",
                            "message", ResultCode.WORKFLOW_RUN_FAILED.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    log.warn("[workflow] 推送错误帧失败（连接可能已关闭）", ex);
                }
            }
            });
        } catch (RejectedExecutionException e) {
            // 线程池饱和：任务未开始，但运行记录已建（RUNNING），必须在此收尾，
            // 否则前端/列表里永远等不到终态。同时回一帧明确错误，别让前端白屏挂到超时。
            log.error("[workflow] 流式任务被拒（线程池饱和）workflowId={} userId={}", workflowId, userId, e);
            try {
                run.setStatus(WorkflowRunStatusEnum.FAILED.getCode());
                run.setErrorMsg(ResultCode.SERVICE_UNAVAILABLE.getMessage());
                run.setFinishedAt(LocalDateTime.now());
                workflowRunMapper.updateById(run);
            } catch (Exception ex) {
                log.error("[workflow] 池饱和失败态落库异常 runId={}", run.getRunId(), ex);
            }
            try {
                sendEvent(emitter, Map.of("type", "error",
                        "message", ResultCode.SERVICE_UNAVAILABLE.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("[workflow] 池饱和错误帧推送失败（连接可能已关闭）", ex);
            }
        }
    }

    // ==================== 节点重跑（从某节点续跑）====================

    /**
     * 节点重跑（流式）：以指定节点为起点，用它上游的**已有产物**重跑该节点及其下游。
     *
     * <p><b>与整轮重跑的区别</b>：普通 {@code /run/stream} 会把整条链从源节点跑一遍；
     * 本方法**跳过** {@code fromNodeId} 的所有上游节点，直接复用上一轮它们的产物作为输入。
     *
     * <p><b>多文件语义</b>：上游产物是**按文件顺序**的数组
     * （与 {@code node_results[nodeId].outputs} 一致），重跑按同样的文件顺序逐个进行，
     * 单文件 / 多文件都支持。
     *
     * <p><b>落库</b>：产生**新的 run 记录**（不复用旧记录，便于审计）；
     * 新记录的 node_results 是**自包含**的（上游节点的结果一并带上）。
     *
     * <p><b>前提</b>：上一轮必须**已跑完并落库** —— {@code node_results} 只在整轮结束时
     * 一次性写入，中途断掉的运行取不到中间产物。
     */
    @Override
    public void runStreamResume(Long userId, String workflowId,
                               com.example.aitools.workflow.dto.WorkflowResumeRequest request,
                               org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        if (request == null || request.getRunId() == null || request.getRunId().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请指定要续跑的运行记录");
        }
        if (request.getFromNodeId() == null || request.getFromNodeId().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请指定要从哪个节点开始重跑");
        }

        // 1) 工作流与执行计划
        Workflow wf = getOwned(userId, workflowId);
        List<WorkflowNode> nodes = parseNodes(wf.getNodes());
        WorkflowValidator.Plan plan = workflowValidator.validateAndPlan(nodes);

        // 2) 上一轮运行记录（必须属于当前用户与当前工作流）
        WorkflowRun prev = workflowRunMapper.selectOne(
                new LambdaQueryWrapper<WorkflowRun>()
                        .eq(WorkflowRun::getRunId, request.getRunId())
                        .eq(WorkflowRun::getUserId, userId)
                        .eq(WorkflowRun::getWorkflowId, workflowId)
                        .eq(WorkflowRun::getDr, Constants.DR_NORMAL)
                        .last("LIMIT 1"));
        if (prev == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "要续跑的运行记录不存在");
        }
        if (prev.getNodeResults() == null || prev.getNodeResults().isBlank()) {
            throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(),
                    "该运行没有节点结果（可能未跑完），无法重跑");
        }

        // 3) 解析上一轮的节点结果
        Map<String, WorkflowNodeResult> prevResults;
        try {
            prevResults = objectMapper.readValue(prev.getNodeResults(),
                    new TypeReference<LinkedHashMap<String, WorkflowNodeResult>>() {});
        } catch (Exception e) {
            log.error("[workflow] 解析上一轮 node_results 失败 runId={}", prev.getRunId(), e);
            throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "该运行的节点结果无法解析");
        }

        // 4) 定位重跑节点，并收集它的**所有上游**（这些节点不执行，只取产物）
        String fromNodeId = request.getFromNodeId();
        int startLevel = -1;
        for (int i = 0; i < plan.levels.size(); i++) {
            for (WorkflowNode n : plan.levels.get(i)) {
                if (fromNodeId.equals(n.getNodeId())) { startLevel = i; break; }
            }
            if (startLevel >= 0) break;
        }
        if (startLevel < 0) {
            throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(),
                    "工作流里已没有该节点（可能被删除或改过），无法重跑");
        }

        // 上游 = 该节点的所有传递依赖
        Set<String> upstream = new LinkedHashSet<>();
        collectUpstream(plan, fromNodeId, upstream);

        // 5) 组装预置产物：上游节点的输出 + 上一轮的结果
        WorkflowEngine.PresetOutputs preset = new WorkflowEngine.PresetOutputs();
        for (String nodeId : upstream) {
            WorkflowNodeResult old = prevResults.get(nodeId);
            if (old == null || old.getOutputs() == null || old.getOutputs().isEmpty()) {
                // 上游没跑出产物 → 无法重跑（要重跑上游，用户应该选更靠上的节点）
                throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(),
                        "上游节点「" + nodeNameOf(nodes, nodeId) + "」没有可用产物，无法重跑");
            }
            preset.outputs.put(nodeId, old.getOutputs());
            preset.results.put(nodeId, old);
        }

        // 6) 源节点输入：默认沿用上一轮的输入快照
        Map<String, List<String>> sourceInputs = (request.getInputs() != null && !request.getInputs().isEmpty())
                ? request.getInputs()
                : parseInputSnapshot(prev.getInputSnapshot());

        // 7) 截断执行计划：从重跑节点所在层开始
        WorkflowValidator.Plan resumed = new WorkflowValidator.Plan();
        for (int i = startLevel; i < plan.levels.size(); i++) {
            resumed.levels.add(plan.levels.get(i));
            for (WorkflowNode n : plan.levels.get(i)) {
                resumed.nodeMap.put(n.getNodeId(), n);
            }
        }

        // 8) 建运行记录（RUNNING）—— 新记录，便于审计
        WorkflowRun run = new WorkflowRun();
        run.setRunId(UUID.randomUUID().toString().replace("-", ""));
        run.setWorkflowId(workflowId);
        run.setUserId(userId);
        run.setStatus(WorkflowRunStatusEnum.RUNNING.getCode());
        run.setSuccessCount(0);
        run.setFailCount(0);
        run.setMaxDepth(resumed.depth());
        run.setDuration(0);
        run.setInputSnapshot(writeJson(sourceInputs));
        run.setInputFileNames(prev.getInputFileNames());
        workflowRunMapper.insert(run);
        log.info("[workflow] 节点重跑 workflowId={} userId={} fromNodeId={} prevRunId={} newRunId={} upstream={}",
                workflowId, userId, fromNodeId, prev.getRunId(), run.getRunId(), upstream);

        // 9) 异步执行 + 实时推送
        try {
            streamExecutor.execute(() -> {
            try {
                sendEvent(emitter, Map.of("type", "run_start", "runId", run.getRunId()));

                final Map<String, String> nodeNameMap = buildNodeNameMap(nodes, workflowId);
                java.util.Set<String> chunkedNodeIds = java.util.Collections.newSetFromMap(
                        new java.util.concurrent.ConcurrentHashMap<>());

                WorkflowEngine.ExecutionResult er = workflowEngine.executeWithProgress(
                        resumed, sourceInputs, buildProgressListener(emitter, nodeNameMap, chunkedNodeIds),
                        userId, run.getRunId(), parseFileNames(prev.getInputFileNames()), preset);

                persistTerminal(run, er);

                Map<String, Object> done = new LinkedHashMap<>();
                done.put("type", "all_done");
                done.put("runId", run.getRunId());
                done.put("status", er.status);
                done.put("successCount", er.successCount);
                done.put("failCount", er.failCount);
                done.put("duration", er.duration);
                sendEvent(emitter, done);
                emitter.complete();
            } catch (Exception e) {
                log.error("[workflow] 节点重跑异常 workflowId={} fromNodeId={}", workflowId, fromNodeId, e);
                try {
                    run.setStatus(WorkflowRunStatusEnum.FAILED.getCode());
                    run.setErrorMsg(ResultCode.WORKFLOW_RUN_FAILED.getMessage());
                    run.setFinishedAt(LocalDateTime.now());
                    workflowRunMapper.updateById(run);
                } catch (Exception ex) {
                    log.error("[workflow] 节点重跑失败态落库异常 runId={}", run.getRunId(), ex);
                }
                try {
                    sendEvent(emitter, Map.of("type", "error",
                            "message", ResultCode.WORKFLOW_RUN_FAILED.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    log.warn("[workflow] 推送错误帧失败（连接可能已关闭）", ex);
                }
            }
            });
        } catch (RejectedExecutionException e) {
            log.error("[workflow] 节点重跑被拒（线程池饱和）workflowId={} userId={}", workflowId, userId, e);
            try {
                run.setStatus(WorkflowRunStatusEnum.FAILED.getCode());
                run.setErrorMsg(ResultCode.SERVICE_UNAVAILABLE.getMessage());
                run.setFinishedAt(LocalDateTime.now());
                workflowRunMapper.updateById(run);
            } catch (Exception ex) {
                log.error("[workflow] 池饱和失败态落库异常 runId={}", run.getRunId(), ex);
            }
            try {
                sendEvent(emitter, Map.of("type", "error",
                        "message", ResultCode.SERVICE_UNAVAILABLE.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("[workflow] 池饱和错误帧推送失败（连接可能已关闭）", ex);
            }
        }
    }

    /**
     * 收集某节点的<b>所有传递上游</b>（不含它自己）。
     */
    private void collectUpstream(WorkflowValidator.Plan plan, String nodeId, Set<String> acc) {
        WorkflowNode node = plan.nodeMap.get(nodeId);
        if (node == null || node.getDeps() == null) return;
        for (String dep : node.getDeps()) {
            if (acc.add(dep)) {
                collectUpstream(plan, dep, acc);
            }
        }
    }

    /** 节点 id → 节点中文名（取不到时回退节点 id） */
    private String nodeNameOf(List<WorkflowNode> nodes, String nodeId) {
        for (WorkflowNode n : nodes) {
            if (nodeId.equals(n.getNodeId())) {
                return (n.getName() != null && !n.getName().isBlank()) ? n.getName() : nodeId;
            }
        }
        return nodeId;
    }

    /**
     * 反解析上一轮的输入快照（{@code input_snapshot}）→ nodeId → 输入数组。
     * <p>解析失败返回空 Map（不抛异常）：重跑时没有源节点输入也能靠预置产物跑起来。
     */
    private Map<String, List<String>> parseInputSnapshot(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            Map<String, List<String>> m = objectMapper.readValue(json,
                    new TypeReference<LinkedHashMap<String, List<String>>>() {});
            return m == null ? new HashMap<>() : m;
        } catch (Exception e) {
            log.warn("[workflow] 解析 input_snapshot 失败（按空处理）", e);
            return new HashMap<>();
        }
    }

    /**
     * 反解析上一轮的原始文件名（{@code input_file_names}）→ nodeId → 文件名数组。
     */
    private Map<String, List<String>> parseFileNames(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, List<String>> m = objectMapper.readValue(json,
                    new TypeReference<LinkedHashMap<String, List<String>>>() {});
            return (m == null || m.isEmpty()) ? null : m;
        } catch (Exception e) {
            log.warn("[workflow] 解析 input_file_names 失败（按空处理）", e);
            return null;
        }
    }

    /**
     * 构造 SSE 进度监听器（普通运行与<b>节点重跑</b>共用）。
     *
     * @param emitter        SSE 通道
     * @param nodeNameMap    nodeId → 节点中文名
     * @param chunkedNodeIds 本次运行内已推送过 chunk 的节点（text 节点不再重复推 output）
     */
    private WorkflowEngine.ProgressListener buildProgressListener(
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
            Map<String, String> nodeNameMap,
            java.util.Set<String> chunkedNodeIds) {
        return new WorkflowEngine.ProgressListener() {
                                    @Override
                                    public void onFileStart(int fileIndex, int fileTotal) {
                                        sendEvent(emitter, Map.of("type", "file_start",
                                                "fileIndex", fileIndex, "fileTotal", fileTotal));
                                    }

                                    @Override
                                    public void onNodeStart(String nodeId, String nodeRef, int fileIndex, int fileTotal) {
                                        Map<String, Object> m = new LinkedHashMap<>();
                                        m.put("type", "node_start");
                                        m.put("nodeId", nodeId);
                                        m.put("nodeRef", nodeRef);
                                        // 节点中文名（如「录音转写」）：取不到时为 null，
                                        // 前端据此回退用 nodeRef 反查，不编造「节点1」之类占位符
                                        m.put("nodeName", nodeNameMap.get(nodeId));
                                        m.put("fileIndex", fileIndex);
                                        m.put("fileTotal", fileTotal);
                                        sendEvent(emitter, m);
                                    }

                                    @Override
                                    public void onNodeChunk(String nodeId, int fileIndex, String chunk) {
                                        chunkedNodeIds.add(nodeId);
                                        Map<String, Object> m = new LinkedHashMap<>();
                                        m.put("type", "chunk");
                                        m.put("nodeId", nodeId);
                                        m.put("fileIndex", fileIndex);
                                        m.put("text", chunk);
                                        sendEvent(emitter, m);
                                    }

                                    @Override
                                    public void onNodeDone(String nodeId, int fileIndex, boolean ok, String output, String errMsg) {
                                        Map<String, Object> m = new LinkedHashMap<>();
                                        m.put("type", "node_done");
                                        m.put("nodeId", nodeId);
                                        // 同 node_start：带上节点中文名，前端无需再靠 nodeRef 反查
                                        m.put("nodeName", nodeNameMap.get(nodeId));
                                        m.put("fileIndex", fileIndex);
                                        m.put("ok", ok);
                                        m.put("errMsg", errMsg);
                                        // 非 text 节点没有 chunk 帧，这里带上完整输出，避免前端丢内容；
                                        // text 节点已通过 chunk 帧推送，front-end 自行拼接，故不带 output
                                        m.put("output", chunkedNodeIds.contains(nodeId) ? null : output);
                                        sendEvent(emitter, m);
                                    }

                                    @Override
                                    public void onFileDone(int fileIndex, int fileTotal, boolean ok) {
                                        sendEvent(emitter, Map.of("type", "file_done",
                                                "fileIndex", fileIndex, "fileTotal", fileTotal, "ok", ok));
                                    }
        };
    }

    /**
     * 落库运行终态（普通运行与<b>节点重跑</b>共用）。
     */
    private void persistTerminal(WorkflowRun run, WorkflowEngine.ExecutionResult er) {
                        run.setStatus(er.status);
                        run.setNodeResults(writeJson(er.nodeResults));
                        run.setSuccessCount(er.successCount);
                        run.setFailCount(er.failCount);
                        run.setMaxDepth(er.depth);
                        run.setDuration(er.duration);
                        run.setFinishedAt(LocalDateTime.now());
                        workflowRunMapper.updateById(run);
    }

    /** 发送一帧 SSE；失败只记日志，不中断主流程（客户端可能已断开） */
    private void sendEvent(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
                           Map<String, ?> payload) {
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event().data(writeJson(payload)));
        } catch (Exception e) {
            log.warn("[workflow] SSE 推送失败（客户端可能已断开）: {}", e.getMessage(), e);
        }
    }

    // ==================== 运行历史 ====================

    @Override
    public List<WorkflowRunVO> listRuns(Long userId, String workflowId, int limit) {
        LambdaQueryWrapper<WorkflowRun> w = new LambdaQueryWrapper<>();
        w.eq(WorkflowRun::getUserId, userId);
        if (workflowId != null && !workflowId.isBlank()) {
            w.eq(WorkflowRun::getWorkflowId, workflowId);
        }
        w.orderByDesc(WorkflowRun::getId).last("LIMIT " + Math.max(1, Math.min(limit, 50)));
        List<WorkflowRun> runs = workflowRunMapper.selectList(w);
        List<WorkflowRunVO> out = new ArrayList<>();
        Map<String, String> nameCache = new HashMap<>();
        for (WorkflowRun r : runs) {
            String name = nameCache.computeIfAbsent(r.getWorkflowId(), id -> {
                LambdaQueryWrapper<Workflow> qw = new LambdaQueryWrapper<>();
                qw.eq(Workflow::getWorkflowId, id).last("LIMIT 1");
                Workflow wf = workflowMapper.selectOne(qw);
                return wf == null ? null : wf.getName();
            });
            out.add(toRunVO(r, name));
        }
        return out;
    }

    @Override
    public WorkflowRunVO runDetail(Long userId, String runId) {
        LambdaQueryWrapper<WorkflowRun> w = new LambdaQueryWrapper<>();
        w.eq(WorkflowRun::getRunId, runId).eq(WorkflowRun::getUserId, userId).last("LIMIT 1");
        WorkflowRun r = workflowRunMapper.selectOne(w);
        if (r == null) throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "运行记录不存在");
        LambdaQueryWrapper<Workflow> qw = new LambdaQueryWrapper<>();
        qw.eq(Workflow::getWorkflowId, r.getWorkflowId()).last("LIMIT 1");
        Workflow wf = workflowMapper.selectOne(qw);
        return toRunVO(r, wf == null ? null : wf.getName());
    }

    // ==================== 私有工具方法 ====================

    private Workflow getOwned(Long userId, String workflowId) {
        LambdaQueryWrapper<Workflow> w = new LambdaQueryWrapper<>();
        w.eq(Workflow::getWorkflowId, workflowId).last("LIMIT 1");
        Workflow wf = workflowMapper.selectOne(w);
        if (wf == null) throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "工作流不存在");
        if (!Objects.equals(wf.getUserId(), userId)) throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问该工作流");
        return wf;
    }

    private WorkflowRun latestRun(Long userId, String workflowId) {
        LambdaQueryWrapper<WorkflowRun> w = new LambdaQueryWrapper<>();
        w.eq(WorkflowRun::getUserId, userId).eq(WorkflowRun::getWorkflowId, workflowId)
                .orderByDesc(WorkflowRun::getId).last("LIMIT 1");
        return workflowRunMapper.selectOne(w);
    }

    private WorkflowVO toVO(Workflow wf, boolean withNodes) {
        WorkflowVO vo = new WorkflowVO();
        vo.setWorkflowId(wf.getWorkflowId());
        vo.setName(wf.getName());
        vo.setDescription(wf.getDescription());
        vo.setNodeCount(wf.getNodeCount());
        vo.setMaxDepth(wf.getMaxDepth());
        vo.setStatus(wf.getStatus());
        vo.setCreateTime(wf.getCreateTime() == null ? null : wf.getCreateTime().format(FMT));
        if (withNodes) {
            List<WorkflowNode> nodes = parseNodes(wf.getNodes());
            List<WorkflowVO.WorkflowNodeVO> nodeVOs = new ArrayList<>();
            for (WorkflowNode n : nodes) {
                WorkflowVO.WorkflowNodeVO nv = new WorkflowVO.WorkflowNodeVO();
                nv.setNodeId(n.getNodeId());
                nv.setNodeRef(n.getNodeRef());
                nv.setName(n.getName());
                nv.setDeps(n.getDeps() == null ? new ArrayList<>() : n.getDeps());
                nv.setParams(n.getParams());
                nv.setIsSource(n.getDeps() == null || n.getDeps().isEmpty());
                AiTool tool = findTool(n.getNodeRef());
                if (tool != null) {
                    nv.setToolName(tool.getToolName());
                    nv.setInputType(tool.getInputType());
                    nv.setOutputType(tool.getOutputType());
                    // 节点卡副标题与图标：来自工具表，前端无需硬编码工具清单
                    nv.setDescription(tool.getDescription());
                    nv.setIcon(tool.getIcon());
                }
                nodeVOs.add(nv);
            }
            vo.setNodes(nodeVOs);
        }
        return vo;
    }

    private WorkflowRunVO toRunVO(WorkflowRun r, String workflowName) {
        WorkflowRunVO vo = new WorkflowRunVO();
        vo.setRunId(r.getRunId());
        vo.setWorkflowId(r.getWorkflowId());
        vo.setWorkflowName(workflowName);
        vo.setStatus(r.getStatus());
        vo.setStatusLabel(WorkflowRunStatusEnum.labelOf(r.getStatus() == null ? -1 : r.getStatus()));
        vo.setSuccessCount(r.getSuccessCount());
        vo.setFailCount(r.getFailCount());
        vo.setMaxDepth(r.getMaxDepth());
        vo.setDuration(r.getDuration());
        vo.setErrorMsg(r.getErrorMsg());
        vo.setCreateTime(r.getCreateTime() == null ? null : r.getCreateTime().format(FMT));
        vo.setFinishedAt(r.getFinishedAt() == null ? null : r.getFinishedAt().format(FMT));
        vo.setInputSnapshot(r.getInputSnapshot());
        vo.setInputFileNames(r.getInputFileNames());
        vo.setTriggerType(TRIGGER_MANUAL);
        vo.setTriggerTypeLabel("手动运行");
        if (workflowOutputService != null) {
            try {
                // 【待办】这里恒为空：工作流来源的文件类产物尚未落库
                //   （ToolOutputService.saveFileOutput 零调用方，接入点见 NodeExecutor 的逐文件循环）。
                //   等那条链路接通后，本行才会返回真实产物列表。
                vo.setOutputs(workflowOutputService.listByRunId(r.getRunId()));
            } catch (Exception e) {
                // 产物查询失败不影响运行记录的正常展示
                log.warn("查询工作流产物失败 runId={}", r.getRunId(), e);
            }
        }
        if (r.getNodeResults() != null && !r.getNodeResults().isBlank()) {
            try {
                Map<String, WorkflowNodeResult> parsed = objectMapper.readValue(r.getNodeResults(),
                        new TypeReference<LinkedHashMap<String, WorkflowNodeResult>>() {});
                fillNodeNames(parsed, r.getWorkflowId());
                vo.setNodeResults(parsed);
            } catch (Exception e) {
                // 传异常对象以保留堆栈：原实现只打 message，数据损坏时无法定位
                log.warn("解析 node_results 失败 runId={}", r.getRunId(), e);
            }
        }
        return vo;
    }

    /**
     * 为 nodeResults 回填 nodeName（节点中文名）。
     * <p>
     * node_results JSON 只按 nodeId 索引、不存节点名，故出参时补齐，前端结果面板
     * 直接展示「文档提取」而非 n1。取值优先级：
     * <ol>
     *   <li>sys_workflow.nodes 里该 nodeId 的 name（编排时定的显示名，最准）；</li>
     *   <li>工具名 sys_aitools_tool.tool_name（节点未命名时兜底）；</li>
     *   <li>都没有 → 保持 null，由前端回退显示 nodeId（不用占位符）。</li>
     * </ol>
     * 工作流被删除或节点被改名时仍能靠工具名兜底，不会因历史记录缺名而报错。
     */
    private void fillNodeNames(Map<String, WorkflowNodeResult> parsed, String workflowId) {
        if (parsed == null || parsed.isEmpty()) {
            return;
        }
        Map<String, String> nameByNodeId = buildNodeNameMap(null, workflowId);
        for (Map.Entry<String, WorkflowNodeResult> e : parsed.entrySet()) {
            WorkflowNodeResult nr = e.getValue();
            if (nr == null) {
                continue;
            }
            nr.setNodeName(nameByNodeId.get(e.getKey()));
        }
    }

    /**
     * 构建 nodeId → 节点显示名的索引（同步出参与 SSE 推送共用同一套取值规则）。
     * <p>
     * 取值优先级：sys_workflow.nodes 的 name（编排时定的显示名，最准）
     * → 工具名 sys_aitools_tool.tool_name（未命名时兜底）
     * → 都没有则 map 里无此 key，调用方得到 null，由前端回退显示 nodeId。
     * <p>
     * 整张 map 只建一次：SSE 多文件多节点逐帧推送，若每帧查库会退化成 N×M 次查询。
     *
     * @param knownNodes 已解析好的节点定义；为 null 时按 workflowId 自行查库
     */
    private Map<String, String> buildNodeNameMap(List<WorkflowNode> knownNodes, String workflowId) {
        List<WorkflowNode> defNodes = knownNodes;
        // 工作流可能已被删除，查不到时全部留 null（前端回退显示 nodeId）
        if (defNodes == null) {
            defNodes = new ArrayList<>();
            try {
                LambdaQueryWrapper<Workflow> qw = new LambdaQueryWrapper<>();
                qw.eq(Workflow::getWorkflowId, workflowId).last("LIMIT 1");
                Workflow wf = workflowMapper.selectOne(qw);
                if (wf != null) {
                    defNodes = parseNodes(wf.getNodes());
                }
            } catch (Exception e) {
                log.warn("构建节点名索引失败 workflowId={}", workflowId, e);
                return new HashMap<>();
            }
        }

        // 一次遍历建两张索引，避免按节点重复查库
        Map<String, String> nameByNodeId = new HashMap<>();
        Map<String, String> toolNameByNodeId = new HashMap<>();
        for (WorkflowNode n : defNodes) {
            if (n.getNodeId() == null) {
                continue;
            }
            if (n.getName() != null && !n.getName().isBlank()) {
                nameByNodeId.put(n.getNodeId(), n.getName());
            }
            if (n.getNodeRef() != null) {
                AiTool t = findTool(n.getNodeRef());
                if (t != null && t.getToolName() != null) {
                    toolNameByNodeId.put(n.getNodeId(), t.getToolName());
                }
            }
        }

        Map<String, String> merged = new HashMap<>(toolNameByNodeId);
        merged.putAll(nameByNodeId);   // 显示名优先于工具名
        return merged;
    }

    private AiTool findTool(String toolCode) {
        LambdaQueryWrapper<AiTool> w = new LambdaQueryWrapper<>();
        w.eq(AiTool::getToolCode, toolCode).last("LIMIT 1");
        return aiToolMapper.selectOne(w);
    }

    private List<WorkflowNode> parseNodes(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkflowNode>>() {});
        } catch (Exception e) {
            // 脱敏：Jackson 异常 message 含目标类全限定名与字段名，只进日志
            log.error("节点结构解析失败", e);
            throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "工作流节点数据有误，请检查后重试");
        }
    }

    /**
     * 取本次请求带的「源节点输入文件原始名」。
     * <p>
     * 老数据/老前端不传该字段时返回 null（不是空 Map）：
     * 空 Map 会被序列化成 "{}" 落库，让「没传」和「传了但是空」在库里无法区分。
     *
     * @return nodeId → 文件名数组；未传时为 null
     */
    private Map<String, List<String>> sourceFileNames(WorkflowRunRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, List<String>> names = request.getInputFileNames();
        return (names == null || names.isEmpty()) ? null : names;
    }

    /**
     * 原始文件名序列化成 JSON 落库；未传时返回 null（该列留空，前端回落到从 URL 反解）。
     */
    private String writeFileNamesJson(Map<String, List<String>> fileNames) {
        return fileNames == null ? null : writeJson(fileNames);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.error("工作流序列化失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage());
        }
    }
}
