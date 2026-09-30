package com.example.aitools.service;

import com.example.aitools.ai.MinimaxClient;
import com.example.aitools.config.AsrConfig;
import com.example.aitools.dto.TranscribeResponse;
import com.example.aitools.common.ResultCode;
import com.example.aitools.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 录音转文本服务（MiniMax M3 多模态 audio input）
 * <p>
 * 流程：保存上传的音频到临时文件 -> ffmpeg 转码为 16k mono mp3 -> 调 MinimaxClient.chatAudio -> 删除临时文件
 * <p>
 * 任意格式音频（mp3/wav/m4a/flac/aac/ogg/opus 等）都会先转码，
 * 统一为 MiniMax chatAudio 支持的 wav/mp3 格式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeService {

    private static final String TRANSCRIBE_PROMPT =
            "请将这段录音准确转写为中文文字输出。不要修改用词、句序或标点；遇到说话人切换可用换行分隔。";

    private final MinimaxClient minimaxClient;
    private final AsrConfig asrConfig;

    public TranscribeResponse transcribe(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "音频文件为空");
        }

        Path inputTemp = null;
        Path outputTemp = null;
        try {
            String safeName = file.getOriginalFilename() == null ? "audio.bin" : file.getOriginalFilename();
            inputTemp = Files.createTempFile("asr_in_", "_" + sanitizeName(safeName));
            // 用 getInputStream 落盘，而非 file.transferTo：
            // 工作流场景传入的是 BatchFilePayload 的内存版 MultipartFile，
            // 其 transferTo 抛 UnsupportedOperationException（仅真实 multipart 支持），
            // 会导致工作流中「录音转写」节点必然失败。
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, inputTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 1. ffmpeg 转码为 16k mono mp3（MiniMax chatAudio 支持）
            outputTemp = Files.createTempFile("asr_out_", ".mp3");
            boolean transcodeOk = transcodeToMp3(inputTemp.toFile(), outputTemp.toFile());
            Path audioPath = transcodeOk ? outputTemp : inputTemp;
            String mime = transcodeOk ? "audio/mpeg" : detectMime(safeName);

            // 2. 读字节 + 调 MiniMax M3
            byte[] audioBytes = Files.readAllBytes(audioPath);
            String text = minimaxClient.chatAudio(TRANSCRIBE_PROMPT, audioBytes, mime);
            if (text == null || text.isBlank()) {
                throw new BusinessException("MiniMax 返回为空");
            }
            String formatted = text.trim();
            log.info("[ASR-minimax] 转写成功 text-len={} transcode={}", formatted.length(), transcodeOk);
            return new TranscribeResponse(formatted, null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ASR-minimax] 转写失败", e);
            // 脱敏：ffmpeg 输出 / MiniMax HTTP 错误 / IO 路径只进日志
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
        } finally {
            if (inputTemp != null) try { Files.deleteIfExists(inputTemp); } catch (IOException ignored) {}
            if (outputTemp != null) try { Files.deleteIfExists(outputTemp); } catch (IOException ignored) {}
        }
    }

    /**
     * 调用 ffmpeg 把任意音频转码为 16kHz 单声道 mp3
     * @return true 转码成功；false ffmpeg 不可用或失败（回退原始文件）
     */
    private boolean transcodeToMp3(File input, File output) {
        String ffmpeg = (asrConfig.getFfmpegBinaryPath() == null || asrConfig.getFfmpegBinaryPath().isBlank())
                ? "ffmpeg"
                : asrConfig.getFfmpegBinaryPath();
        try {
            // ffmpeg -y -i input -ar 16000 -ac 1 -b:a 64k output.mp3
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg,
                    "-y",                       // 覆盖输出
                    "-i", input.getAbsolutePath(),
                    "-ar", String.valueOf(asrConfig.getSampleRate()),
                    "-ac", String.valueOf(asrConfig.getChannels()),
                    "-b:a", "64k",              // 64 kbps 对语音足够
                    "-f", "mp3",
                    output.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // 读 stderr/stdout（避免 ffmpeg 输出阻塞）
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                int code = p.waitFor();
                if (code != 0) {
                    log.warn("[ASR-ffmpeg] 转码失败 code={} tail={}", code, sb.toString().substring(Math.max(0, sb.length() - 400)));
                    return false;
                }
                log.debug("[ASR-ffmpeg] 转码成功 output={} bytes", output.length());
                return true;
            }
        } catch (IOException e) {
            log.warn("[ASR-ffmpeg] ffmpeg 不可用: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ASR-ffmpeg] 转码被中断");
            return false;
        }
    }

    private String detectMime(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".amr")) return "audio/amr";
        return "audio/mpeg";
    }

    private String sanitizeName(String name) {
        // 移除特殊字符，避免临时文件路径问题
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
