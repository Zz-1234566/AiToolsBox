package com.example.aitools.workflow.engine;

import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.common.WorkflowRunStatusEnum;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.workflow.dto.WorkflowNode;
import com.example.aitools.workflow.dto.WorkflowNodeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 工作流执行引擎：拓扑分层 + 同层并行 + 数组传递 + 状态机推进。
 * <p>
 * <b>执行模型</b>
 * <pre>
 *   按 deps 拓扑分层：level(节点) = max(上游 level) + 1，源节点 level = 1
 *   同层节点并行执行（上限 Constants.WORKFLOW_MAX_PARALLEL）
 *   上层全部完成才进入下层
 *   单节点输入 = 所有上游输出的「数组拼接」（保持逐项独立，不合并字符串）
 * </pre>
 * <b>数组语义</b>：上游各自输出 N 项，下游收到的是「各项之和」的数组，逐项独立处理，输出等长数组。
 * 例：n1 出 1 项 + n2 出 1 项 → n3 收 2 项 → n3 出 2 项。
 * <p>
 * <b>失败策略</b>：某节点失败 → 其下游标记为「未执行」，其余分支继续跑。整体状态按成功/失败节点数判定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final NodeExecutor nodeExecutor;

    /** 节点级结果状态（复用 WorkflowRunStatusEnum 的编码：2成功 / 4失败） */
    private static final int NODE_SUCCESS = 2;
    private static final int NODE_FAILED = 4;

    /**
     * 执行入口（同步阻塞，调用方负责放到线程池）。
     *
     * @param plan           已校验的执行计划
     * @param sourceInputs   源节点输入：nodeId → 输入数组（文本内容 或 文件路径/dataURL）
     * @return 执行汇总
     */
    public ExecutionResult execute(WorkflowValidator.Plan plan, Map<String, List<String>> sourceInputs) {
        long start = System.currentTimeMillis();

        Map<String, List<String>> outputsByNode = new HashMap<>(); // 已成功节点的输出
        Map<String, WorkflowNodeResult> results = new LinkedHashMap<>();
        Set<String> failedNodes = new HashSet<>();

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(Constants.WORKFLOW_MAX_PARALLEL, Math.max(1, plan.depth())),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("workflow-node-" + t.getId());
                    t.setDaemon(true);
                    return t;
                });
        try {
            for (List<WorkflowNode> level : plan.levels) {
                // 记录每个 Future 对应的 nodeId：Future 级异常（Error/OOM/线程池拒绝）时
                // 必须把该节点记为失败，否则它会从 results 中完全消失，
                // 导致 success=0 且 fail=0 → decideStatus(0,0) 返回 COMPLETED（静默假成功）
                List<Map.Entry<String, Future<LevelTask>>> futures = new ArrayList<>(level.size());
                for (WorkflowNode node : level) {
                    // 上游有失败 → 本节点不执行
                    boolean upstreamFailed = node.getDeps().stream().anyMatch(failedNodes::contains);
                    if (upstreamFailed) {
                        WorkflowNodeResult r = new WorkflowNodeResult();
                        r.setStatus(NODE_FAILED);
                        r.setInputs(new ArrayList<>());
                        r.setOutputs(new ArrayList<>());
                        r.setCostMs(0);
                        r.setErrorMsg("上游节点失败，本节点未执行");
                        results.put(node.getNodeId(), r);
                        failedNodes.add(node.getNodeId());
                        continue;
                    }
                    String nodeId = node.getNodeId();
                    try {
                        futures.add(Map.entry(nodeId, pool.submit(() -> runNode(node, sourceInputs, outputsByNode))));
                    } catch (Exception e) {
                        // 提交失败（线程池饱和/关闭）：同样要记为失败，不能丢
                        log.error("[workflow] 节点提交失败 nodeId={}", nodeId, e);
                        results.put(nodeId, failedResult("节点提交失败，请重试"));
                        failedNodes.add(nodeId);
                    }
                }
                // 等待本层全部完成后再进入下一层
                for (Map.Entry<String, Future<LevelTask>> entry : futures) {
                    String nodeId = entry.getKey();
                    LevelTask t;
                    try {
                        t = entry.getValue().get();
                    } catch (Exception e) {
                        // 关键：不能 continue 跳过——必须落一条失败结果，避免静默假成功
                        log.error("[workflow] 节点任务异常 nodeId={}", nodeId, e);
                        results.put(nodeId, failedResult("节点执行异常，请重试"));
                        failedNodes.add(nodeId);
                        continue;
                    }
                    results.put(t.nodeId, t.result);
                    if (t.result.getStatus() != null && t.result.getStatus() == NODE_SUCCESS) {
                        outputsByNode.put(t.nodeId, t.result.getOutputs());
                    } else {
                        failedNodes.add(t.nodeId);
                    }
                }
            }
        } finally {
            pool.shutdown();
        }

        int success = (int) results.values().stream().filter(r -> Objects.equals(r.getStatus(), NODE_SUCCESS)).count();
        int fail = results.size() - success;
        ExecutionResult er = new ExecutionResult();
        er.nodeResults = results;
        er.successCount = success;
        er.failCount = fail;
        er.depth = plan.depth();
        er.duration = (int) (System.currentTimeMillis() - start);
        er.status = decideStatus(success, fail);
        return er;
    }

    /** 执行单个节点：算输入数组 → 调 NodeExecutor → 组装结果 */
    private LevelTask runNode(WorkflowNode node,
                              Map<String, List<String>> sourceInputs,
                              Map<String, List<String>> outputsByNode) {
        WorkflowNodeResult r = new WorkflowNodeResult();
        String nodeId = node.getNodeId();
        long start = System.currentTimeMillis();
        try {
            List<String> inputs = collectInputs(node, sourceInputs, outputsByNode);
            if (inputs.isEmpty()) {
                throw new BusinessException(ResultCode.WORKFLOW_NODE_FAILED.getCode(), "节点没有可用输入，请检查上游节点");
            }
            r.setStatus(NODE_SUCCESS);
            r.setInputs(inputs);
            List<String> outputs = nodeExecutor.execute(node.getNodeRef(), inputs, node.getParams());
            r.setOutputs(outputs);
            r.setCostMs((int) (System.currentTimeMillis() - start));
            return new LevelTask(nodeId, r);
        } catch (Exception e) {
            // 错误信息脱敏：业务异常本身就是面向用户的文案；
            // 其他异常（IO/HTTP/SDK）的 message 含服务器路径、上游地址等，只进日志
            String userMsg;
            if (e instanceof BusinessException) {
                userMsg = e.getMessage();
                log.warn("[workflow] 节点 {} 执行失败: {}", nodeId, userMsg);
            } else {
                userMsg = ResultCode.WORKFLOW_NODE_FAILED.getMessage();
                log.error("[workflow] 节点 {} 执行异常", nodeId, e);
            }
            r.setStatus(NODE_FAILED);
            r.setInputs(new ArrayList<>());
            r.setOutputs(new ArrayList<>());
            r.setCostMs((int) (System.currentTimeMillis() - start));
            r.setErrorMsg(userMsg);
            return new LevelTask(nodeId, r);
        }
    }

    /**
     * 汇总节点输入：
     * - 源节点（deps 空）→ 取运行时输入 sourceInputs
     * - 有上游 → 按 deps 顺序，把各上游输出数组依次拼接成一个数组（数组语义，不合并为单个字符串）
     */
    private List<String> collectInputs(WorkflowNode node,
                                       Map<String, List<String>> sourceInputs,
                                       Map<String, List<String>> outputsByNode) {
        if (node.getDeps() == null || node.getDeps().isEmpty()) {
            List<String> src = sourceInputs == null ? null : sourceInputs.get(node.getNodeId());
            return src == null ? new ArrayList<>() : new ArrayList<>(src);
        }
        List<String> merged = new ArrayList<>();
        for (String dep : node.getDeps()) {
            List<String> depOut = outputsByNode.get(dep);
            if (depOut != null) merged.addAll(depOut);
        }
        return merged;
    }

    /** 构造一条失败的节点结果（用于 Future 级异常/提交失败等兜底场景） */
    private WorkflowNodeResult failedResult(String errorMsg) {
        WorkflowNodeResult r = new WorkflowNodeResult();
        r.setStatus(NODE_FAILED);
        r.setInputs(new ArrayList<>());
        r.setOutputs(new ArrayList<>());
        r.setCostMs(0);
        r.setErrorMsg(errorMsg);
        return r;
    }

    private int decideStatus(int success, int fail) {
        // 兜底：一个节点都没有结果（如计划为空/全部异常丢失）不能算成功
        if (success == 0 && fail == 0) return WorkflowRunStatusEnum.FAILED.getCode();
        if (fail == 0) return WorkflowRunStatusEnum.COMPLETED.getCode();
        if (success == 0) return WorkflowRunStatusEnum.FAILED.getCode();
        return WorkflowRunStatusEnum.PARTIAL.getCode();
    }

    /** 单节点任务载体 */
    private static class LevelTask {
        final String nodeId;
        final WorkflowNodeResult result;

        LevelTask(String nodeId, WorkflowNodeResult result) {
            this.nodeId = nodeId;
            this.result = result;
        }
    }

    /** 执行汇总 */
    public static class ExecutionResult {
        public Map<String, WorkflowNodeResult> nodeResults;
        public int successCount;
        public int failCount;
        public int depth;
        public int duration;
        public int status;
    }
}
