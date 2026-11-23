package com.example.aitools.service;

import com.example.aitools.dto.TranscribeResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 录音转文本服务。
 * <p>
 * 唯一通道：ffmpeg 转码 16k mono mp3 -> 腾讯云 {@code CreateRecTask} 录音文件识别。
 * 任意格式音频（mp3/wav/m4a/flac/aac/ogg/opus 等）都会先转码，统一为 16kHz 单声道 mp3。
 */
public interface TranscribeService {

    /** 引擎标识：腾讯云录音文件识别通道（当前唯一通道） */
    String ENGINE_TENCENT = "tencent";

    /**
     * 录音转写（默认通道）。
     * <p>
     * 供工作流引擎 {@code NodeExecutor} 等不关心引擎的调用方使用。
     */
    TranscribeResponse transcribe(MultipartFile file);

    /**
     * 录音转写。
     *
     * @param file   音频文件
     * @param engine 引擎标识（仅为接口兼容保留；当前固定走腾讯云，传其他值会被忽略）
     */
    TranscribeResponse transcribe(MultipartFile file, String engine);
}
