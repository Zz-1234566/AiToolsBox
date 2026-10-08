package com.example.aitools.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 4 个 Handler 的注册表，按 bean name 取。
 * <p>
 * 当前注册：
 * <ul>
 *     <li>sse-text-single   → 单文件 SSE 长文本（work-summary / weekly-report / meeting-minutes / ocr-recognize 单图等）</li>
 *     <li>json-single       → 单文件 JSON 结构化（meeting-minutes 试点）</li>
 *     <li>sse-text-batch    → 多文件 SSE 长文本（doc-keypoint / ocr-recognize / ai-file-reader 批量）</li>
 *     <li>json-batch        → 多文件 JSON 结构化（暂未启用）</li>
 * </ul>
 */
@Slf4j
@Component
public class HandlerFactory {

    /** Spring 自动注入：所有 AiToolHandler 类型 Bean，key = bean name */
    private final Map<String, AiToolHandler> registry;

    @Autowired
    public HandlerFactory(Map<String, AiToolHandler> handlerMap) {
        this.registry = new HashMap<>(handlerMap);
        log.info("[HandlerFactory] 注册 {} 个 handler：{}", registry.size(), registry.keySet());
    }

    public AiToolHandler get(String name) {
        AiToolHandler h = registry.get(name);
        if (h == null) {
            throw new IllegalArgumentException("未注册的 handler：" + name + "（已注册：" + registry.keySet() + "）");
        }
        return h;
    }

    /**
     * 便捷：通过 (handlerType, batched) 自动取
     * <p>handlerType = "sse-text" | "json"
     * <br>batched = true/false
     * <br>实际 key = handlerType + "-single"/"-batch"
     */
    public AiToolHandler get(String handlerType, boolean batch) {
        String key = handlerType.toLowerCase() + (batch ? "-batch" : "-single");
        return get(key);
    }
}