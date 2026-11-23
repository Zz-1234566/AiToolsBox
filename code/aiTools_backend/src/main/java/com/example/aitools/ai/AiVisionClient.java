package com.example.aitools.ai;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.AiVisionConfig;
import com.example.aitools.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.example.aitools.common.MimeTypes;

/**
 * AI 视觉客户端：多模态（图片/视频）与纯文本走 OpenAI 兼容 chat/completions。
 * <p>
 * 端点与凭据来自 {@code ai.vision.*}（默认指向 OpenCode Go）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiVisionClient {

    private final AiVisionConfig visionConfig;
    private final ObjectMapper objectMapper;

    /**
     * 单张图片解读
     * @param userPrompt 用户提示词
     * @param imageBytes 图片字节数组
     * @return AI 解读文本
     */
    public String chatImage(String userPrompt, byte[] imageBytes) {
        return chatImage(userPrompt, imageBytes, null);
    }

    public String chatImage(String userPrompt, byte[] imageBytes, ResolvedCredential cred) {
        return chatImages(userPrompt, List.of(imageBytes), cred);
    }

    /**
     * 多图解读（PDF 转图片后逐页发）
     * @param userPrompt 用户提示词
     * @param imageBytesList 图片字节数组列表（每元素 = 一张图片）
     * @return AI 解读文本
     */
    public String chatImages(String userPrompt, List<byte[]> imageBytesList) {
        return chatImages(userPrompt, imageBytesList, null);
    }

    public String chatImages(String userPrompt, List<byte[]> imageBytesList, ResolvedCredential cred) {
        try {
            List<Map<String, Object>> contentBlocks = new ArrayList<>();

            // text block
            Map<String, Object> textBlock = new HashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", userPrompt);
            contentBlocks.add(textBlock);

            // image blocks
            for (byte[] imgBytes : imageBytesList) {
                String base64 = Base64.getEncoder().encodeToString(imgBytes);
                String mimeType = guessMimeType(imgBytes);
                Map<String, Object> imgBlock = new HashMap<>();
                imgBlock.put("type", "image_url");
                Map<String, Object> imgUrl = new HashMap<>();
                imgUrl.put("url", "data:" + mimeType + ";base64," + base64);
                imgUrl.put("detail", "default");
                imgBlock.put("image_url", imgUrl);
                contentBlocks.add(imgBlock);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", cred != null && cred.apiModel() != null ? cred.apiModel() : visionConfig.getModel());
            requestBody.put("stream", false);
            requestBody.put("thinking", Map.of("type", "disabled"));

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", contentBlocks);
            messages.add(userMsg);
            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);

            String apiKey = cred != null && cred.keyResolverKey() != null ? cred.keyResolverKey() : visionConfig.getApiKey();
            String url = cred != null && cred.apiUrl() != null ? cred.apiUrl() : visionConfig.getApiUrl();

            // 调用
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            // OpenCode Go 通道要求：客户端标识 + 会话 id
            conn.setRequestProperty("User-Agent", com.example.aitools.common.Constants.AI_CLIENT_USER_AGENT);
            conn.setRequestProperty("x-opencode-session", java.util.UUID.randomUUID().toString());
            conn.setDoOutput(true);
            applyTimeout(conn);
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            String resp;
            try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                resp = s.useDelimiter("\\A").next();
            }

            if (code != 200) {
                log.error("AI 模型 调用失败 status={} body={}", code, resp);
                throw new RuntimeException("AI 模型 调用失败：" + code);
            }

            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI 模型 返回为空");
            }
            JsonNode message = choices.get(0).get("message");
            String content = message.get("content").asText();
            log.debug("AI 模型 响应 length={}", content == null ? 0 : content.length());
            return content;

        } catch (IOException e) {
            log.error("AI 模型 调用异常", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 给 chat/completions 连接设置连接 / 读取超时。
     * <p>
     * 关键：{@code HttpURLConnection} 默认超时为 0（无限）。AI 端半开连接
     * （TCP 未断开但不再发数据）时会永久阻塞，逐个占满 batchExecutor 线程，
     * 最终 AI 功能整体不可用，且不抛异常、不打日志。
     * <p>
     * 超时值取自 {@link AiVisionConfig}（{@code ai.vision.*-timeout-seconds}），配置驱动，不硬编码。
     */
    private void applyTimeout(java.net.HttpURLConnection conn) {
        conn.setConnectTimeout(Math.max(visionConfig.getConnectTimeoutSeconds(), 1) * 1000);
        conn.setReadTimeout(Math.max(visionConfig.getReadTimeoutSeconds(), 1) * 1000);
    }

    /**
     * 纯文本对话（复用同一模型）
     */
    public String chatText(String systemPrompt, String userPrompt) {
        return chatText(systemPrompt, userPrompt, null);
    }

    public String chatText(String systemPrompt, String userPrompt, ResolvedCredential cred) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", cred != null && cred.apiModel() != null ? cred.apiModel() : visionConfig.getModel());
            requestBody.put("stream", false);
            requestBody.put("thinking", Map.of("type", "disabled"));

            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                Map<String, Object> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.add(sysMsg);
            }
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);
            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);

            String apiKey = cred != null && cred.keyResolverKey() != null ? cred.keyResolverKey() : visionConfig.getApiKey();
            String url = cred != null && cred.apiUrl() != null ? cred.apiUrl() : visionConfig.getApiUrl();

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            // OpenCode Go 通道要求：客户端标识 + 会话 id
            conn.setRequestProperty("User-Agent", com.example.aitools.common.Constants.AI_CLIENT_USER_AGENT);
            conn.setRequestProperty("x-opencode-session", java.util.UUID.randomUUID().toString());
            conn.setDoOutput(true);
            applyTimeout(conn);
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            String resp;
            try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                resp = s.useDelimiter("\\A").next();
            }

            if (code != 200) {
                log.error("AI 模型 文本调用失败 status={} body={}", code, resp);
                throw new RuntimeException("AI 模型 调用失败：" + code);
            }

            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI 模型 返回为空");
            }
            JsonNode message = choices.get(0).get("message");
            return message.get("content").asText();

        } catch (java.net.SocketTimeoutException e) {
            // 超时单独分支：明确告知是超时而非通用失败，便于区分「AI 慢」和「AI 挂了」。
            // 仍包成 RuntimeException，与既有失败路径保持一致，由调用方按文件粒度兜底。
            log.error("AI 模型 调用超时（connect={}s, read={}s）",
                    visionConfig.getConnectTimeoutSeconds(), visionConfig.getReadTimeoutSeconds(), e);
            throw new RuntimeException("AI 服务响应超时，请稍后重试", e);
        } catch (IOException e) {
            log.error("AI 模型 调用异常", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 根据文件头字节猜测 MIME 类型
     */
    private String guessMimeType(byte[] bytes) {
        if (bytes.length < 4) return MimeTypes.OCTET_STREAM;
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        // PNG
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return MimeTypes.PNG;
        // JPEG
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return MimeTypes.JPEG;
        // GIF
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) return MimeTypes.GIF;
        // WEBP
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) {
            // RIFF...WEBP
            if (bytes.length >= 12 && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
                return MimeTypes.WEBP;
            }
        }
        return MimeTypes.JPEG; // 默认按 JPEG 处理
    }
}
