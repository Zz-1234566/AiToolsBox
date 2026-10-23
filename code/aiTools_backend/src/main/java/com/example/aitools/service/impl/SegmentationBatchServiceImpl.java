package com.example.aitools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aitools.common.ToolCodes;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.SegmentResult;
import com.example.aitools.entity.AiTool;
import com.example.aitools.entity.ToolOutput;
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.service.HistoryService;
import com.example.aitools.service.SegmentationBatchService;
import com.example.aitools.service.SegmentationService;
import com.example.aitools.service.ToolOutputService;
import com.example.aitools.vo.ToolOutputVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图片去/换背景批量处理实现。
 *
 * <p>编排逻辑（逐文件）：建「处理中」历史 → 推理 → 落产物 → 完成历史；
 * 单文件失败只记失败并计数，不中断整批；末尾统一置任务终态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentationBatchServiceImpl implements SegmentationBatchService {

    /** 产物落库时用的文件名（扩展名固定，实际编码由 mime 决定） */
    private static final String OUTPUT_FILENAME_BG_REMOVER = "image-bg-remover.png";

    private final SegmentationService segmentationService;
    private final ToolOutputService toolOutputService;
    private final BatchTaskService batchTaskService;
    private final HistoryService historyService;
    private final AiToolMapper aiToolMapper;

    @Override
    public void processImageBatch(Long userId, List<BatchFilePayload> payloads,
                                  String mode, String backgroundColor, byte[] backgroundImage,
                                  String batchId) {
        int fileCount = payloads == null ? 0 : payloads.size();
        try {
            if (!batchTaskService.markRunning(batchId)) {
                // 任务行不存在 / 状态已不是 PENDING：不再跑推理
                throw new IllegalStateException("批量任务状态推进失败，任务不存在或已非待处理状态");
            }
            int success = 0;
            int fail = 0;
            // 工具 id 查一次即可（N 个文件共用），查不到不阻断（历史允许 tool_id 为空）
            Long bgRemoverToolId = findToolId(ToolCodes.IMAGE_BG_REMOVER);
            for (BatchFilePayload p : payloads) {
                String fileName = p.getOriginalFilename();
                // 先建「处理中」历史，保证产物一产出就有记录（与 OCR / doc-to-text 同口径）
                Long historyId = historyService.createPendingHistory(
                        userId, bgRemoverToolId, null, ToolCodes.IMAGE_BG_REMOVER,
                        "上传图片：" + fileName);
                long fileStart = System.currentTimeMillis();
                try {
                    // 3 模式合一：交由 SegmentationService 内部按 mode 分发
                    SegmentResult r = segmentationService.processImage(
                            p.getContent(), mode, backgroundColor, backgroundImage);
                    // 产物落库（批量任务来源：写 batchId 让 completed 端点能反查）
                    // fileIndex 用「已处理文件数」区分同批次多张产物（0-based 整批位置）
                    ToolOutputVO output = toolOutputService.saveBatchFileOutput(
                            batchId,
                            ToolCodes.IMAGE_BG_REMOVER,
                            success + fail,
                            ToolOutput.TYPE_IMAGE,
                            r.getImageBytes(),
                            OUTPUT_FILENAME_BG_REMOVER,
                            r.getMimeType(),
                            r.getWidth(),
                            r.getHeight(),
                            null);
                    // 历史输出内容存产物 URL（产物本体在 sys_tool_output，可按 batchId 回查）
                    historyService.completeHistory(historyId, output.getFileUrl(),
                            (int) (System.currentTimeMillis() - fileStart));
                    success++;
                } catch (Exception e) {
                    log.error("[image-bg-remover] 单文件失败 batchId={} file={} err={}",
                            batchId, fileName, e.getMessage());
                    historyService.failHistory(historyId, e.getMessage());
                    fail++;
                } finally {
                    // 释放本文件的字节缓存：批量总大小上限 200MB，
                    // 不逐个释放的话 N 个文件的字节会同时驻留堆里（等于没做落盘优化）
                    p.release();
                }
            }
            batchTaskService.completeBatch(batchId, success, fail, "[]");
            log.info("[image-bg-remover] 批量任务完成 batchId={} success={} fail={}",
                    batchId, success, fail);
        } catch (Exception e) {
            log.error("[image-bg-remover] 批量任务异常 batchId={}", batchId, e);
            try {
                batchTaskService.failRemaining(batchId, fileCount);
            } catch (Exception ignore) {
                log.error("[image-bg-remover] 兜底 completeBatch 失败 batchId={}", batchId, ignore);
            }
        }
    }

    @Override
    public void failBatch(String batchId, int fileCount) {
        try {
            batchTaskService.failRemaining(batchId, fileCount);
        } catch (Exception e) {
            log.error("[image-bg-remover] failBatch 失败 batchId={}", batchId, e);
        }
    }

    /**
     * 查工具 id（查不到不阻断：历史允许 tool_id 为空）。
     */
    private Long findToolId(String toolCode) {
        try {
            AiTool tool = aiToolMapper.selectOne(
                    new LambdaQueryWrapper<AiTool>()
                            .eq(AiTool::getToolCode, toolCode)
                            .last("LIMIT 1"));
            return tool == null ? null : tool.getId();
        } catch (Exception e) {
            log.warn("[image-bg-remover] 查询工具 id 失败 toolCode={}", toolCode, e);
            return null;
        }
    }
}
