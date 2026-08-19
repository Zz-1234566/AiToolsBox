package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.deepseek")
public class AiConfig {
    private String apiUrl;
    private String apiKey;
    private String model;
    private int timeoutSeconds = 60;
}
