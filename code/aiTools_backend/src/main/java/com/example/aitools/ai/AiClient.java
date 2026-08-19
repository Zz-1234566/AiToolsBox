package com.example.aitools.ai;

import com.example.aitools.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek API 客户端（OpenAI 兼容接口）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final AiConfig aiConfig;

    private final ObjectMapper objectMapper;

    /**
     * 调用 DeepSeek 对话接口
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return AI 返回的文本内容
     */
    public String chat(String systemPrompt, String userPrompt) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // 构建请求体（OpenAI 兼容格式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("stream", false);

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.add(sysMsg);
            }
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);
            requestBody.put("messages", messages);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiConfig.getApiKey());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    aiConfig.getApiUrl(), HttpMethod.POST, entity, String.class);

            // 解析响应
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI 返回结果为空");
            }
            JsonNode message = choices.get(0).get("message");
            String content = message.get("content").asText();
            log.info("AI response length: {}", content == null ? 0 : content.length());
            return content;
        } catch (Exception e) {
            log.error("AI 调用失败", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 流式调用 DeepSeek（SSE），每收到一个内容块回调 onChunk
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param onChunk 收到内容块时的回调
     */
    public void chatStream(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // 构建请求体（OpenAI 兼容格式，stream=true）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("stream", true);

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.add(sysMsg);
            }
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);
            requestBody.put("messages", messages);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiConfig.getApiKey());

            // execute(url, method, requestCallback, responseExtractor)：
            // 在 RequestCallback 中把序列化后的请求体写入输出流（确保 body 真正发送），
            // 在 ResponseExtractor 中逐行读取 SSE 流并回调 onChunk
            restTemplate.execute(aiConfig.getApiUrl(), HttpMethod.POST,
                    request -> {
                        request.getHeaders().putAll(headers);
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        byte[] body = objectMapper.writeValueAsBytes(requestBody);
                        request.getHeaders().setContentLength(body.length);
                        try (OutputStream os = request.getBody()) {
                            os.write(body);
                        }
                    },
                    response -> {
                        // 逐行读取 SSE
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    String data = line.substring(5).trim();
                                    if ("[DONE]".equals(data)) {
                                        break;
                                    }
                                    try {
                                        JsonNode node = objectMapper.readTree(data);
                                        JsonNode choices = node.get("choices");
                                        if (choices != null && !choices.isEmpty()) {
                                            JsonNode delta = choices.get(0).get("delta");
                                            if (delta != null) {
                                                JsonNode contentNode = delta.get("content");
                                                if (contentNode != null && !contentNode.isNull()) {
                                                    String content = contentNode.asText();
                                                    if (!content.isEmpty()) {
                                                        onChunk.accept(content);
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("SSE 解析跳过异常行: {}", e.getMessage());
                                    }
                                }
                            }
                        }
                        return null;
                    });

            log.info("AI stream completed");
        } catch (Exception e) {
            log.error("AI 流式调用失败", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }
}
