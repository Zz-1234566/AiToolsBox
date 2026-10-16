package com.example.aitools.service;

import com.example.aitools.dto.TranscribeResponse;
import org.springframework.web.multipart.MultipartFile;

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
public interface TranscribeService {

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
    TranscribeResponse transcribe(MultipartFile file);

    /**
     * 录音转写（可指定引擎）。
     *
     * @param file   音频文件
     * @param engine 引擎标识（minimax / tencent）；为 null、空或无法识别时回落到默认引擎
     */
    TranscribeResponse transcribe(MultipartFile file, String engine);
}
