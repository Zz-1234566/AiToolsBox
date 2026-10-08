package com.example.aitools.service;

import com.example.aitools.ai.MinimaxClient;
import com.example.aitools.ai.TencentAsrClient;
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
 * 录音转文本服务（双引擎）
 * <p>
 * 支持两个转写通道，由 {@link #transcribe(MultipartFile, String)} 的 engine 参数选择：
 * <ul>
 *   <li>{@link #ENGINE_MINIMAX}（默认）：ffmpeg 转码 16k mono mp3 -> {@code MinimaxClient.chatAudio}</li>
 *   <li>{@link #ENGINE_TENCENT}：ffmpeg 转码 16k mono mp3 -> 腾讯云 {@code CreateRecTask} 录音文件识别</li>
 * </ul>
 * 两个通道共用同一套 ffmpeg 转码；任意格式音频（mp3/wav/m4a/flac/aac/ogg/opus 等）都会先转码，
 * 统一为 16kHz 单声道 mp3。
 * <p>
 * <b>兼容性</b>：{@code transcribe(MultipartFile)} 单参重载保留，engine 为空时回落到
 * {@code asr.engine} 配置值（默认 minimax），旧调用方（工作流 NodeExecutor）行为不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeService {

    private static final String TRANSCRIBE_PROMPT =
            "请将这段录音准确转写为中文文字输出。不要修改用词、句序或标点；遇到说话人切换可用换行分隔。";

    private final MinimaxClient minimaxClient;
    private final TencentAsrClient tencentAsrClient;
    private final AsrConfig asrConfig;

    /** 引擎标识：MiniMax 多模态音频通道（默认，向后兼容） */
    public static final String ENGINE_MINIMAX = "minimax";

    /** 引擎标识：腾讯云录音文件识别通道 */
    public static final String ENGINE_TENCENT = "tencent";

    /**
     * 录音转写（默认引擎）。
     * <p>
     * 保留此重载供工作流引擎 {@code NodeExecutor} 调用 —— 工作流节点暂不支持指定引擎，
     * 统一走配置里的默认引擎，行为与接入腾讯云前完全一致。
     */
    public TranscribeResponse transcribe(MultipartFile file) {
        return transcribe(file, null);
    }

    /**
     * 录音转写（可指定引擎）。
     *
     * @param file   音频文件
     * @param engine 引擎标识（minimax / tencent）；为 null、空或无法识别时回落到默认引擎
     */
    public TranscribeResponse transcribe(MultipartFile file, String engine) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "音频文件为空");
        }
        String engineKey = resolveEngine(engine);
        return ENGINE_TENCENT.equals(engineKey)
                ? transcribeByTencent(file)
                : transcribeByMinimax(file);
    }

    /**
     * 解析引擎标识：入参优先，其次配置默认值，最后兜底 minimax。
     * <p>
     * 腾讯云通道未启用（enabled=false 或 appId 缺失）时也回落到 minimax，
     * 避免前端传了 engine=tencent 但服务端未配置导致 500。
     */
    private String resolveEngine(String engine) {
        String candidate = (engine == null || engine.isBlank())
                ? asrConfig.getEngine() : engine.trim();
        if (ENGINE_TENCENT.equalsIgnoreCase(candidate)) {
            return tencentAsrClient.isEnabled() ? ENGINE_TENCENT : ENGINE_MINIMAX;
        }
        return ENGINE_MINIMAX;
    }

    /**
     * 腾讯云通道：ffmpeg 转码为 16k mono mp3 -> CreateRecTask -> 轮询结果。
     * <p>
     * 与 MiniMax 通道共用同一套 ffmpeg 转码（16kHz 单声道 mp3），
     * 腾讯云对该格式识别稳定，且体积远小于 wav（wav 更容易撞 5MB base64 上限）。
     */
    private TranscribeResponse transcribeByTencent(MultipartFile file) {
        Path inputTemp = null;
        Path outputTemp = null;
        try {
            String safeName = file.getOriginalFilename() == null ? "audio.bin" : file.getOriginalFilename();
            inputTemp = Files.createTempFile("asr_in_", "_" + sanitizeName(safeName));
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, inputTemp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            outputTemp = Files.createTempFile("asr_out_", ".mp3");
            boolean transcodeOk = transcodeToMp3(inputTemp.toFile(), outputTemp.toFile());
            Path audioPath = transcodeOk ? outputTemp : inputTemp;

            byte[] audioBytes = Files.readAllBytes(audioPath);
            String text = tencentAsrClient.transcribe(audioBytes, safeName);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "未识别到语音内容，请检查录音后重试");
            }
            String formatted = text.trim();
            log.info("[ASR-tencent] 转写成功 text-len={} transcode={}", formatted.length(), transcodeOk);
            return new TranscribeResponse(formatted, null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ASR-tencent] 转写失败", e);
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), ResultCode.AUDIO_FAILED.getMessage());
        } finally {
            if (inputTemp != null) try { Files.deleteIfExists(inputTemp); } catch (IOException ignored) {}
            if (outputTemp != null) try { Files.deleteIfExists(outputTemp); } catch (IOException ignored) {}
        }
    }

    /**
     * MiniMax 通道（原有逻辑）：ffmpeg 转码为 16k mono mp3 -> MinimaxClient.chatAudio。
     */
    private TranscribeResponse transcribeByMinimax(MultipartFile file) {
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
                throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "未识别到语音内容，请检查录音后重试");
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
