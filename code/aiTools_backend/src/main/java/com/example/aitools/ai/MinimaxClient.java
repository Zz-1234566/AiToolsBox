package com.example.aitools.ai;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.AiVisionConfig;
import com.example.aitools.config.AsrConfig;
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

/**
 * MiniMax 客户端：多模态（图片/视频/文本）走 OpenAI 兼容 chat/completions；
 * 音频转写走官方语音识别接口 /v1/speech_to_text。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinimaxClient {

    private final AiVisionConfig visionConfig;
    private final AsrConfig asrConfig;
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
            applyTimeout(conn);
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
     * 音频转文本：调用 MiniMax 官方语音识别接口 {@code POST /v1/speech_to_text}。
     * <p>
     * <b>重要</b>：{@code /v1/chat/completions}（chatAudio 旧实现走的端点）**不支持音频输入**，
     * 传入 {@code input_audio} 会被服务端静默忽略，表现为模型回复"没有收到录音文件"。
     * 官方 ASR 接口要求 multipart/form-data，参数 {@code model=asr-1.0} + {@code file=音频文件}。
     *
     * @param systemPrompt 忽略（官方 ASR 不接受自定义提示词，仅做识别）
     * @param audioBytes   音频字节
     * @param mimeType     音频 MIME，如 audio/mpeg（用于推断扩展名）
     * @return 识别出的文本
     */
    public String chatAudio(String systemPrompt, byte[] audioBytes, String mimeType) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "音频内容为空");
        }
        try {
            String boundary = "----AiToolsBox" + UUID.randomUUID().toString().replace("-", "");
            String fileName = "audio" + extensionOf(mimeType, audioBytes);

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            writeFormField(buf, boundary, "model", asrConfig.getModel());
            writeFormField(buf, boundary, "response_format", "json");
            // 文件字段
            buf.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: " + (mimeType == null ? "application/octet-stream" : mimeType) + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            buf.write(audioBytes);
            buf.write("\r\n".getBytes(StandardCharsets.UTF_8));
            buf.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] body = buf.toByteArray();

            String apiKey = (asrConfig.getApiKey() == null || asrConfig.getApiKey().isBlank())
                    ? visionConfig.getApiKey() : asrConfig.getApiKey();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(asrConfig.getApiUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (asrConfig.getLanguage() != null && !asrConfig.getLanguage().isBlank()) {
                conn.setRequestProperty("language", asrConfig.getLanguage());
            }
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(300_000);
            conn.setDoOutput(true);
            conn.getOutputStream().write(body);

            int code = conn.getResponseCode();
            String resp = readAll(code == 200 ? conn.getInputStream() : conn.getErrorStream());
            if (code != 200) {
                // 只进日志：响应体可能含 trace_id 等内部信息
                log.error("MiniMax ASR 调用失败 status={} body={}", code, resp);
                throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
            }
            JsonNode root = objectMapper.readTree(resp);
            JsonNode textNode = root.get("text");
            if (textNode == null || textNode.isNull()) {
                log.error("MiniMax ASR 返回缺少 text 字段: {}", resp);
                throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
            }
            String text = textNode.asText();
            log.info("MiniMax ASR 成功 text-len={} duration={}", text.length(),
                    root.has("duration") ? root.get("duration").asText() : "-");
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MiniMax ASR 调用异常", e);
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
        }
    }

    /**
     * 给 chat/completions 连接设置连接 / 读取超时。
     * <p>
     * 关键：{@code HttpURLConnection} 默认超时为 0（无限）。AI 端半开连接
     * （TCP 未断开但不再发数据）时会永久阻塞，逐个占满 batchExecutor 线程，
     * 最终 AI 功能整体不可用，且不抛异常、不打日志。
     * <p>
     * 超时值取自 {@link AiVisionConfig}（{@code ai.minimax.*-timeout-seconds}），
     * 与 {@code chatAudio} 同为配置驱动，不硬编码。
     */
    private void applyTimeout(java.net.HttpURLConnection conn) {
        conn.setConnectTimeout(Math.max(visionConfig.getConnectTimeoutSeconds(), 1) * 1000);
        conn.setReadTimeout(Math.max(visionConfig.getReadTimeoutSeconds(), 1) * 1000);
    }

    /** 写 multipart 文本字段 */
    private void writeFormField(ByteArrayOutputStream buf, String boundary, String name, String value) throws IOException {
        buf.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    /** 读取输入流全部内容 */
    private String readAll(java.io.InputStream in) throws IOException {
        if (in == null) return "";
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** 按 MIME/文件头推断音频扩展名 */
    private String extensionOf(String mimeType, byte[] bytes) {
        String fmt = normalizeAudioFormat(mimeType, bytes);
        return "." + fmt;
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
            applyTimeout(conn);
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

        } catch (java.net.SocketTimeoutException e) {
            // 超时单独分支：明确告知是超时而非通用失败，便于区分「AI 慢」和「AI 挂了」。
            // 仍包成 RuntimeException，与既有失败路径保持一致，由调用方按文件粒度兜底。
            log.error("MiniMax M3 调用超时（connect={}s, read={}s）",
                    visionConfig.getConnectTimeoutSeconds(), visionConfig.getReadTimeoutSeconds(), e);
            throw new RuntimeException("AI 服务响应超时，请稍后重试", e);
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
