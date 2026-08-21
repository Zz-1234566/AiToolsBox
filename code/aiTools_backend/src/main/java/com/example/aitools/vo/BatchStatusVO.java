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
    /** 已处理文件数（成功+失败），前端轮询 since 增量时使用 */
    private Integer processedIndex;
    private Integer status;
    private String statusLabel;
    /** 本次返回的增量 items（since 之后），如果 since=0 则返回全部 */
    private List<BatchFileResult> results;
    private String createTime;
    private String finishedAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 旧接口：返回全部 results（兼容旧前端） */
    public static BatchStatusVO from(BatchTask t) {
        return from(t, 0);
    }

    /**
     * 新接口：since 增量返回
     * @param t 数据库任务实体
     * @param since 前端已收到的 items 数（0=全部；N=只返回 N 之后的新 items）
     */
    public static BatchStatusVO from(BatchTask t, int since) {
        BatchStatusVO vo = new BatchStatusVO();
        vo.batchId = t.getBatchId();
        vo.toolCode = t.getToolCode();
        vo.fileCount = t.getFileCount();
        vo.successCount = t.getSuccessCount();
        vo.failCount = t.getFailCount();
        vo.processedIndex = t.getProcessedIndex() == null ? 0 : t.getProcessedIndex();
        vo.status = t.getStatus();
        vo.statusLabel = statusLabel(t.getStatus());
        // 解析 result_summary 数组，按 since 截取增量
        List<BatchFileResult> all = parseResults(t.getResultSummary());
        if (since < 0) since = 0;
        if (since >= all.size()) {
            vo.results = Collections.emptyList();
        } else {
            vo.results = all.subList(since, all.size());
        }
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
