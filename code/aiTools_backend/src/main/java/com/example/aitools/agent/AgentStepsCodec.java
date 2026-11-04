package com.example.aitools.agent;

import com.example.aitools.agent.dto.AgentStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能体步骤 JSON 的编解码（存 {@code sys_agent_run.steps}）。
 *
 * <p><b>为什么单独成类</b>：编解码是**纯逻辑**，藏在一个 Spring Bean 的私有方法里
 * 就没法单测 —— 而「存进去再读出来要一样」这件事恰恰最值得测
 * （工具结果里带引号、换行、中文都很常见，转义错了整条记录就废了）。
 */
@Slf4j
public final class AgentStepsCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentStepsCodec() {
    }

    /**
     * 步骤列表 → JSON 数组字符串。
     */
    public static String toJson(List<AgentStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(steps);
        } catch (Exception e) {
            log.warn("[agent] 步骤序列化失败，退化为空数组", e);
            return "[]";
        }
    }

    /**
     * JSON 数组字符串 → 步骤列表（解析失败返回空列表，不抛异常）。
     */
    public static List<AgentStep> fromJson(String json) {
        List<AgentStep> list = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return list;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return list;
            }
            for (JsonNode n : root) {
                AgentStep s = new AgentStep();
                s.setSeq(n.has("seq") ? n.get("seq").asInt() : list.size() + 1);
                s.setThought(textOf(n, "thought"));
                s.setTool(textOf(n, "tool"));
                s.setArgs(textOf(n, "args"));
                s.setOutput(textOf(n, "output"));
                s.setOutputTruncated(n.has("outputTruncated") && n.get("outputTruncated").asBoolean());
                s.setCostMs(n.has("costMs") ? n.get("costMs").asLong() : 0L);
                s.setErrorMsg(textOf(n, "errorMsg"));
                list.add(s);
            }
        } catch (Exception e) {
            log.warn("[agent] 步骤 JSON 解析失败", e);
        }
        return list;
    }

    private static String textOf(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
