package com.example.aitools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 OCR 配置（密钥复用 COS 同一套，可单独覆盖）
 * ocr.enabled=true 启用 ocr-recognize 工具
 */
@Data
@Component
@ConfigurationProperties(prefix = "ocr")
public class OcrConfig {

    /** 是否启用 OCR 能力（false 时 ai-ocr 工具不可用） */
    private Boolean enabled = false;

    /** 腾讯云 SecretId（默认复用 cos.secret-id） */
    private String secretId;

    /** 腾讯云 SecretKey（默认复用 cos.secret-key） */
    private String secretKey;

    /** 地域，如 ap-guangzhou（默认复用 cos.region） */
    private String region;

    /** OCR 模型名，默认 GeneralAccurateOCR */
    private String model = "GeneralAccurateOCR";
}
