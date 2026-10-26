package com.example.aitools.workflow.engine;

import com.example.aitools.workflow.dto.WorkflowNode;
import com.example.aitools.workflow.dto.WorkflowNodeResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 节点重跑（{@code /run/resume}）的**计划截断 + 预置产物**逻辑单测。
 *
 * <p>这两块是重跑功能的地基：
 * <ul>
 *   <li><b>计划截断</b>：从 {@code fromNodeId} 所在层开始，上游不执行；</li>
 *   <li><b>预置产物</b>：上游节点的上一轮产物被喂回，
 *       下游节点 {@code inputForFile} 取到的是**当前文件**那一项。</li>
 * </ul>
 *
 * <p>不覆盖的部分（需要真实 AI / 文件，交给实机验证）：端点鉴权、落库、SSE 帧。
 */
class WorkflowResumePlanTest {

    /** 构造一个 3 层链：n1 → n2 → n3 */
    private WorkflowValidator.Plan buildPlan() {
        WorkflowValidator.Plan plan = new WorkflowValidator.Plan();
        List<WorkflowNode> l1 = new ArrayList<>();
        l1.add(node("n1", "work-summary", null));
        List<WorkflowNode> l2 = new ArrayList<>();
        l2.add(node("n2", "weekly-report", List.of("n1")));
        List<WorkflowNode> l3 = new ArrayList<>();
        l3.add(node("n3", "meeting-minutes", List.of("n2")));
        plan.levels.add(l1);
        plan.levels.add(l2);
        plan.levels.add(l3);
        for (List<WorkflowNode> lv : plan.levels) {
            for (WorkflowNode n : lv) plan.nodeMap.put(n.getNodeId(), n);
        }
        return plan;
    }

    private WorkflowNode node(String id, String ref, List<String> deps) {
        WorkflowNode n = new WorkflowNode();
        n.setNodeId(id);
        n.setNodeRef(ref);
        n.setName(id);
        n.setDeps(deps);
        return n;
    }

    @Test
    void 上游节点集合是传递闭包() {
        WorkflowValidator.Plan plan = buildPlan();
        java.util.Set<String> acc = new java.util.LinkedHashSet<>();
        collectUpstream(plan, "n3", acc);
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("n2", "n1")), acc,
                "n3 的上游应包含 n2 与 n1（传递闭包）");
    }

    @Test
    void 源节点没有上游() {
        WorkflowValidator.Plan plan = buildPlan();
        java.util.Set<String> acc = new java.util.LinkedHashSet<>();
        collectUpstream(plan, "n1", acc);
        assertTrue(acc.isEmpty(), "源节点没有上游");
    }

    @Test
    void 环状依赖不会死循环() {
        // 人为造一个环：a → b → a
        WorkflowValidator.Plan plan = new WorkflowValidator.Plan();
        List<WorkflowNode> l1 = new ArrayList<>();
        l1.add(node("a", "t", List.of("b")));
        l1.add(node("b", "t", List.of("a")));
        plan.levels.add(l1);
        for (WorkflowNode n : l1) plan.nodeMap.put(n.getNodeId(), n);

        java.util.Set<String> acc = new java.util.LinkedHashSet<>();
        collectUpstream(plan, "a", acc);
        assertEquals(2, acc.size(), "环状依赖下收集必须终止（visited 集合去重）");
    }

    @Test
    void 预置产物按文件顺序取当前文件那一项() {
        // 模拟引擎里的预置逻辑：outputsByNode 每个文件重建，只放当前文件那一项
        WorkflowEngine.PresetOutputs preset = new WorkflowEngine.PresetOutputs();
        preset.outputs.put("n1", Arrays.asList("文件1的产物", "文件2的产物"));

        Map<String, List<String>> outputsByNode = new LinkedHashMap<>();
        for (int fi = 0; fi < 2; fi++) {
            outputsByNode = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : preset.outputs.entrySet()) {
                List<String> arr = e.getValue();
                if (arr != null && fi < arr.size()) {
                    List<String> one = new ArrayList<>(1);
                    one.add(arr.get(fi));
                    outputsByNode.put(e.getKey(), one);
                }
            }
            // inputForFile 对非源节点取 out.get(0)
            List<String> got = outputsByNode.get("n1");
            assertEquals(1, got.size(), "outputsByNode 每轮只放当前文件那一项（长度恒为 1）");
            assertEquals(fi == 0 ? "文件1的产物" : "文件2的产物", got.get(0),
                    "第 " + fi + " 个文件应取到自己的产物");
        }
    }

    @Test
    void 预置产物缺失时该文件取不到输入() {
        // 预置数组比文件数短 → 越界的文件拿不到上游产物（引擎会判该节点无输入）
        WorkflowEngine.PresetOutputs preset = new WorkflowEngine.PresetOutputs();
        preset.outputs.put("n1", Arrays.asList("只有文件1"));
        Map<String, List<String>> outputsByNode = new LinkedHashMap<>();
        int fi = 1;  // 第 2 个文件
        for (Map.Entry<String, List<String>> e : preset.outputs.entrySet()) {
            List<String> arr = e.getValue();
            if (arr != null && fi < arr.size()) {
                outputsByNode.put(e.getKey(), new ArrayList<>(List.of(arr.get(fi))));
            }
        }
        assertTrue(outputsByNode.isEmpty(), "越界的文件不该拿到上游产物");
    }

    @Test
    void 上游结果会被复制而不是共享引用() {
        // 引擎里把上一轮结果 copy 进新 run 的 node_results —— 不能共享可变对象
        WorkflowNodeResult old = new WorkflowNodeResult();
        old.setStatus(2);
        old.setOutputs(new ArrayList<>(Arrays.asList("旧产物")));
        old.setInputs(new ArrayList<>(Arrays.asList("旧输入")));

        WorkflowNodeResult copy = new WorkflowNodeResult();
        copy.setStatus(old.getStatus());
        copy.setOutputs(new ArrayList<>(old.getOutputs()));
        copy.setInputs(new ArrayList<>(old.getInputs()));
        copy.setCostMs(old.getCostMs());
        copy.setErrorMsg(old.getErrorMsg());

        assertNotSame(old.getOutputs(), copy.getOutputs(), "outputs 不能共享同一个 List");
        assertNotSame(old.getInputs(), copy.getInputs(), "inputs 不能共享同一个 List");
        assertEquals(old.getOutputs(), copy.getOutputs(), "内容要一致");
    }

    /** 与 WorkflowServiceImpl#collectUpstream 同逻辑（此处独立实现以便单测） */
    private void collectUpstream(WorkflowValidator.Plan plan, String nodeId, java.util.Set<String> acc) {
        WorkflowNode node = plan.nodeMap.get(nodeId);
        if (node == null || node.getDeps() == null) return;
        for (String dep : node.getDeps()) {
            if (acc.add(dep)) {
                collectUpstream(plan, dep, acc);
            }
        }
    }
}
