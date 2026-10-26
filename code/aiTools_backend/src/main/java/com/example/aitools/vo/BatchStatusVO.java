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
 * <p>
 * <b>item.index 的口径：统一 1-based</b>（与 {@link BatchFileResult#getIndex()} 一致）。
 * 两条构造路径都遵守：
 * <ul>
 *   <li>主路径（result_summary 非空）：取 {@code BatchFileResult.index}（1-based）；</li>
 *   <li>回退路径（result_summary 为空、按产物重建）：取产物的 {@code fileIndex + 1}
 *       —— {@code fileIndex} 是该文件在**整批**中的 0-based 位置，
 *       因此中间有文件失败时序号会留空档（如 [1/3]、[3/3]），能看出少了哪个。</li>
 * </ul>
 * 另有 {@code since} 参数，口径是**「已返回的条数」**（不是文件序号），
 * 前端 {@code since += results.length} 与之对应。
 * <p>
 * <b>兼容层说明</b>：本类保留了 {@link #from(BatchTask)} 与 {@link #from(BatchTask, int)}
 * 两个旧重载（已标 {@code @Deprecated}），供可能仍在调用它们的旧版 App 使用。
 * 现役前端只走 {@code GET /api/ai-office/batch/{batchId}/completed}。
 * <b>下线条件</b>：确认线上已无旧版 App 在调后，即可连同 Controller 里的
 * {@code batch-stream} / {@code batch-status} 两个旧端点一起删除
 * （需发版数据才能判定，代码里看不出来）。
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
    /**
     * 任务级失败原因（如「服务重启导致任务中断，请重新提交」）。
     * <p>成功时为 null。整批失败且没有任何单文件结果时，前端靠这个字段说明原因。
     */
    private String errorMsg;
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

    /**
     * 旧接口：返回全部 results（兼容旧前端）。
     *
     * @deprecated 现役前端只走 {@code GET /api/ai-office/batch/{batchId}/completed}
     *     （即 {@link #from(BatchTask, int, List)}）。本重载零调用方，
     *     保留仅为兼容可能仍在调用它的旧版 App —— **确认线上无旧版后即可删除**。
     */
    @Deprecated
    public static BatchStatusVO from(BatchTask t) {
        return from(t, 0, Collections.emptyList());
    }

    /**
     * 旧接口：since 增量返回，产物列表为空（不补全 fileUrl 等文件信息）。
     *
     * @deprecated 同 {@link #from(BatchTask)}，零调用方，
     *     确认线上无旧版 App 在调后即可删除。
     */
    @Deprecated
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
        vo.errorMsg = t.getErrorMsg();

        List<ToolOutputVO> safeOutputs = outputs == null ? Collections.emptyList() : outputs;
        List<BatchFileResult> all = parseResults(t.getResultSummary());
        if (since < 0) since = 0;

        // 回退：result_summary 空但 sys_tool_output 有产物（图片类工具典型场景）
        if (all.isEmpty() && !safeOutputs.isEmpty()) {
            List<ResultItem> items = new ArrayList<>(safeOutputs.size());
            for (int i = 0; i < safeOutputs.size(); i++) {
                if (i < since) continue; // 增量裁剪（since = 前端已收到的条数）
                ToolOutputVO o = safeOutputs.get(i);
                ResultItem item = new ResultItem();
                // 序号统一 1-based（与主路径 BatchFileResult.index 及本类 Javadoc 一致）。
                // 优先取产物记录的 fileIndex（该文件在**整批**中的 0-based 位置），
                // 这样中间若有文件失败，序号会留空档（如 [1/3]、[3/3]），能看出少了哪个；
                // fileIndex 缺失时回退为「成功产物中的次序 + 1」。
                Integer fileIdx = o.getFileIndex();
                item.setIndex(fileIdx != null ? fileIdx + 1 : i + 1);
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
            // 匹配产物用 **result_summary 里自带的序号**（BatchFileResult.index，1-based）换算成
            // 0-based 位置，而不是「数组下标 fromIdx + i」。
            // 两者只在「每个文件都恰好在 result_summary 里占一位」时相等；
            // 一旦 result_summary 缺项（落库失败 / 历史数据），下标就会与真实文件位置错位，
            // 导致产物挂到错误的文件上。取不到 index 时才回退到下标。
            Integer idx = r.getIndex();
            int fileIdx = (idx != null && idx > 0) ? idx - 1 : fromIdx + i;
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
