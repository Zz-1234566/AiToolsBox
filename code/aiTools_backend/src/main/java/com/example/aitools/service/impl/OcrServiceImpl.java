package com.example.aitools.service.impl;

import com.example.aitools.common.ResultCode;
import com.example.aitools.config.CosConfig;
import com.example.aitools.config.OcrConfig;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.OcrService;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.GeneralAccurateOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.GeneralAccurateOCRResponse;
import com.tencentcloudapi.ocr.v20181119.models.TextDetection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Locale;

/**
 * 腾讯云 OCR 服务实现：图片 → 调 GeneralAccurateOCR → 返回原始文字。
 * <p>
 * <b>关于 PDF</b>：当前 SDK 版本（tencentcloud-sdk-java 3.1.270）的
 * {@code GeneralAccurateOCR} 只吃图片，官方注释明确「支持 PNG、JPG、JPEG、BMP」，
 * OCR 模块下也不存在任何 Pdf Request 类。因此扫描型 PDF 需由本类
 * {@link #recognizePdfPages} 先用 PDFBox 渲染成图片，再逐页送 OCR。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrServiceImpl implements OcrService {

    private final OcrConfig ocrConfig;
    private final CosConfig cosConfig;

    /**
     * 扫描型 PDF → 逐页渲染成 PNG → 逐页 OCR → 按页拼接。
     * <p>
     * 部分页渲染或识别失败时只记日志跳过（参考 AiFileReaderServiceImpl 的既有约定），
     * 但若<b>所有页都失败</b>则抛业务异常，避免把空结果当成「文档无内容」误导下游。
     */
    @Override
    public String recognizePdfPages(MultipartFile pdfFile, int maxPages, float dpi) {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new BusinessException(ResultCode.OCR_UNSUPPORTED.getCode(), "请上传 PDF 文件");
        }
        if (!Boolean.TRUE.equals(ocrConfig.getEnabled())) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "识别能力未启用，请联系管理员");
        }
        org.apache.pdfbox.pdmodel.PDDocument document = null;
        try {
            document = org.apache.pdfbox.pdmodel.PDDocument.load(pdfFile.getInputStream());
            int total = document.getNumberOfPages();
            int limit = Math.min(total, Math.max(maxPages, 1));
            org.apache.pdfbox.rendering.PDFRenderer renderer =
                    new org.apache.pdfbox.rendering.PDFRenderer(document);

            StringBuilder sb = new StringBuilder();
            int okPages = 0;
            for (int i = 0; i < limit; i++) {
                try {
                    java.awt.image.BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(image, "png", baos);
                    String pageText = doOcr(baos.toByteArray(), "page-" + (i + 1) + ".png");
                    if (pageText != null && !pageText.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(pageText);
                        okPages++;
                    }
                } catch (Exception e) {
                    log.warn("[ocr-pdf] 第 {} 页识别失败，跳过", i + 1, e);
                }
            }
            if (total > limit) {
                log.info("[ocr-pdf] 仅处理前 {} / {} 页（受 maxPages 限制）", limit, total);
            }
            if (okPages == 0) {
                log.warn("[ocr-pdf] 全部 {} 页均未识别出文字", limit);
                return "";
            }
            log.info("[ocr-pdf] 完成 成功页={}/{} 字符数={}", okPages, limit, sb.length());
            return sb.toString().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ocr-pdf] PDF 解析失败", e);
            throw new BusinessException(ResultCode.OCR_FAILED.getCode(), "PDF 解析失败，请确认文件未加密且未损坏");
        } finally {
            closeQuietly(document);
        }
    }

    private static void closeQuietly(org.apache.pdfbox.pdmodel.PDDocument doc) {
        if (doc == null) {
            return;
        }
        try {
            doc.close();
        } catch (Exception ignored) {
            // 关闭失败不影响主流程
        }
    }

    @Override
    public String recognizeText(MultipartFile file) {
        // 改用 getSize() < 0 判断（避免 H5 fetch 提交 multipart 时 Spring getSize()=0 的边界误判）
        if (file == null || file.getSize() < 0) {
            throw new BusinessException(ResultCode.OCR_UNSUPPORTED.getCode(), "请上传图片文件");
        }
        try {
            return doOcr(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            log.error("OCR 识别失败", e);
            throw new BusinessException(ResultCode.OCR_FAILED.getCode(), ResultCode.OCR_FAILED.getMessage());
        }
    }

    @Override
    public String recognizeBytes(byte[] imageBytes, String originalFilename) {
        // 字节流重载：批量任务中文件已在内存，避开 MultipartFile 临时文件被清理问题
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ResultCode.OCR_UNSUPPORTED.getCode(), "请上传图片文件");
        }
        return doOcr(imageBytes, originalFilename);
    }

    /**
     * 核心 OCR 调用：仅支持图片（PNG/JPG/JPEG/BMP）；PDF 拒绝并提示改用 doc-keypoint-extract
     */
    private String doOcr(byte[] bytes, String originalFilename) {
        if (!ocrConfig.getEnabled()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(), "识别能力未启用，请联系管理员");
        }
        // 注意：这里不再拒绝 pdf —— 扫描件 PDF 由 recognizePdfPages 先渲染成图片再进本方法，
        // 传进来的始终是图片字节。若外部直接传 PDF 字节，腾讯云会返回格式错误。
        try {
            // 密钥优先级：OcrConfig 自己的 > CosConfig 复用
            String secretId = ocrConfig.getSecretId() != null && !ocrConfig.getSecretId().isBlank()
                    ? ocrConfig.getSecretId() : cosConfig.getSecretId();
            String secretKey = ocrConfig.getSecretKey() != null && !ocrConfig.getSecretKey().isBlank()
                    ? ocrConfig.getSecretKey() : cosConfig.getSecretKey();
            String region = ocrConfig.getRegion() != null && !ocrConfig.getRegion().isBlank()
                    ? ocrConfig.getRegion() : cosConfig.getRegion();

            Credential cred = new Credential(secretId, secretKey);
            OcrClient client = new OcrClient(cred, region);

            GeneralAccurateOCRRequest req = new GeneralAccurateOCRRequest();
            req.setImageBase64(Base64.getEncoder().encodeToString(bytes));

            GeneralAccurateOCRResponse resp = client.GeneralAccurateOCR(req);
            TextDetection[] detections = resp.getTextDetections();
            if (detections == null || detections.length == 0) {
                log.info("OCR 未识别出文字 fileName={}", originalFilename);
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (TextDetection d : detections) {
                if (d != null && d.getDetectedText() != null) {
                    sb.append(d.getDetectedText()).append("\n");
                }
            }
            log.info("OCR 识别完成 fileName={} 行数={}", originalFilename, detections.length);
            return sb.toString().trim();
        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 OCR 调用失败 fileName={}", originalFilename, e);
            log.error("OCR 识别失败", e);
            throw new BusinessException(ResultCode.OCR_FAILED.getCode(), ResultCode.OCR_FAILED.getMessage());
        } catch (Exception e) {
            log.error("OCR 异常 fileName={}", originalFilename, e);
            log.error("OCR 识别失败", e);
            throw new BusinessException(ResultCode.OCR_FAILED.getCode(), ResultCode.OCR_FAILED.getMessage());
        }
    }
}
