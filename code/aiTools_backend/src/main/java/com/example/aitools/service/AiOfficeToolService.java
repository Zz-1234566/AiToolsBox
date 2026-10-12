package com.example.aitools.service;

import com.example.aitools.dto.BatchFilePayload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Consumer;

public interface AiOfficeToolService {

    /**
     * 整理工作内容并记录历史：调用 AI 生成结构化总结，成功/失败均记录历史
     */
    String aiWorkSummary(Long userId, String content, String promptFormat, String promptGenerate, Long promptId);

    /**
     * BYOK 覆盖版工作总结。
     */
    String aiWorkSummary(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, String apiKey);

    /**
     * 流式工作总结（SSE），内部统一管理历史记录
     */
    String aiWorkSummaryStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /** BYOK 版流式工作总结 */
    String aiWorkSummaryStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, String apiKey, Consumer<String> onChunk);

    /**
     * 流式周报生成（SSE），内部统一管理历史记录
     */
    String aiWeeklyReportStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /** BYOK 版流式周报 */
    String aiWeeklyReportStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, String apiKey, Consumer<String> onChunk);

    /**
     * 流式会议纪要（SSE），内部统一管理历史记录
     */
    String aiMeetingMinutesStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /** BYOK 版流式会议纪要 */
    String aiMeetingMinutesStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, String apiKey, Consumer<String> onChunk);

    /**
     * 流式文档重点提取（SSE），上传文档解析后调 AI 提炼重点，成功/失败均记录历史
     */
    String aiDocumentSummaryStream(Long userId, MultipartFile file, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /**
     * 流式重点提取（SSE，纯文字输入），成功/失败均记录历史
     */
    String aiDocKeypointTextStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /** BYOK 版流式纯文字重点提取 */
    String aiDocKeypointTextStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, String apiKey, Consumer<String> onChunk);

    /**
     * 流式 OCR 智能识别（SSE）：上传图片 → 腾讯云 OCR 提取文字 → 调 AI 整理成结构化结果
     */
    String aiOcrStream(Long userId, MultipartFile file, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /** BYOK 版流式 OCR 识别 */
    String aiOcrStream(Long userId, MultipartFile file, String promptFormat, String promptGenerate, Long promptId, String apiKey, Consumer<String> onChunk);

    /**
     * 批量流式文档重点提取（B2）：逐文件解析 + AI 整理
     */
    com.example.aitools.dto.BatchProcessResult aiDocumentSummaryBatchStream(Long userId, List<BatchFilePayload> files, String promptFormat, String promptGenerate, Long promptId, String batchId);

    /**
     * 批量 OCR 智能识别（B2）：逐文件 OCR + AI 整理
     */
    com.example.aitools.dto.BatchProcessResult aiOcrBatchStream(Long userId, List<BatchFilePayload> files, String promptFormat, String promptGenerate, Long promptId, String batchId);

    /**
     * 批量 AI 文件解读（B2）：MiniMax M3 多模态
     */
    com.example.aitools.dto.BatchProcessResult aiFileReaderBatchStream(Long userId, List<BatchFilePayload> files, String prompt, String batchId);

    /**
     * 批量录音转写（B2）：逐文件 ffmpeg 转码 + ASR（默认引擎）。
     */
    com.example.aitools.dto.BatchProcessResult audioTranscribeBatchStream(Long userId, List<BatchFilePayload> files, String batchId);

    /**
     * 批量录音转写（B2）：可指定转写引擎。
     */
    com.example.aitools.dto.BatchProcessResult audioTranscribeBatchStream(Long userId, List<BatchFilePayload> files, String batchId, String engine);
}