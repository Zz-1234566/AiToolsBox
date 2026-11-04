package com.example.aitools.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体协议解析：把模型输出的一坨文本解析成 {@link AgentAction}。
 *
 * <p><b>为什么要有专门的解析器</b>：模型输出 JSON 是**不确定**的 ——
 * 可能裹 ```json 围栏、可能前后带废话、可能字符串里含花括号、可能干脆不输出 JSON。
 * 这个类把「脏输入 → 结构化动作」的所有容错集中在一处，
 * 业务代码只面对「拿到 action 或拿到 null」两种结果。
 *
 * <p><b>为什么解析失败返回 null 而不是抛异常</b>：调用方（智能体循环）要能
 * 「把错误回灌给模型让它重试」，而不是直接崩掉整轮运行。
 */
@Slf4j
public final class AgentActionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentActionParser() {
    }

    /**
     * 解析模型输出。
     *
     * @param raw 模型原始输出（可为 null）
     * @return 解析出的动作；**解析不出返回 null**（调用方负责重试 / 报错）
     */
    public static AgentAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = extractJson(raw);
        if (json == null) {
            log.warn("[agent] 模型输出里找不到 JSON 片段，原始输出前 200 字: {}", brief(raw));
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                return null;
            }
            AgentAction action = new AgentAction();
            action.setThought(text(node, "thought"));
            action.setTool(text(node, "tool"));
            action.setOutput(text(node, "output"));

            String act = text(node, "action");
            if (act == null) {
                // 没有 action 字段 → 动作类型无法判定（不要靠「有没有 output」猜）
                return null;
            }
            action.setAction(act.trim().toLowerCase(java.util.Locale.ROOT));

            JsonNode args = node.get("args");
            if (args != null && args.isObject()) {
                Map<String, Object> map = new HashMap<>();
                args.fields().forEachRemaining(e -> map.put(e.getKey(), toPlain(e.getValue())));
                action.setArgs(map);
            } else {
                action.setArgs(new HashMap<>());
            }
            return action;
        } catch (Exception e) {
            log.warn("[agent] 模型输出的 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 动作是否完整可用。
     * <p>
     * tool_call 必须有 tool；final 必须有非空 output。
     * 不完整的动作**不回灌给模型重试**（模型连协议都没遵守，重试也没用），
     * 直接判定本轮失败。
     */
    public static boolean isValid(AgentAction action) {
        if (action == null || action.getAction() == null) {
            return false;
        }
        if (AgentAction.ACTION_TOOL_CALL.equals(action.getAction())) {
            return action.getTool() != null && !action.getTool().isBlank();
        }
        if (AgentAction.ACTION_FINAL.equals(action.getAction())) {
            return action.getOutput() != null && !action.getOutput().isBlank();
        }
        return false;
    }

    /**
     * 从一段文本里截出第一个**配对完整**的 JSON 对象。
     * <p>
     * 用括号配对扫描而不是 {@code indexOf("{")} + {@code indexOf("}")} ——
     * 后者会在字符串里含花括号时截断（如 {@code {"output":"含 {括号} 的正文"}}）。
     */
    static String extractJson(String raw) {
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return raw.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** JSON 值 → Java 原始类型（数字统一转 Number，布尔转 Boolean） */
    private static Object toPlain(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.numberValue();
        }
        if (v.isBoolean()) {
            return v.booleanValue();
        }
        return v.asText();
    }

    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
