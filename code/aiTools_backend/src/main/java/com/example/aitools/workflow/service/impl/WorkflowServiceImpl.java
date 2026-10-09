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
import com.example.aitools.service.ToolOutputService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
        workflowRunMapper.insert(run);

        // 执行
        WorkflowEngine.ExecutionResult er;
        String fatal = null;
        try {
            er = workflowEngine.execute(plan, sourceInputs, userId, run.getRunId());
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
        workflowRunMapper.insert(run);

        // 3) 异步执行 + 实时推送
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
                        plan, sourceInputs, new WorkflowEngine.ProgressListener() {
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
                        }, userId, run.getRunId());

                // 4) 落库终态
                run.setStatus(er.status);
                run.setNodeResults(writeJson(er.nodeResults));
                run.setSuccessCount(er.successCount);
                run.setFailCount(er.failCount);
                run.setMaxDepth(er.depth);
                run.setDuration(er.duration);
                run.setFinishedAt(LocalDateTime.now());
                workflowRunMapper.updateById(run);

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
    }

    /** 发送一帧 SSE；失败只记日志，不中断主流程（客户端可能已断开） */
    private void sendEvent(org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter,
                           Map<String, ?> payload) {
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event().data(writeJson(payload)));
        } catch (Exception e) {
            log.warn("[workflow] SSE 推送失败（客户端可能已断开）: {}", e.getMessage());
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
        vo.setTriggerType(TRIGGER_MANUAL);
        vo.setTriggerTypeLabel("手动运行");
        if (workflowOutputService != null) {
            try {
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

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.error("工作流序列化失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage());
        }
    }
}
