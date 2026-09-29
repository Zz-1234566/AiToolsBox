package com.example.aitools.vo;

import lombok.Data;

/**
 * 收藏项出参（工具 / 提示词统一结构，供收藏页展示）。
 */
@Data
public class FavoriteVO {

    /** 收藏记录 id */
    private Long id;

    /** tool 工具 / prompt 提示词 */
    private String targetType;

    /** 工具编码 或 提示词 id */
    private String targetId;

    /* ---------- 工具类型字段（targetType=tool 时有值） ---------- */
    private String toolName;
    private String toolDesc;
    private String toolCode;

    /* ---------- 提示词类型字段（targetType=prompt 时有值） ---------- */
    private String promptName;
    private String promptText;
    private String promptUse;

    /** 所属工具编码（工具与提示词共用，供前端显示标签） */
    private String belongToolCode;

    private java.time.LocalDateTime createTime;
}
