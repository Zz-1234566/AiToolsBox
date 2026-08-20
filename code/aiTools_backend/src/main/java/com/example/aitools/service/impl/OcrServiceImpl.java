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

/**
 * 腾讯云 OCR 服务实现：上传图片 → 调 GeneralAccurateOCR → 返回原始文字
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrServiceImpl implements OcrService {

    private final OcrConfig ocrConfig;
    private final CosConfig cosConfig;

    @Override
    public String recognizeText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传图片文件");
        }
        if (!ocrConfig.getEnabled()) {
            throw new BusinessException("OCR 能力未启用（请在 application-dev.yml 里设 ocr.enabled=true）");
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
            req.setImageBase64(Base64.getEncoder().encodeToString(file.getBytes()));
            // req.setImageUrl(null);  // 二选一，这里走 base64

            GeneralAccurateOCRResponse resp = client.GeneralAccurateOCR(req);
            TextDetection[] detections = resp.getTextDetections();
            if (detections == null || detections.length == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (TextDetection d : detections) {
                if (d != null && d.getDetectedText() != null) {
                    sb.append(d.getDetectedText()).append("\n");
                }
            }
            log.info("OCR 识别完成，行数：{}", detections.length);
            return sb.toString().trim();
        } catch (TencentCloudSDKException e) {
            log.error("腾讯云 OCR 调用失败", e);
            throw new BusinessException("OCR 识别失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("OCR 异常", e);
            throw new BusinessException("OCR 识别失败：" + e.getMessage());
        }
    }
}
