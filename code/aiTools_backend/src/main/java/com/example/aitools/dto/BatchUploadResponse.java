package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量上传响应：返回 batchId + fileCount，客户端拿 batchId 去轮询 /completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponse {

    /** 任务 ID（UUID） */
    private String batchId;

    /** 接收的文件数 */
    private Integer fileCount;
}
