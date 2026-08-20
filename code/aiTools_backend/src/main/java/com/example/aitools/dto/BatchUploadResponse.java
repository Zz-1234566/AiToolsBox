package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 批量上传响应：返回 batchId，客户端用 batchId 订阅 SSE 流
 */
@Data
@AllArgsConstructor
public class BatchUploadResponse {

    /** 任务 ID（UUID） */
    private String batchId;

    /** 接收的文件数 */
    private Integer fileCount;
}
