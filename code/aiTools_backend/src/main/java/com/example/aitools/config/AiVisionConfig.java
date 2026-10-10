package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MiniMax M3 多模态配置（AI 文件解读工具用）
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.minimax")
public class AiVisionConfig {
    private String apiUrl;
    private String apiKey;
    private String model = "MiniMax-M3";

    /** 连接超时（秒），默认 10。与 {@link AiConfig} 的连接超时保持一致：连接阶段不应长时间等待 */
    private int connectTimeoutSeconds = 10;

    /**
     * 读取超时（秒），默认 120。
     * <p>
     * MiniMax M3 读多模态大图/长文档时推理较慢，故给到 120s；
     * 但**必须有上限**：{@code HttpURLConnection} 不设超时 = 永久阻塞，
     * 上游半开连接（TCP 未断开但不再发数据）时会把 batchExecutor 线程一个个吃光，
     * 最终整个 AI 功能不可用且不产生任何异常与日志。
     */
    private int readTimeoutSeconds = 120;
}
