package com.example.aitools.agent;

import com.example.aitools.agent.dto.AgentStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能体步骤 JSON 编解码测试。
 *
 * <p><b>为什么值得测</b>：步骤里存的是**工具原样输出**，
 * 里面出现引号、反斜杠、换行、中文、Emoji 都是常态 ——
 * 转义错了，整条运行记录就废了（前端详情页会显示成乱码或直接解析失败）。
 */
class AgentStepsCodecTest {

    private static AgentStep sample() {
        AgentStep s = new AgentStep();
        s.setSeq(1);
        s.setThought("用户要总结这份文档，先解析");
        s.setTool("doc-keypoint-extract");
        s.setArgs("{\"file\":\"file_1\"}");
        s.setOutput("第一段\n第二段含\"引号\"和\\反斜杠\\，还有中文标点。");
        s.setOutputTruncated(false);
        s.setCostMs(1234L);
        s.setErrorMsg(null);
        return s;
    }

    @Test
    @DisplayName("空列表 → 空数组；null → 空数组")
    void emptyToEmptyArray() {
        assertEquals("[]", AgentStepsCodec.toJson(null));
        assertEquals("[]", AgentStepsCodec.toJson(new ArrayList<>()));
    }

    @Test
    @DisplayName("编解码往返后字段完全一致")
    void roundTrip() {
        String json = AgentStepsCodec.toJson(List.of(sample()));
        List<AgentStep> back = AgentStepsCodec.fromJson(json);
        assertEquals(1, back.size());
        AgentStep s = back.get(0);
        assertEquals(1, s.getSeq());
        assertEquals("用户要总结这份文档，先解析", s.getThought());
        assertEquals("doc-keypoint-extract", s.getTool());
        assertEquals("{\"file\":\"file_1\"}", s.getArgs());
        assertEquals("第一段\n第二段含\"引号\"和\\反斜杠\\，还有中文标点。", s.getOutput());
        assertFalse(s.getOutputTruncated());
        assertEquals(1234L, s.getCostMs());
        assertNull(s.getErrorMsg());
    }

    @Test
    @DisplayName("换行 / 引号 / 反斜杠 / Emoji 都能原样往返")
    void specialCharsRoundTrip() {
        AgentStep s = new AgentStep();
        s.setSeq(7);
        s.setOutput("换行\n换行\r\n回车\t制表符 \"双引号\" \\反斜杠\\ 😀🎉 中文");
        s.setOutputTruncated(true);

        List<AgentStep> back = AgentStepsCodec.fromJson(AgentStepsCodec.toJson(List.of(s)));
        assertEquals(1, back.size());
        assertEquals(s.getOutput(), back.get(0).getOutput());
        assertTrue(back.get(0).getOutputTruncated());
    }

    @Test
    @DisplayName("多步骤顺序保持")
    void multipleStepsKeepOrder() {
        List<AgentStep> list = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            AgentStep s = new AgentStep();
            s.setSeq(i);
            s.setTool("tool-" + i);
            list.add(s);
        }
        List<AgentStep> back = AgentStepsCodec.fromJson(AgentStepsCodec.toJson(list));
        assertEquals(5, back.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("tool-" + (i + 1), back.get(i).getTool());
            assertEquals(i + 1, back.get(i).getSeq());
        }
    }

    @Test
    @DisplayName("解析非法 JSON → 空列表（不抛异常，详情页显示空而不是 500）")
    void invalidJsonReturnsEmpty() {
        assertTrue(AgentStepsCodec.fromJson("{不是 json").isEmpty());
        assertTrue(AgentStepsCodec.fromJson("").isEmpty());
        assertTrue(AgentStepsCodec.fromJson(null).isEmpty());
        // 不是数组也当空
        assertTrue(AgentStepsCodec.fromJson("{\"a\":1}").isEmpty());
    }
}
