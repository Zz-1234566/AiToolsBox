package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 录音转文本服务配置（ffmpeg 转码 + 腾讯云语音识别）。
 * <p>
 * 转写通道只有腾讯云一条（录音文件识别 CreateRecTask），
 * 具体参数见 {@link TencentAsrConfig}；本类只管转码参数。
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

    /** 音频时长上限（秒），超出会被上游拒绝 */
    private int maxDurationSeconds = 500;
}
