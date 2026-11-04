package com.example.aitools.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能体协议解析测试。
 *
 * <p><b>为什么这个测试重要</b>：模型输出 JSON 是**不确定的** —— 可能裹代码围栏、
 * 可能前后带废话、可能用中文引号、可能干脆不输出 JSON。
 * 解析器是「模型不听话」与「智能体可用」之间的最后一道防线，
 * 它的容错行为必须被钉死，否则线上会出现「模型明明答了，页面却报解析失败」。
 */
class AgentActionParserTest {

    // ==================== 正常形态 ====================

    @Test
    @DisplayName("标准 tool_call JSON 能解析出 thought/tool/args")
    void parseToolCall() {
        String raw = "{\"thought\":\"先转写录音\",\"action\":\"tool_call\","
                + "\"tool\":\"audio-transcribe\",\"args\":{\"file\":\"file_1\"}}";
        AgentAction a = AgentActionParser.parse(raw);
        assertNotNull(a);
        assertEquals(AgentAction.ACTION_TOOL_CALL, a.getAction());
        assertEquals("audio-transcribe", a.getTool());
        assertEquals("先转写录音", a.getThought());
        assertEquals("file_1", a.getArgs().get("file"));
    }

    @Test
    @DisplayName("标准 final JSON 能解析出 output")
    void parseFinal() {
        String raw = "{\"thought\":\"好了\",\"action\":\"final\",\"output\":\"会议纪要正文\"}";
        AgentAction a = AgentActionParser.parse(raw);
        assertNotNull(a);
        assertEquals(AgentAction.ACTION_FINAL, a.getAction());
        assertEquals("会议纪要正文", a.getOutput());
    }

    @Test
    @DisplayName("args 缺失时不报错（解析成空 map）")
    void parseWithoutArgs() {
        AgentAction a = AgentActionParser.parse("{\"action\":\"tool_call\",\"tool\":\"x\"}");
        assertNotNull(a);
        assertNotNull(a.getArgs());
        assertTrue(a.getArgs().isEmpty());
    }

    // ==================== 容错：模型输出的各种脏形态 ====================

    @Test
    @DisplayName("裹 ```json 代码围栏能解析")
    void parseWithCodeFence() {
        String raw = "```json\n{\"action\":\"final\",\"output\":\"答案\"}\n```";
        AgentAction a = AgentActionParser.parse(raw);
        assertNotNull(a);
        assertEquals("答案", a.getOutput());
    }

    @Test
    @DisplayName("JSON 前后有废话也能解析（取第一个 {...} 块）")
    void parseWithNoise() {
        String raw = "好的，我来帮你分析。\n{\"action\":\"final\",\"output\":\"答案\"}\n希望对你有帮助！";
        AgentAction a = AgentActionParser.parse(raw);
        assertNotNull(a);
        assertEquals("答案", a.getOutput());
    }

    @Test
    @DisplayName("字符串里含花括号不会截断（按括号配对扫描，不是简单 indexOf）")
    void parseWithBraceInsideString() {
        String raw = "{\"action\":\"final\",\"output\":\"包含 {花括号} 的正文\"}";
        AgentAction a = AgentActionParser.parse(raw);
        assertNotNull(a);
        assertEquals("包含 {花括号} 的正文", a.getOutput());
    }

    @Test
    @DisplayName("完全不是 JSON → 返回 null（调用方负责重试/报错，不抛异常）")
    void parseGarbageReturnsNull() {
        assertNull(AgentActionParser.parse("我不知道该用什么工具"));
        assertNull(AgentActionParser.parse(""));
        assertNull(AgentActionParser.parse(null));
    }

    @Test
    @DisplayName("JSON 合法但没有 action 字段 → 返回 null（动作类型无法判定）")
    void parseWithoutActionReturnsNull() {
        assertNull(AgentActionParser.parse("{\"thought\":\"想一下\"}"));
    }

    @Test
    @DisplayName("action 是未知类型 → 解析出来但校验不通过（要能报出「未知动作类型」而不是笼统的解析失败）")
    void parseUnknownActionIsInvalid() {
        AgentAction a = AgentActionParser.parse("{\"action\":\"delete_everything\"}");
        assertNotNull(a, "未知 action 类型应保留下来，调用方据此给出可诊断的错误");
        assertEquals("delete_everything", a.getAction());
        assertTrue(!AgentActionParser.isValid(a), "未知 action 类型必须判为无效");
    }

    @Test
    @DisplayName("action 大小写不敏感")
    void parseActionCaseInsensitive() {
        AgentAction a = AgentActionParser.parse("{\"action\":\"FINAL\",\"output\":\"x\"}");
        assertNotNull(a);
        assertEquals(AgentAction.ACTION_FINAL, a.getAction());
    }

    // ==================== 协议完整性校验 ====================

    @Test
    @DisplayName("tool_call 缺 tool → 校验不通过")
    void toolCallWithoutToolInvalid() {
        AgentAction a = AgentActionParser.parse("{\"action\":\"tool_call\",\"args\":{}}");
        assertNotNull(a);
        assertTrue(!AgentActionParser.isValid(a), "缺 tool 的 tool_call 应判为无效");
    }

    @Test
    @DisplayName("final 缺 output → 校验不通过")
    void finalWithoutOutputInvalid() {
        AgentAction a = AgentActionParser.parse("{\"action\":\"final\"}");
        assertNotNull(a);
        assertTrue(!AgentActionParser.isValid(a), "缺 output 的 final 应判为无效");
    }

    @Test
    @DisplayName("完整的 tool_call / final 都通过校验")
    void validActionsPass() {
        assertTrue(AgentActionParser.isValid(
                AgentActionParser.parse("{\"action\":\"tool_call\",\"tool\":\"t\",\"args\":{}}")));
        assertTrue(AgentActionParser.isValid(
                AgentActionParser.parse("{\"action\":\"final\",\"output\":\"答案\"}")));
    }

    @Test
    @DisplayName("args 里的值是字符串/数字/布尔都能解析")
    void argsValueTypes() {
        AgentAction a = AgentActionParser.parse(
                "{\"action\":\"tool_call\",\"tool\":\"t\",\"args\":{\"s\":\"x\",\"n\":1,\"b\":true}}");
        assertNotNull(a);
        Map<String, Object> args = a.getArgs();
        assertEquals("x", args.get("s"));
        assertEquals(1, ((Number) args.get("n")).intValue());
        assertEquals(Boolean.TRUE, args.get("b"));
    }
}
