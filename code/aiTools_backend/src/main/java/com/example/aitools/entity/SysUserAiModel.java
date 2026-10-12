package com.example.aitools.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户自配 AI 模型实体（BYOK 模式）。
 * <p>
 * 加密后的 API Key 存在 {@link #apiKeyEncrypted}（VARBINARY(512)），
 * 前缀展示 {@link #apiKeyPrefix}（前 4 位明文）。
 * <p>
 * 加解密使用 AES-256-GCM，工具类 {@code com.example.aitools.utils.AesKeyUtils}。
 */
@Data
@TableName("sys_user_ai_model")
public class SysUserAiModel implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 引用的模型编码，对应 sys_ai_model.model_code */
    private String modelCode;

    /** AES-256-GCM 加密后的 API Key 字节流 */
    private byte[] apiKeyEncrypted;

    /** API Key 前 4 位（脱敏展示） */
    private String apiKeyPrefix;

    /** 用户起的别名（如「我的 key A」） */
    private String nickname;

    /** 能力（text / vision / asr / ocr），冗余字段方便查询 */
    private String capability;

    /** 是否用户默认（每 capability 1 个） */
    private Integer isDefault;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 上次使用时间（懒清理用） */
    private LocalDateTime lastUsedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer dr;
}