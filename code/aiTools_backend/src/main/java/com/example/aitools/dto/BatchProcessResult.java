package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量文档处理结果（多文件上传 B2 方案用）
 * 由 AiOfficeToolServiceImpl.aiDocumentSummaryBatchStream 返回
 * Controller 拿到后调 batchTaskService.completeBatch(...) 把 resultJson / successCount / failCount 入库
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchProcessResult {
    /** 成功文件数 */
    private int successCount;
    /** 失败文件数 */
    private int failCount;
    /** 已处理文件数（成功+失败，== successCount + failCount；Controller 入库后回传前端用于轮询 since） */
    private int processedIndex;
    /** 每文件结果的 JSON 数组（[{fileName,status,output}, ...]），写入 sys_batch_task.result_summary */
    private String resultJson;
    /** 全部文件的人类可读拼接文本（用于兜底/调试，非必需入库） */
    private String batchOutput;
}
