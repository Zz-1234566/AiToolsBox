package com.example.aitools.service;

import com.example.aitools.dto.BatchFilePayload;

/**
 * AI 文件解读服务（MiniMax M3 多模态）
 * <p>B2 方案：逐文件轮询，每个文件独立调用 AI
 */
public interface AiFileReaderService {

    /**
     * 解读单个文件
     * <p>图片 → MiniMax M3 多模态理解；PDF → 转图片逐页读；Word/TXT → 抽文本进 AI
     *
     * @param payload 文件载体（byte[] + originalFilename）
     * @param prompt  用户提示词（可为空，默认"请解读这个文件"）
     * @return AI 解读文本
     */
    String readFile(BatchFilePayload payload, String prompt);
}
