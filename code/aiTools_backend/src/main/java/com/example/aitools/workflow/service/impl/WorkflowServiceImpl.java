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

    private final WorkflowMapper workflowMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final AiToolMapper aiToolMapper;
    private final WorkflowValidator workflowValidator;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    // ==================== 保存 ====================

    @Override
    @Transactional
    public String save(Long userId, String workflowId, String name, String description, String nodesJson) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "工作流名称不能为空");
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
            WorkflowVO vo = toVO(wf, false);
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
                    throw new BusinessException("节点「" + n.getName() + "」缺少输入，请先填写或上传");
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
            er = workflowEngine.execute(plan, sourceInputs);
        } catch (Exception e) {
            log.error("[workflow] 运行异常 workflowId={}", workflowId, e);
            fatal = e.getMessage();
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
        if (r == null) throw new BusinessException("运行记录不存在");
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
        if (wf == null) throw new BusinessException("工作流不存在");
        if (!Objects.equals(wf.getUserId(), userId)) throw new BusinessException("无权访问该工作流");
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
        if (r.getNodeResults() != null && !r.getNodeResults().isBlank()) {
            try {
                vo.setNodeResults(objectMapper.readValue(r.getNodeResults(),
                        new TypeReference<LinkedHashMap<String, WorkflowNodeResult>>() {}));
            } catch (Exception e) {
                log.warn("解析 node_results 失败: {}", e.getMessage());
            }
        }
        return vo;
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
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "节点结构解析失败：" + e.getMessage());
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException("序列化失败：" + e.getMessage());
        }
    }
}
