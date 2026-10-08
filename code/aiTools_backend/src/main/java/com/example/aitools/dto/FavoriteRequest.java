package com.example.aitools.dto;

import lombok.Data;

/**
 * 收藏操作请求体（添加 / 取消）。
 */
@Data
public class FavoriteRequest {

    /** tool 工具 / prompt 提示词 */
    private String targetType;

    /** tool: tool_code；prompt: prompt_id */
    private String targetId;
}
