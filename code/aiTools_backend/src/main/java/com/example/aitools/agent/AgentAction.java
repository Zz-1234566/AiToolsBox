package com.example.aitools.agent;

import lombok.Data;

import java.util.Map;

/**
 * 智能体的一步动作（模型输出的解析结果）。
 *
 * <p><b>协议</b>：模型被要求「只输出一个 JSON 对象」，形态二选一：
 * <pre>
 * {"thought":"先转写录音","action":"tool_call","tool":"audio-transcribe","args":{"file":"file_1"}}
 * {"thought":"文字已就绪","action":"final","output":"会议纪要正文……"}
 * </pre>
 *
 * <p>用 {@code action} 区分两种形态，而不是靠「有没有 output 字段」猜 ——
 * 后者会让「模型漏写 output」这种错误被静默当成工具调用。
 */
@Data
public class AgentAction {

    /** 动作类型：tool_call（调工具）/ final（给最终答案） */
    public static final String ACTION_TOOL_CALL = "tool_call";
    public static final String ACTION_FINAL = "final";

    /** 模型的思考过程（展示给用户，不参与执行） */
    private String thought;

    /** 动作类型：tool_call / final */
    private String action;

    /** 工具编码（action=tool_call 时必填） */
    private String tool;

    /** 工具参数（action=tool_call 时） */
    private Map<String, Object> args;

    /** 最终答案（action=final 时必填） */
    private String output;
}
