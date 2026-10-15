package com.example.aitools.vo;

import com.example.aitools.entity.BatchTask;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aitools.dto.BatchFileResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量任务状态查询 VO（脱敏：剥离内部字段，只展示摘要）
 * <p>
 * results 现在自动包含产物信息：调用方传入 {@link ToolOutputVO} 列表后，
 * 每个 item 同时携带「文件产物」字段（fileUrl/fileName/mimeType/width/height 等）
 * 与「处理结果」字段（status/output/errorMsg/costMs），前端无需二次请求即可渲染产物。
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
    /** 本次返回的增量 items（since 之后），如果 since=0 则返回全部。
     *  每个 item 同时包含处理结果（status/output）和文件产物（fileUrl/mimeType/width/height） */
    private List<ResultItem> results;
    private String createTime;
    private String finishedAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 单条结果项：兼容旧 {@link BatchFileResult}（status/output/errorMsg/costMs），
     * 同时承载文件产物信息（outputId/fileName/fileUrl/mimeType/width/height/durationMs/outputType）。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResultItem {
        /** 文件序号（从 1 开始） */
        private Integer index;
        /** 文件名 */
        private String fileName;
        /** 状态：ok / failed */
        private String status;
        /** 单文件处理耗时（毫秒） */
        private Long costMs;
        /** 失败原因（成功时为 null/空） */
        private String errorMsg;
        /** AI 输出文本（成功时为整理结果，失败时为空） */
        private String output;

        // ===== 文件产物字段（来自 sys_tool.output，按 batchId 反查）=====
        /** sys_tool_output.id；前端点击下载/预览时带上，便于后端按需重签 COS 签名 URL */
        private Long outputId;
        /** 完整文件访问 URL（含 COS 签名） */
        private String fileUrl;
        /** MIME 类型，如 image/png */
        private String mimeType;
        /** 图片/视频宽度（像素） */
        private Integer width;
        /** 图片/视频高度（像素） */
        private Integer height;
        /** 音视频时长（毫秒） */
        private Integer durationMs;
        /** 文件字节数 */
        private Long fileSize;
        /** 人类可读的体积，如 1.2 MB */
        private String fileSizeText;
        /** 产物类型：1文本 2图片 3视频 4音频 5文件 */
        private Integer outputType;
    }

    /** 旧接口：返回全部 results（兼容旧前端） */
    public static BatchStatusVO from(BatchTask t) {
        return from(t, 0, Collections.emptyList());
    }

    /**
     * 旧接口：since 增量返回，产物列表为空（不补全 fileUrl 等文件信息）
     */
    public static BatchStatusVO from(BatchTask t, int since) {
        return from(t, since, Collections.emptyList());
    }

    /**
     * 新接口：since 增量返回 + 按 batchId 反查 sys_tool_output，拼到每个 result item 上。
     * <p>
     * 实现思路（最小修改、向后兼容）：
     * <ol>
     *   <li>解析 result_summary JSON 数组得到 {@link BatchFileResult} 列表（旧字段）；</li>
     *   <li>按 fileIndex 顺序把 {@link ToolOutputVO} 列表挂到对应 item（产物字段），</li>
     *       fileIndex 与 result_summary 数组下标对齐（同一文件一次循环、同一 fileIndex）；</li>
     *   <li><b>回退</b>：若 result_summary 为空但 sys_tool_output 有产物（如图片类工具存完产物后调用
     *       {@code completeBatch(batchId, success, fail, "[]")}），则按产物列表重建 items，</li>
     *       每个 item 仅携带产物字段（旧字段 status/output/errorMsg 全 null）。</li>
     *   <li>没有任何产物（如老批次 / 老数据）也不会报错，产物字段保持 null。</li>
     * </ol>
     *
     * @param t       数据库任务实体
     * @param since   前端已收到的 items 数（0=全部；N=只返回 N 之后的新 items）
     * @param outputs 按 batchId 反查 sys_tool_output 得到的产物列表（可空，nullable）
     */
    public static BatchStatusVO from(BatchTask t, int since, List<ToolOutputVO> outputs) {
        BatchStatusVO vo = new BatchStatusVO();
        vo.batchId = t.getBatchId();
        vo.toolCode = t.getToolCode();
        vo.fileCount = t.getFileCount();
        vo.successCount = t.getSuccessCount();
        vo.failCount = t.getFailCount();
        vo.processedIndex = t.getProcessedIndex() == null ? 0 : t.getProcessedIndex();
        vo.status = t.getStatus();
        vo.statusLabel = statusLabel(t.getStatus());

        List<ToolOutputVO> safeOutputs = outputs == null ? Collections.emptyList() : outputs;
        List<BatchFileResult> all = parseResults(t.getResultSummary());
        if (since < 0) since = 0;

        // 回退：result_summary 空但 sys_tool_output 有产物（图片类工具典型场景）
        if (all.isEmpty() && !safeOutputs.isEmpty()) {
            List<ResultItem> items = new ArrayList<>(safeOutputs.size());
            for (int i = 0; i < safeOutputs.size(); i++) {
                if (i < since) continue; // 增量裁剪
                ToolOutputVO o = safeOutputs.get(i);
                ResultItem item = new ResultItem();
                item.setIndex(i);
                item.setStatus("ok"); // 落进 sys_tool_output 即视为成功
                mergeOutput(item, o);
                items.add(item);
            }
            vo.results = items;
            vo.createTime = t.getCreateTime() == null ? null : t.getCreateTime().toString();
            vo.finishedAt = t.getFinishedAt() == null ? null : t.getFinishedAt().toString();
            return vo;
        }

        // 主路径：按 result_summary 数组构建 items，挂产物字段
        int fromIdx = Math.min(since, all.size());
        List<BatchFileResult> slice = all.subList(fromIdx, all.size());
        List<ResultItem> items = new ArrayList<>(slice.size());
        for (int i = 0; i < slice.size(); i++) {
            BatchFileResult r = slice.get(i);
            ResultItem item = toItem(r);
            int fileIdx = fromIdx + i;
            ToolOutputVO match = findByFileIndex(safeOutputs, fileIdx);
            if (match != null) {
                mergeOutput(item, match);
            }
            items.add(item);
        }
        vo.results = items;
        vo.createTime = t.getCreateTime() == null ? null : t.getCreateTime().toString();
        vo.finishedAt = t.getFinishedAt() == null ? null : t.getFinishedAt().toString();
        return vo;
    }

    private static ResultItem toItem(BatchFileResult r) {
        ResultItem item = new ResultItem();
        item.setIndex(r.getIndex());
        item.setFileName(r.getFileName());
        item.setStatus(r.getStatus());
        item.setCostMs(r.getCostMs());
        item.setErrorMsg(r.getErrorMsg());
        item.setOutput(r.getOutput());
        return item;
    }

    private static void mergeOutput(ResultItem item, ToolOutputVO o) {
        item.setOutputId(o.getId());
        item.setFileUrl(o.getFileUrl());
        item.setMimeType(o.getMimeType());
        item.setWidth(o.getWidth());
        item.setHeight(o.getHeight());
        item.setDurationMs(o.getDurationMs());
        item.setFileSize(o.getFileSize());
        item.setFileSizeText(o.getFileSizeText());
        item.setOutputType(o.getOutputType());
        // 产物表里的 fileName 比 result_summary 里的更准（带后缀），覆盖
        if (o.getFileName() != null && !o.getFileName().isBlank()) {
            item.setFileName(o.getFileName());
        }
    }

    private static ToolOutputVO findByFileIndex(List<ToolOutputVO> outputs, int fileIndex) {
        for (ToolOutputVO o : outputs) {
            if (o != null && o.getFileIndex() != null && o.getFileIndex() == fileIndex) {
                return o;
            }
        }
        return null;
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
