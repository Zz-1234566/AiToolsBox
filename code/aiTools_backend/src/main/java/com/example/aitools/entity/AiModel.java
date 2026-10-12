package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_ai_model")
public class AiModel implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelCode;

    private String modelName;

    private String apiUrl;

    private String apiKey;

    private String apiModel;

    /** 服务商标识（minimax/tencent/openai）—— v1.2026-10-12 新增 */
    private String provider;

    /** 能力标签（text/vision/asr/ocr）—— v1.2026-10-12 新增 */
    private String capability;

    /** AES-256-GCM 加密后的 Key（VARBINARY(512)）—— v1.2026-10-12 新增 */
    private byte[] apiKeyEncrypted;

    /** API Key 前 4 位（脱敏展示）—— v1.2026-10-12 新增 */
    private String apiKeyPrefix;

    /** 排序号 —— v1.2026-10-12 新增 */
    private Integer sortNo;

    private Long userId;

    private Integer isDefault;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer dr;
}
