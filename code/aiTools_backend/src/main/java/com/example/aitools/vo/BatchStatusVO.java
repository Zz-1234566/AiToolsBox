package com.example.aitools.vo;

import com.example.aitools.entity.BatchTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aitools.dto.BatchFileResult;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 批量任务状态查询 VO（脱敏：剥离内部字段，只展示摘要）
 */
@Data
public class BatchStatusVO {

    private String batchId;
    private String toolCode;
    private Integer fileCount;
    private Integer successCount;
    private Integer failCount;
    private Integer status;
    private String statusLabel;
    private List<BatchFileResult> results;
    private String createTime;
    private String finishedAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static BatchStatusVO from(BatchTask t) {
        BatchStatusVO vo = new BatchStatusVO();
        vo.batchId = t.getBatchId();
        vo.toolCode = t.getToolCode();
        vo.fileCount = t.getFileCount();
        vo.successCount = t.getSuccessCount();
        vo.failCount = t.getFailCount();
        vo.status = t.getStatus();
        vo.statusLabel = statusLabel(t.getStatus());
        vo.results = parseResults(t.getResultSummary());
        vo.createTime = t.getCreateTime() == null ? null : t.getCreateTime().toString();
        vo.finishedAt = t.getFinishedAt() == null ? null : t.getFinishedAt().toString();
        return vo;
    }

    private static String statusLabel(Integer s) {
        if (s == null) return "未知";
        switch (s) {
            case 0: return "待处理";
            case 1: return "处理中";
            case 2: return "已完成";
            case 3: return "部分失败";
            case 4: return "全部失败";
            default: return "未知";
        }
    }

    private static List<BatchFileResult> parseResults(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<BatchFileResult>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
