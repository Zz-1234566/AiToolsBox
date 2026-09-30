package com.example.aitools.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 文本模型（OpenAI 兼容）配置。
 * <p>
 * 同时提供带超时的 {@link RestTemplate}——见 {@link #timeoutSeconds}。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "ai.minimax-text")
public class AiConfig {
    private String apiUrl;
    private String apiKey;
    private String model;

    /** 读取超时（秒），默认 60。防止 AI 端半开连接时线程永久阻塞 */
    private int timeoutSeconds = 60;

    /** 连接超时（秒），固定 10，不可配（连接阶段不应长时间等待） */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /**
     * 专用于 AI 调用的 RestTemplate：带连接/读取超时。
     * <p>
     * 关键：Spring 默认 {@code RestTemplate} 的超时为 0（无限）。AI 端出现半开连接
     * （TCP 未断开但不再发数据）时，流式 {@code readLine()} 会永久阻塞，逐个占满
     * SSE 线程池，最终全体用户流式功能不可用，且不产生任何异常与日志。
     */
    @Bean
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_SECONDS * 1000);
        factory.setReadTimeout(Math.max(timeoutSeconds, 1) * 1000);
        log.info("AI RestTemplate initialized: connectTimeout={}s, readTimeout={}s",
                CONNECT_TIMEOUT_SECONDS, timeoutSeconds);
        return new RestTemplate(factory);
    }
}

