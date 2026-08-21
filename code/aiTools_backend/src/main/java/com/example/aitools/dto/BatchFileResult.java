package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量任务中单个文件的结果（用于 result_summary JSON 数组里）
 * 字段说明：
 *  - index: 文件序号（从 1 开始）
 *  - fileName: 文件名
 *  - status: "ok" | "failed"（兼容旧 "OK" / "FAIL"）
 *  - costMs: 单文件处理耗时（毫秒）
 *  - errorMsg: 失败原因（成功时为 null/空）
 *  - output: AI 输出文本（成功时为整理结果，失败时为空）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchFileResult {

    /** 文件序号（从 1 开始） */
    private Integer index;

    /** 文件名 */
    private String fileName;

    /** 状态：ok / failed（兼容旧 OK / FAIL） */
    private String status;

    /** 单文件处理耗时（毫秒） */
    private Long costMs;

    /** 失败原因（成功时为 null/空） */
    private String errorMsg;

    /** AI 输出文本（成功时为整理结果，失败时为空） */
    private String output;
}
