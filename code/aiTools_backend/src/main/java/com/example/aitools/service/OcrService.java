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

    /**
     * 识别扫描型 PDF 中的文字：逐页渲染成图片后送腾讯云 OCR，再按页拼接。
     * <p>
     * 腾讯云 OCR 接口本身只吃图片（PNG/JPG/JPEG/BMP），不支持 PDF，
     * 因此扫描件 PDF 必须由调用方先转成图片 —— 本方法封装了这一步。
     *
     * @param pdfFile   PDF 文件
     * @param maxPages  最多处理页数，超出部分丢弃（控制 OCR 调用成本）
     * @param dpi       渲染 DPI，越高越清晰但耗时与体积越大
     * @return 全部页面的识别文字（页间以空行分隔）；无可识别内容时返回空串
     */
    String recognizePdfPages(MultipartFile pdfFile, int maxPages, float dpi);
}
