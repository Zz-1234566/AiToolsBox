package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量任务中单个文件的结果（用于 result_summary JSON 数组里）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchFileResult {

    /** 文件名 */
    private String fileName;

    /** 状态：OK / FAIL */
    private String status;

    /** AI 输出（失败时为错误信息） */
    private String output;
}
