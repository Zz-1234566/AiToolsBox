package com.example.aitools.service.impl;

import com.example.aitools.ai.TencentAsrClient;
import com.example.aitools.common.ResultCode;
import com.example.aitools.config.AsrConfig;
import com.example.aitools.dto.TranscribeResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.TranscribeService;
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
 * 录音转文本服务实现。
 * <p>
 * 实现见 {@link TranscribeService}：统一走腾讯云录音文件识别。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeServiceImpl implements TranscribeService {

    private final TencentAsrClient tencentAsrClient;
    private final AsrConfig asrConfig;

    @Override
    public TranscribeResponse transcribe(MultipartFile file) {
        return transcribe(file, null);
    }

    @Override
    public TranscribeResponse transcribe(MultipartFile file, String engine) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(), "音频文件为空");
        }
        // 当前只有腾讯云一条通道：engine 参数仅为接口兼容保留
        if (engine != null && !engine.isBlank() && !ENGINE_TENCENT.equalsIgnoreCase(engine.trim())) {
            log.warn("[ASR] 忽略未知引擎标识 engine={}（当前仅支持 tencent）", engine);
        }
        if (!tencentAsrClient.isEnabled()) {
            throw new BusinessException(ResultCode.AUDIO_FAILED.getCode(),
                    "语音识别服务未启用，请检查腾讯云 ASR 配置");
        }
        return transcribeByTencent(file);
    }

    /**
     * 腾讯云通道：ffmpeg 转码为 16k mono mp3 -> CreateRecTask -> 轮询结果。
     * <p>
     * 统一转成 16kHz 单声道 mp3：腾讯云对该格式识别稳定，
     * 且体积远小于 wav（wav 更容易撞 5MB base64 上限）。
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
            // 临时文件清理是 best-effort：删不掉（Windows 上文件被占用）不该影响转写结果。
            // 但不能静默 —— 至少要能在日志里看到（静默失败规范豁免：清理失败不影响主流程）
            if (inputTemp != null) try { Files.deleteIfExists(inputTemp); }
            catch (IOException ignored) { log.debug("[ASR] 临时输入文件删除失败 path={}", inputTemp, ignored); }
            if (outputTemp != null) try { Files.deleteIfExists(outputTemp); }
            catch (IOException ignored) { log.debug("[ASR] 临时输出文件删除失败 path={}", outputTemp, ignored); }
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
            log.warn("[ASR-ffmpeg] ffmpeg 不可用: {}", e.getMessage(), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ASR-ffmpeg] 转码被中断");
            return false;
        }
    }

    private String sanitizeName(String name) {
        // 移除特殊字符，避免临时文件路径问题
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
