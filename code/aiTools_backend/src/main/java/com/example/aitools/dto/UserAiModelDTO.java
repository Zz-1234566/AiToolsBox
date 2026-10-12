package com.example.aitools.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 AI 模型 DTO（前端交互 / 响应统一对象）。
 * <p>
 * <b>安全约定</b>：本 DTO <b>永远不会</b>携带 {@code apiKey} 明文 / 加密值。
 * 入参 {@link #apiKey} 只在 {@code Save} 场景接收前端提交；
 * 返回值用 {@link #apiKeyPrefix} 替代。
 */
@Data
public class UserAiModelDTO {

    private Long id;

    private Long userId;

    @NotBlank(message = "modelCode 不能为空")
    private String modelCode;

    /**
     * 仅写入时有效。后端会立刻加密，<b>不会</b>以任何形式回传给前端。
     */
    private String apiKey;

    /**
     * 明文前 4 位。仅展示用。
     */
    private String apiKeyPrefix;

    private String nickname;

    /** 冗余字段(text/vision/asr/ocr) */
    private String capability;

    private Integer isDefault;

    private Integer status;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}