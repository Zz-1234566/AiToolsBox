package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 录音转文本服务配置（MiniMax + ffmpeg 转码）
 * <p>
 * - ffmpegBinaryPath: ffmpeg 可执行文件路径。空时默认从 PATH 找（"ffmpeg"）
 * - 转码统一输出 16kHz 单声道 mp3（MiniMax chatAudio 推荐）
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
}
