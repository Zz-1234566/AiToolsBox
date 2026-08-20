package com.example.aitools.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 腾讯云 OCR 服务接口
 * 实现类：service/impl/OcrServiceImpl.java
 * 密钥默认复用 CosConfig（COS 同账号），可用 OcrConfig 单独覆盖
 */
public interface OcrService {

    /**
     * 识别图片中的文字
     * @param file 上传的图片（jpg/png/pdf）
     * @return 识别出的全部文字（按行拼接）
     */
    String recognizeText(MultipartFile file);

    /**
     * 识别图片中的文字（字节流重载，用于批量任务中文件已在内存的场景，避开 MultipartFile 临时文件被清理问题）
     * @param imageBytes 图片字节
     * @param originalFilename 原始文件名（保留扩展名，腾讯云 PDF 接口识别需要）
     * @return 识别出的全部文字（按行拼接）
     */
    String recognizeBytes(byte[] imageBytes, String originalFilename);
}
