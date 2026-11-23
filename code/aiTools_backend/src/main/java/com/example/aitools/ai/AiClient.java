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
 * 文本模型 API 客户端（OpenAI 兼容接口），用于办公类 AI 工具。
 * 端点/模型由 application*.yml 中 ai.text.* 配置（默认指向 OpenCode Go）。
 * <p>
 * 支持 BYOK：调用方传入 {@link ResolvedCredential}（如 user-default）可覆盖 .env 默认 key/url/model。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final AiConfig aiConfig;

    private final ObjectMapper objectMapper;

    /**
     * 带超时的 RestTemplate：连接 10s、读 {ai.text.timeout-seconds}（默认 60s）。
     */
    private final RestTemplate aiRestTemplate;

    /**
     * 调用文本模型对话接口
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI 返回的文本内容
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null);
    }

    /**
     * 调用文本模型对话接口（带凭据覆盖，BYOK 透传）。
     * <p>
     * {@code cred != null} 时优先使用其中的 key / model / url（override 模式）；
     * 仍可填 null 走 .env 兜底。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param cred         凭据（可为 null —— 走 .env 兜底）
     */
    public String chat(String systemPrompt, String userPrompt, ResolvedCredential cred) {
        return chatMessages(buildMessages(systemPrompt, userPrompt), cred);
    }

    /**
     * 调用文本模型对话接口（**多轮 messages**）。
     * <p>
     * 智能体（agent）需要把「上一轮的动作 JSON」和「工具执行结果」作为
     * assistant / user 消息回灌，让模型继续决策 —— 现有的
     * {@code chat(system, user)} 只能表达一问一答，撑不起多轮。
     * <p>
     * messages 元素约定：{@code {"role": "system"|"user"|"assistant", "content": "..."}}。
     * 角色顺序由调用方保证（system 必须在最前）。
     *
     * @param messages 消息列表（不可为空）
     * @param cred     凭据（可为 null —— 走 .env 兜底）
     * @return AI 返回的文本内容
     */
    public String chatMessages(List<Map<String, String>> messages, ResolvedCredential cred) {
        if (messages == null || messages.isEmpty()) {
            throw new RuntimeException("AI 调用失败：消息列表为空");
        }
        try {
            RestTemplate restTemplate = aiRestTemplate;

            // 构建请求体（OpenAI 兼容格式）。
            // thinking.type=disabled：官方推荐做法，禁用思考以减少 token 消耗与流式首字延迟；
            // 视觉客户端 AiVisionClient 同样处理，保持一致。
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", cred != null && cred.apiModel() != null ? cred.apiModel() : aiConfig.getModel());
            requestBody.put("stream", false);
            requestBody.put("thinking", Map.of("type", "disabled"));
            requestBody.put("messages", messages);

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = cred != null && cred.keyResolverKey() != null ? cred.keyResolverKey() : aiConfig.getApiKey();
            headers.setBearerAuth(apiKey);
            // OpenCode Go 通道要求：客户端标识 + 会话 id（缺失会被 400 MissingSessionID 拒绝）
            headers.set("User-Agent", com.example.aitools.common.Constants.AI_CLIENT_USER_AGENT);
            headers.set("x-opencode-session", java.util.UUID.randomUUID().toString());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String url = cred != null && cred.apiUrl() != null ? cred.apiUrl() : aiConfig.getApiUrl();
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

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
     * 拼 system + user 两条消息（{@link #chat(String, String, ResolvedCredential)} 的原有行为）。
     */
    private List<Map<String, String>> buildMessages(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt == null ? "" : userPrompt);
        messages.add(userMsg);
        return messages;
    }

    /**
     * 流式调用文本模型（SSE），每收到一个内容块回调 onChunk
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param onChunk 收到内容块时的回调
     */
    public void chatStream(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        chatStream(systemPrompt, userPrompt, onChunk, null);
    }

    /**
     * 流式调用文本模型（SSE），带凭据覆盖。
     *
     * @param cred 凭据（可为 null —— 走 .env 兜底）
     */
    public void chatStream(String systemPrompt, String userPrompt, Consumer<String> onChunk, ResolvedCredential cred) {
        try {
            RestTemplate restTemplate = aiRestTemplate;
            // 统计实际推送到前端的 chunk 数（lambda 内需可写，故用数组）
            final int[] chunkCount = {0};

            // 构建请求体（OpenAI 兼容格式，stream=true）。
            // thinking.type=disabled：官方推荐做法，禁用思考以减少 token 消耗与流式首字延迟。
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", cred != null && cred.apiModel() != null ? cred.apiModel() : aiConfig.getModel());
            requestBody.put("stream", true);
            requestBody.put("thinking", Map.of("type", "disabled"));

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
            String apiKey = cred != null && cred.keyResolverKey() != null ? cred.keyResolverKey() : aiConfig.getApiKey();
            headers.setBearerAuth(apiKey);
            // OpenCode Go 通道要求：客户端标识 + 会话 id
            headers.set("User-Agent", com.example.aitools.common.Constants.AI_CLIENT_USER_AGENT);
            headers.set("x-opencode-session", java.util.UUID.randomUUID().toString());

            String url = cred != null && cred.apiUrl() != null ? cred.apiUrl() : aiConfig.getApiUrl();

            // execute(url, method, requestCallback, responseExtractor)：
            // 在 RequestCallback 中把序列化后的请求体写入输出流（确保 body 真正发送），
            // 在 ResponseExtractor 中逐行读取 SSE 流并回调 onChunk
            restTemplate.execute(url, HttpMethod.POST,
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
                                    // 结束标记：跳出循环（原实现是空 if 体，会继续往下解析 "[DONE]" 并打 warn）
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
                                                        chunkCount[0]++;
                                                        onChunk.accept(content);
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        // 传异常对象以保留堆栈；正常模型偶发非 JSON 行属预期，故 warn 级
                                        log.warn("SSE 解析跳过异常行: {}", e.getMessage(), e);
                                    }
                                }
                            }
                        }
                        return null;
                    });

            // 上游可能返回 HTTP 200 但 body 非 SSE（网关/模型返回 {"error":...}、空 body 等），
            // 此时循环不产生任何 chunk 却被当作成功——会在调用方存成"成功但内容为空"的历史记录。
            if (chunkCount[0] == 0) {
                log.error("AI 流式调用未返回任何内容（可能上游返回了非 SSE 响应）");
                throw new RuntimeException("AI 服务未返回内容，请稍后重试");
            }
            log.info("AI stream completed, chunks={}", chunkCount[0]);
        } catch (Exception e) {
            log.error("AI 流式调用失败", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }
}
