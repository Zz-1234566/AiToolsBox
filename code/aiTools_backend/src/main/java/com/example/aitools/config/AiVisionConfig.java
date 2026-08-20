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
}
