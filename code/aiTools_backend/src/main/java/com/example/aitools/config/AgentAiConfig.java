package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能体（ReAct 循环）专用模型配置。
 *
 * <p>与办公工具的 {@code ai.text} 分开：智能体的 key 来自环境变量
 * {@code AI_OPENCODEGO_API_KEY}（OpenCode Go 套餐），接口是 OpenAI 兼容的
 * {@code /chat/completions}。这样换智能体供应商不需要动数据库里的模型表。
 *
 * <p>未配置 key 时 {@link #isEnabled()} 返回 false，调用方会回退到
 * {@code ModelCredentialResolver} 原有的三级回退（用户默认 / 系统默认）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AgentAiConfig {

    /** OpenAI 兼容的 chat/completions 地址 */
    private String apiUrl;

    /** 明文 API Key（来自环境变量，不入库） */
    private String apiKey;

    /** 模型名，如 deepseek-v4.1-flash / glm-5.3 / kimi-k3 */
    private String model;

    /** 单次请求读取超时（秒）。智能体一轮可能较长，默认 120 */
    private int timeoutSeconds = 120;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank()
                && apiUrl != null && !apiUrl.isBlank()
                && model != null && !model.isBlank();
    }
}
