package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 录音转文本服务配置（MiniMax 语音识别 + ffmpeg 转码）。
 * <p>
 * 使用 MiniMax 官方语音识别接口 {@code POST /v1/speech_to_text}（multipart/form-data，
 * 参数 model=asr-1.0 + file）。注意：{@code /v1/chat/completions} **不支持**音频输入，
 * 传入 input_audio 会被静默忽略，表现为"模型说没收到录音文件"。
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr")
public class AsrConfig {

    /** ffmpeg 可执行文件路径。空 = 走 PATH 自动查找 */
    private String ffmpegBinaryPath = "";

    /** 转码采样率（Hz） */
    private Integer sampleRate = 16000;

    /** 转码声道数（1=单声道，2=立体声） */
    private Integer channels = 1;

    /** 语音识别接口地址（MiniMax 官方 ASR） */
    private String apiUrl = "https://api.minimaxi.com/v1/speech_to_text";

    /** API Key，默认复用 ai.minimax-text.api-key */
    private String apiKey = "";

    /** 识别模型（官方固定 asr-1.0） */
    private String model = "asr-1.0";

    /** 语言提示（BCP-47，如 zh/en）；空 = 混合语言识别 */
    private String language = "zh";

    /** 音频时长上限（秒），官方约束 500s，超出返回 400 */
    private int maxDurationSeconds = 500;
}
