package com.example.aitools.service.impl;

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
 * 腾讯云 OCR 服务实现：图片 → 调 GeneralAccurateOCR → 返回原始文字
 * 注意：当前 SDK 版本（tencentcloud-sdk-java 3.1.270）只支持图片，不支持 PDF。
 * PDF 用户应改用 doc-keypoint-extract 工具（DocumentParser 用 PDFBox 解析数字 PDF 文本层）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrServiceImpl implements OcrService {

    private final OcrConfig ocrConfig;
    private final CosConfig cosConfig;

    @Override
    public String recognizeText(MultipartFile file) {
        // 改用 getSize() < 0 判断（避免 H5 fetch 提交 multipart 时 Spring getSize()=0 的边界误判）
        if (file == null || file.getSize() < 0) {
            throw new BusinessException("请上传图片文件");
        }
        try {
            return doOcr(file.getBytes(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new BusinessException("OCR 识别失败：" + e.getMessage());
        }
    }

    @Override
    public String recognizeBytes(byte[] imageBytes, String originalFilename) {
        // 字节流重载：批量任务中文件已在内存，避开 MultipartFile 临时文件被清理问题
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("请上传图片文件");
        }
        return doOcr(imageBytes, originalFilename);
    }

    /**
     * 核心 OCR 调用：仅支持图片（PNG/JPG/JPEG/BMP）；PDF 拒绝并提示改用 doc-keypoint-extract
     */
    private String doOcr(byte[] bytes, String originalFilename) {
        if (!ocrConfig.getEnabled()) {
            throw new BusinessException("OCR 能力未启用（请在 application-dev.yml 里设 ocr.enabled=true）");
        }
        if (originalFilename != null && originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException("当前 OCR 工具仅支持图片（PNG/JPG/JPEG/BMP），PDF 请改用【文档重点提取】工具");
        }
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
            throw new BusinessException("OCR 识别失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("OCR 异常 fileName={}", originalFilename, e);
            throw new BusinessException("OCR 识别失败：" + e.getMessage());
        }
    }
}
