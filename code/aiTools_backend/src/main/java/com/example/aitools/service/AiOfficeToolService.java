package com.example.aitools.service;

import com.example.aitools.dto.BatchFilePayload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Consumer;

public interface AiOfficeToolService {

    /**
     * 整理工作内容并记录历史：调用 AI 生成结构化总结，成功/失败均记录历史
     * @param userId 当前登录用户 ID
     * @param content 用户输入的工作内容
     * @param promptFormat 用户自定义格式提示词（可空，缺省取系统 format 默认）
     * @param promptGenerate 用户自定义生成内容提示词（可空，缺省取系统 generate 默认）
     * @param promptId 系统提示词ID（可空，选中后按其 prompt_use 补 format/generate 之一）
     * @return AI 生成的整理结果
     */
    String aiWorkSummary(Long userId, String content, String promptFormat, String promptGenerate, Long promptId);

    /**
     * 流式工作总结（SSE），内部统一管理历史记录
     * @param userId 用户ID
     * @param content 工作内容
     * @param promptFormat 用户自定义格式提示词（可空，缺省取系统 format 默认）
     * @param promptGenerate 用户自定义生成内容提示词（可空，缺省取系统 generate 默认）
     * @param promptId 系统提示词ID（可空）
     * @param onChunk 每收到一段内容回调
     * @return 完整结果（最后返回）
     */
    String aiWorkSummaryStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /**
     * 流式周报生成（SSE），内部统一管理历史记录
     * @param userId 用户ID
     * @param content 工作内容
     * @param promptFormat 用户自定义格式提示词（可空，缺省取系统 format 默认）
     * @param promptGenerate 用户自定义生成内容提示词（可空，缺省取系统 generate 默认）
     * @param promptId 系统提示词ID（可空）
     * @param onChunk 每收到一段内容回调
     * @return 完整结果（最后返回）
     */
    String aiWeeklyReportStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /**
     * 流式会议纪要（SSE），内部统一管理历史记录
     * @param userId 用户ID
     * @param content 会议内容
     * @param promptFormat 用户自定义格式提示词（可空，缺省取系统 format 默认）
     * @param promptGenerate 用户自定义生成内容提示词（可空，缺省取系统 generate 默认）
     * @param promptId 系统提示词ID（可空）
     * @param onChunk 每收到一段内容回调
     * @return 完整结果（最后返回）
     */
    String aiMeetingMinutesStream(Long userId, String content, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /**
     * 流式文档重点提取（SSE），上传文档解析后调 AI 提炼重点，成功/失败均记录历史
     * @param userId 用户ID
     * @param file 上传的文档（txt/pdf/docx）
     * @param promptFormat 用户自定义格式提示词（可空）
     * @param promptGenerate 用户自定义生成内容提示词（可空）
     * @param promptId 系统提示词ID（可空）
     * @param onChunk 每收到一段内容回调
     * @return 完整结果
     */
    String aiDocumentSummaryStream(Long userId, MultipartFile file, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /**
     * 流式 OCR 智能识别（SSE）：上传图片 → 腾讯云 OCR 提取文字 → 调 AI 整理成结构化结果
     * @param userId 用户ID
     * @param file 上传的图片（jpg/png/pdf）
     * @param promptFormat 用户自定义格式提示词（可空）
     * @param promptGenerate 用户自定义生成内容提示词（可空）
     * @param promptId 系统提示词ID（可空）
     * @param onChunk 每收到一段内容回调
     * @return 完整结果
     */
    String aiOcrStream(Long userId, MultipartFile file, String promptFormat, String promptGenerate, Long promptId, Consumer<String> onChunk);

    /**
     * 批量流式文档重点提取（B2 方案，多文件上传）：逐文件 OCR/解析 + AI 整理，
     * 单文件完成后立即调 batchTaskService.appendItem 入库，最后再调 completeBatch 写终态
     * 单文件失败不影响整体
     * @param userId 用户ID
     * @param files 文件载体列表（1-10，controller 在 HTTP 同步返回前已把 MultipartFile 读成 byte[]，避开 Tomcat 临时文件被清理问题）
     * @param promptFormat 用户自定义格式提示词（可空）
     * @param promptGenerate 用户自定义生成内容提示词（可空）
     * @param promptId 系统提示词ID（可空）
     * @param batchId 批量任务 ID（必传，单文件完成后立即调 batchTaskService.appendItem 写入）
     * @return 批量处理结果（successCount / failCount / processedIndex / resultJson），Controller 拿到后调 completeBatch
     */
    com.example.aitools.dto.BatchProcessResult aiDocumentSummaryBatchStream(Long userId, List<BatchFilePayload> files, String promptFormat, String promptGenerate, Long promptId, String batchId);

    /**
     * 批量 OCR 智能识别（B2）：逐文件 OCR + AI 整理，
     * 单文件完成后立即调 batchTaskService.appendItem 入库，最后再调 completeBatch 写终态
     * 单文件失败不影响整体
     * @param userId 用户ID
     * @param files 文件载体列表（1-10，字节已在内存，避开 MultipartFile 临时文件被清理问题）
     * @param promptFormat 用户自定义格式提示词（可空）
     * @param promptGenerate 用户自定义生成内容提示词（可空）
     * @param batchId 批量任务 ID（必传）
     * @return 批量处理结果（successCount / failCount / processedIndex / resultJson）
     */
    com.example.aitools.dto.BatchProcessResult aiOcrBatchStream(Long userId, List<BatchFilePayload> files, String promptFormat, String promptGenerate, String batchId);
}
