package com.example.aitools.workflow.engine;

import com.example.aitools.common.ResultCode;
import com.example.aitools.common.Constants;
import com.example.aitools.common.NodeIoTypeEnum;
import com.example.aitools.entity.AiTool;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.mapper.AiToolMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.example.aitools.workflow.dto.WorkflowNode;

import java.util.*;

/**
 * 工作流校验器：结构校验 + 拓扑分层 + 类型匹配 + 深度校验。
 * <p>
 * 供「保存工作流」与「运行工作流」两处共用，保证同一套规则（避免前端校验过了、后端仍放行脏数据）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowValidator {

    private final AiToolMapper aiToolMapper;

    /**
     * 校验结果：分层后的执行计划。
     */
    public static class Plan {
        /** 按层分组：levels.get(0) = 第1层（源节点），依此类推 */
        public final List<List<WorkflowNode>> levels = new ArrayList<>();
        /** nodeId → 节点 */
        public final Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();

        public int depth() {
            return levels.size();
        }
    }

    /**
     * 校验并生成执行计划（拓扑分层）。
     *
     * @throws BusinessException 结构非法 / 依赖环 / 深度超限 / 类型不匹配 / 工具不存在
     */
    public Plan validateAndPlan(List<WorkflowNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "工作流至少需要一个节点");
        }

        Plan plan = new Plan();
        // 1) 基础结构：nodeId 唯一、nodeRef 非空、deps 指向存在的节点
        for (WorkflowNode n : nodes) {
            if (isBlank(n.getNodeId())) throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "工作流节点数据不完整，请重新编辑保存");
            if (plan.nodeMap.containsKey(n.getNodeId())) {
                throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "工作流节点编号重复，请重新编辑保存");
            }
            if (isBlank(n.getNodeRef())) throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "工作流存在未选择工具的节点，请检查后重试");
            plan.nodeMap.put(n.getNodeId(), n);
        }
        for (WorkflowNode n : nodes) {
            if (n.getDeps() == null) n.setDeps(new ArrayList<>());
            for (String dep : n.getDeps()) {
                if (!plan.nodeMap.containsKey(dep)) {
                    throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "工作流存在无效的上游依赖，请重新编辑保存");
                }
                if (dep.equals(n.getNodeId())) {
                    throw new BusinessException(ResultCode.WORKFLOW_CYCLE.getCode(), "节点不能依赖自己，请检查上游设置");
                }
            }
        }

        // 2) 拓扑分层：level(节点) = max(上游 level) + 1；源节点 level = 1
        //    用「已定级节点数」驱动，能同时检出环（存在环时有节点永远无法定级）
        Map<String, Integer> levelOf = new HashMap<>();
        for (WorkflowNode n : nodes) {
            if (n.getDeps().isEmpty()) levelOf.put(n.getNodeId(), 1);
        }
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (WorkflowNode n : nodes) {
                if (levelOf.containsKey(n.getNodeId())) continue;
                // 所有上游都已定级才能定级
                if (!n.getDeps().stream().allMatch(levelOf::containsKey)) continue;
                int level = n.getDeps().stream().mapToInt(levelOf::get).max().orElse(0) + 1;
                levelOf.put(n.getNodeId(), level);
                progressed = true;
            }
        }
        if (levelOf.size() != nodes.size()) {
            throw new BusinessException(ResultCode.WORKFLOW_CYCLE.getCode(), "工作流存在循环依赖，无法确定执行顺序");
        }

        // 3) 按 level 分组
        int maxLevel = levelOf.values().stream().max(Integer::compareTo).orElse(0);
        for (int i = 1; i <= maxLevel; i++) {
            plan.levels.add(new ArrayList<>());
        }
        for (WorkflowNode n : nodes) {
            plan.levels.get(levelOf.get(n.getNodeId()) - 1).add(n);
        }

        // 4) 深度约束（≤ 5 层）
        if (plan.depth() > Constants.WORKFLOW_MAX_DEPTH) {
            throw new BusinessException(ResultCode.WORKFLOW_DEPTH_EXCEEDED.getCode(),
                    "工作流层数 " + plan.depth() + " 超过上限 " + Constants.WORKFLOW_MAX_DEPTH + " 层");
        }

        // 5) 类型匹配：上游 output_type ⊆ 下游 input_type
        for (WorkflowNode n : nodes) {
            AiTool downstream = findByCode(n.getNodeRef());
            for (String depId : n.getDeps()) {
                AiTool upstream = findByCode(plan.nodeMap.get(depId).getNodeRef());
                if (!NodeIoTypeEnum.canConnect(upstream.getOutputType(), downstream.getInputType())) {
                    throw new BusinessException(ResultCode.WORKFLOW_TYPE_MISMATCH.getCode(), "节点连线类型不匹配："
                            + upstream.getToolName() + "(" + upstream.getOutputType() + ")"
                            + " → " + downstream.getToolName() + "(" + downstream.getInputType() + ")");
                }
            }
        }
        return plan;
    }

    /** 计算层数（保存工作流时写入冗余字段 max_depth） */
    public int depthOf(List<WorkflowNode> nodes) {
        return validateAndPlan(nodes).depth();
    }

    private AiTool findByCode(String toolCode) {
        LambdaQueryWrapper<AiTool> w = new LambdaQueryWrapper<>();
        w.eq(AiTool::getToolCode, toolCode).last("LIMIT 1");
        AiTool tool = aiToolMapper.selectOne(w);
        if (tool == null) throw new BusinessException(ResultCode.WORKFLOW_INVALID.getCode(), "节点引用的工具不存在，请重新选择工具");
        return tool;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
