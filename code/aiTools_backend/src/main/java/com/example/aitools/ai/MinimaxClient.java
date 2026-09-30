package com.example.aitools.ai;

import com.example.aitools.config.AiVisionConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MiniMax M3 多模态客户端（OpenAI 兼容接口）
 * <p>支持：图片（base64）、多图、纯文本对话
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinimaxClient {

    private final AiVisionConfig visionConfig;
    private final ObjectMapper objectMapper;

    /**
     * 单张图片解读
     * @param userPrompt 用户提示词
     * @param imageBytes 图片字节数组
     * @return AI 解读文本
     */
    public String chatImage(String userPrompt, byte[] imageBytes) {
        return chatImages(userPrompt, List.of(imageBytes));
    }

    /**
     * 多图解读（PDF 转图片后逐页发）
     * @param userPrompt 用户提示词
     * @param imageBytesList 图片字节数组列表（每元素 = 一张图片）
     * @return AI 解读文本
     */
    public String chatImages(String userPrompt, List<byte[]> imageBytesList) {
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
            requestBody.put("model", visionConfig.getModel());
            requestBody.put("stream", false);
            requestBody.put("thinking", Map.of("type", "disabled"));

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", contentBlocks);
            messages.add(userMsg);
            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);

            // 调用
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(visionConfig.getApiUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + visionConfig.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            String resp;
            try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                resp = s.useDelimiter("\\A").next();
            }

            if (code != 200) {
                log.error("MiniMax M3 调用失败 status={} body={}", code, resp);
                throw new RuntimeException("MiniMax M3 调用失败：" + code);
            }

            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("MiniMax M3 返回为空");
            }
            JsonNode message = choices.get(0).get("message");
            String content = message.get("content").asText();
            log.debug("MiniMax M3 响应 length={}", content == null ? 0 : content.length());
            return content;

        } catch (IOException e) {
            log.error("MiniMax M3 调用异常", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 音频转文本（MiniMax M3 多模态 audio input）
     *
     * @param systemPrompt   系统提示词（如：请将这段录音准确转写为中文文字，不要修改用词和标点）
     * @param audioBytes     音频字节（mp3 / wav / m4a）
     * @param mimeType       音频 MIME，如 audio/mpeg
     * @return 模型返回的识别文本
     */
    public String chatAudio(String systemPrompt, byte[] audioBytes, String mimeType) {
        try {
            // 1. content blocks: text + input_audio
            List<Map<String, Object>> contentBlocks = new ArrayList<>();

            Map<String, Object> textBlock = new HashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", systemPrompt);
            contentBlocks.add(textBlock);

            String base64 = Base64.getEncoder().encodeToString(audioBytes);
            Map<String, Object> audioBlock = new HashMap<>();
            audioBlock.put("type", "input_audio");
            Map<String, Object> inputAudio = new HashMap<>();
            inputAudio.put("data", base64);
            inputAudio.put("format", normalizeAudioFormat(mimeType, audioBytes));
            audioBlock.put("input_audio", inputAudio);
            contentBlocks.add(audioBlock);

            // 2. request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", visionConfig.getModel());
            requestBody.put("stream", false);
            requestBody.put("thinking", Map.of("type", "disabled"));

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", contentBlocks);
            messages.add(userMsg);
            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);

            // 3. HTTP call
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(visionConfig.getApiUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + visionConfig.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int code = -1;
            String resp = null;
            int maxRetries = 3;
            long backoffMs = 2000;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    code = conn.getResponseCode();
                    // 遇 5xx / 429 过载：重试
                    if ((code >= 500 || code == 429) && attempt < maxRetries) {
                        log.warn("MiniMax audio 调用遇 status={} attempt={}/{} -> retry after {}ms", code, attempt, maxRetries, backoffMs);
                        try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        backoffMs *= 2;
                        continue;
                    }
                    try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        resp = s.useDelimiter("\\A").next();
                    }
                    break;
                } catch (java.io.IOException ioe) {
                    // 网络异常（SSL / EOF / 超时）：重试
                    log.warn("MiniMax audio 调用 IOException attempt={}/{} -> retry: {}", attempt, maxRetries, ioe.getMessage());
                    if (attempt < maxRetries) {
                        try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        backoffMs *= 2;
                    } else {
                        throw ioe;
                    }
                }
            }

            if (code != 200) {
                log.error("MiniMax M3 audio 调用失败 status={} body={}", code, resp);
                throw new RuntimeException("MiniMax M3 audio 调用失败：" + code);
            }

            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("MiniMax M3 audio 返回为空");
            }
            JsonNode message = choices.get(0).get("message");
            String content = message.get("content").asText();
            log.debug("MiniMax M3 audio 响应 length={}", content == null ? 0 : content.length());
            return content;

        } catch (IOException e) {
            log.error("MiniMax M3 audio 调用异常", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 归一化为 MiniMax audio format（仅 mp3 / wav 两值）
     */
    private String normalizeAudioFormat(String mimeType, byte[] bytes) {
        if (mimeType != null) {
            String lower = mimeType.toLowerCase();
            if (lower.contains("wav")) return "wav";
            if (lower.contains("mp3") || lower.contains("mpeg")) return "mp3";
        }
        // 备选：按文件头检测
        if (bytes.length >= 4) {
            // RIFF...WAV
            if (bytes[0] == (byte) 0x52 && bytes[1] == (byte) 0x49 && bytes[2] == (byte) 0x46 && bytes[3] == (byte) 0x46) {
                return "wav";
            }
            // ID3 或 MP3 sync 0xFFE
            if (bytes[0] == (byte) 0x49 && bytes[1] == (byte) 0x44 && bytes[2] == (byte) 0x33) return "mp3";
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0) return "mp3";
        }
        return "mp3";
    }

    /**
     * 纯文本对话（复用同一模型）
     */
    public String chatText(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", visionConfig.getModel());
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

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(visionConfig.getApiUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + visionConfig.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            String resp;
            try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
                resp = s.useDelimiter("\\A").next();
            }

            if (code != 200) {
                log.error("MiniMax M3 文本调用失败 status={} body={}", code, resp);
                throw new RuntimeException("MiniMax M3 调用失败：" + code);
            }

            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("MiniMax M3 返回为空");
            }
            JsonNode message = choices.get(0).get("message");
            return message.get("content").asText();

        } catch (IOException e) {
            log.error("MiniMax M3 调用异常", e);
            throw new RuntimeException("AI 服务调用失败，请稍后重试", e);
        }
    }

    /**
     * 根据文件头字节猜测 MIME 类型
     */
    private String guessMimeType(byte[] bytes) {
        if (bytes.length < 4) return "application/octet-stream";
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;
        // PNG
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "image/png";
        // JPEG
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "image/jpeg";
        // GIF
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) return "image/gif";
        // WEBP
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) {
            // RIFF...WEBP
            if (bytes.length >= 12 && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
                return "image/webp";
            }
        }
        return "image/jpeg"; // 默认按 JPEG 处理
    }
}
