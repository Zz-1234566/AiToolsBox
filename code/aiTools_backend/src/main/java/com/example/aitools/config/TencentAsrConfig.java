package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云语音识别（ASR）配置。
 * <p>
 * 用于「录音转写」工具的腾讯云通道：{@code CreateRecTask}（录音文件识别）
 * + {@code DescribeTaskStatus}（轮询结果）。
 * <p>
 * 密钥默认复用 COS 同一套（与 {@link com.example.aitools.config.OcrConfig} 同样的复用策略），
 * 仅 {@code appId} 为 ASR 专用，需从腾讯云语音识别控制台「账户信息」获取。
 * <p>
 * 注意：{@code enabled=false} 时不参与引擎选择，录音转写仍走 MiniMax 通道。
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr.tencent")
public class TencentAsrConfig {

    /** 是否启用腾讯云 ASR 通道（false 时前端不展示腾讯云选项） */
    private Boolean enabled = false;

    /** 腾讯云 SecretId（留空 = 复用 cos.secret-id） */
    private String secretId;

    /** 腾讯云 SecretKey（留空 = 复用 cos.secret-key） */
    private String secretKey;

    /** 地域，如 ap-guangzhou（留空 = 复用 cos.region；云 API 实际走就近接入） */
    private String region;

    /** 语音识别 AppId（ASR 专用，控制台「账户信息」，形如 100047898545） */
    private String appId;

    /** 引擎模型类型：16k_zh = 中文普通话 16kHz */
    private String engineModelType = "16k_zh";

    /** 音频声道数（1=单声道） */
    private Integer channelNum = 1;

    /**
     * 单个音频文件大小上限（字节，原始文件口径）。
     * <p>
     * 腾讯云 {@code CreateRecTask} 走 {@code SourceType=1} 内联 base64 时，
     * {@code Data} 字段上限 5242880 字节（5MB）；base64 膨胀约 4/3，
     * 故原始文件上限取 3900000 字节（约 3.7MB），留出安全余量。
     * <p>
     * 超限文件由 FileCompressService 压缩后再提交（后续阶段实现）；
     * 本阶段先显式拦截并报错，避免把腾讯云的英文错误码透给用户。
     */
    private Long maxAudioBytes = 3900000L;

    /** 轮询间隔（毫秒） */
    private Long pollIntervalMs = 3000L;

    /** 单次识别最大轮询次数（默认 100 × 3s ≈ 5 分钟） */
    private Integer maxPollTimes = 100;
}
