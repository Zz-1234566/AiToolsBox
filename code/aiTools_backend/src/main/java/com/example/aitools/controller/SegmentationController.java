package com.example.aitools.controller;

import com.example.aitools.common.Constants;
import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchUploadResponse;
import com.example.aitools.dto.SegmentResult;
import com.example.aitools.entity.ToolOutput;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.service.FileStorageService;
import com.example.aitools.service.SegmentationService;
import com.example.aitools.service.ToolOutputService;
import com.example.aitools.utils.AuthUtil;
import com.example.aitools.vo.ToolOutputVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 人像分割（图片去背景）Controller。
 * <p>
 * 提供证件照换背景色端点：上传人像照片 → 本地 ONNX 抠图 → 合成红/蓝/白底 → 存 COS + 产物落库。
 * <p>
 * <b>鉴权说明</b>：端点路径不在 {@link Constants#PUBLIC_PATH_PATTERNS} 内，
 * {@code AuthInterceptor} 会自动强制登录，Controller 内无需任何鉴权代码。
 * <p>
 * <b>产物落两处的原因</b>：{@code FileStorageService} 只负责「文件在哪」，
 * {@code ToolOutputService} 负责「这个文件是什么工具的第几个产物、尺寸多大」，
 * 二者缺一不可 —— 前端历史记录读的是产物表，而预览 URL 来自存储层。
 */
@RestController
@RequestMapping("/api/ai-office")
@RequiredArgsConstructor
@Slf4j
public class SegmentationController {

    /** 工具编码，与 sys_aitools_tool.tool_code 一致，历史记录按它归类 */
    private static final String TOOL_CODE = "id-photo-bg-change";

    /** 产物文件名（不含随机串，存储层会再拼） */
    private static final String OUTPUT_FILENAME = "id-photo-bg-change.png";

    /** 图片去/换背景图 tool_code（独立工具，与 id-photo-bg-change 不共享实现） */
    private static final String TOOL_CODE_BG_REMOVER = "image-bg-remover";

    /** image-bg-remover 产物文件名：transparent/color 模式输出 PNG，image 模式输出 JPG。
     *  真实后缀由 mimeType 决定（storage 层会按 mime 取后缀），此处仅作语义占位 */
    private static final String OUTPUT_FILENAME_BG_REMOVER = "image-bg-remover.png";

    private final SegmentationService segmentationService;
    private final ToolOutputService toolOutputService;
    private final BatchTaskService batchTaskService;
    private final AuthUtil authUtil;

    /** 批量任务线程池（核心 4 / 最大 16 / 队列 50）—— 与 OCR 批处理、文件解读同池 */
    @Qualifier(ExecutorConfig.BATCH_EXECUTOR)
    private final ExecutorService batchExecutor;

    /**
     * 证件照换背景色。
     * <p>
     * 独立调用工具（无工作流上下文），故 runId / nodeId / nodeName 全传 null，
     * 产物仅靠 toolCode + fileIndex 关联 —— 与 ToolOutputService 接口约定一致。
     *
     * @param file    待处理的人像证件照（jpg / png / bmp）
     * @param bgColor 底色：red / blue / white，不传默认红色
     * @return 产物 URL、文件名、宽高信息
     */
    @PostMapping("/id-photo-bg-change")
    public Result<ToolOutputVO> idPhotoBgChange(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "bgColor", required = false,
                                                        defaultValue = "red") String bgColor,
                                                HttpServletRequest request) {
        // 1) 入参校验（与 FileController 同一口径：空文件 / 超 20MB 一律拒绝）
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请选择要处理的证件照");
        }
        if (file.getSize() > Constants.MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(), "文件大小不能超过20MB");
        }

        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException e) {
            log.warn("[id-photo-bg-change] 读取上传文件失败 name={}", file.getOriginalFilename(), e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "读取文件失败，请重试");
        }

        // 2) 去背景 + 换底色（非法底色、无法解码、推理失败均由 service 抛业务异常）
        long start = System.currentTimeMillis();
        SegmentResult result = segmentationService.removeBackground(source, bgColor);
        log.info("[id-photo-bg-change] 抠图完成 bgColor={} {}x{} 耗时={}ms",
                bgColor, result.getWidth(), result.getHeight(),
                System.currentTimeMillis() - start);

        // 3) 产物落库（内部先写 COS 再插 sys_tool_output，返回可直接给前端展示的 VO）
        ToolOutputVO vo = toolOutputService.saveFileOutput(
                null, null, null,
                TOOL_CODE,
                0,
                ToolOutput.TYPE_IMAGE,
                result.getImageBytes(),
                OUTPUT_FILENAME,
                result.getMimeType(),
                result.getWidth(),
                result.getHeight(),
                null);

        // 4) 本地存储实现只返回相对路径（/uploads/xxx），统一补成完整 URL
        vo.setFileUrl(FileStorageService.resolveFullUrl(vo.getFileUrl(), request));
        return Result.success("换背景色成功", vo);
    }

    /**
     * 图片去/换背景（image-bg-remover，1-10 个文件，批量），3 模式合一：
     * <ul>
     *   <li>{@code mode=transparent} —— 单纯去背景，输出透明 PNG</li>
     *   <li>{@code mode=color} —— 换底色（需传 {@code backgroundColor=red/blue/white}），输出 PNG</li>
     *   <li>{@code mode=image} —— 换背景图（需传 {@code background} 文件），输出 JPG</li>
     * </ul>
     * <p>
     * 与 {@code /id-photo-bg-change} 的区别：本端点是「批量 + 3 模式合一 + 通用尺寸」，
     * 证件照是「单文件 + 固定 3 色 + 严格像素限」。两个端点不复用实现，独立 tool_code、独立产物。
     * <p>
     * 流程对齐 {@code /document-summary/batch-upload}：HTTP 同步建 batch 任务并立即返回
     * {@code batchId}，前端用 {@code GET /api/ai-office/batch/{batchId}/completed?since=N}
     * 拉增量结果。
     */
    @PostMapping(value = "/image-bg-remover", produces = "application/json;charset=UTF-8")
    public Result<BatchUploadResponse> imageBgRemover(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "mode", defaultValue = "transparent") String mode,
            @RequestParam(value = "backgroundColor", required = false) String backgroundColor,
            @RequestParam(value = "background", required = false) MultipartFile background,
            HttpServletRequest request) {

        Long userId = authUtil.getUserIdFromRequest(request);

        // 1) 入参校验
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "请至少上传 1 张图片");
        }
        if (files.size() > Constants.BATCH_MAX_FILE_COUNT) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "单次最多上传 " + Constants.BATCH_MAX_FILE_COUNT + " 张图片");
        }
        String modeNorm = (mode == null) ? "" : mode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Arrays.asList("transparent", "color", "image").contains(modeNorm)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "mode 必须是 transparent / color / image，当前: " + mode);
        }
        if ("color".equals(modeNorm) && (backgroundColor == null || backgroundColor.isBlank())) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(),
                    "color 模式必须指定 backgroundColor（red/blue/white）");
        }
        if ("image".equals(modeNorm) && (background == null || background.isEmpty())) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(),
                    "image 模式必须上传 background 背景图");
        }
        if (background != null && background.getSize() > Constants.MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(), "背景图不能超过20MB");
        }

        // 2) 同步建任务（HTTP 必须立即返回 batchId）
        String batchId = batchTaskService.createTask(userId, TOOL_CODE_BG_REMOVER, files.size());
        log.info("[image-bg-remover] 创建批量任务 batchId={} userId={} fileCount={} mode={} hasBg={} bgColor={}",
                batchId, userId, files.size(), modeNorm, background != null, backgroundColor);

        // 3) 同步把 MultipartFile 读到 byte[]（避开 Tomcat 异步线程跑批时临时文件已被清理）
        List<BatchFilePayload> payloads;
        byte[] bgBytes = null;
        try {
            if (background != null) {
                try {
                    bgBytes = background.getBytes();
                } catch (IOException e) {
                    throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "读取背景图失败，请重试");
                }
            }
            payloads = files.stream().map(f -> {
                try {
                    return BatchFilePayload.from(f);
                } catch (IOException e) {
                    throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "读取文件失败，请重试");
                }
            }).toList();
        } catch (BusinessException e) {
            log.error("[image-bg-remover] 读取上传文件失败，任务置为失败 batchId={}", batchId, e);
            safeFailBatch(batchId, files.size());
            throw e;
        }

        final byte[] bgFinal = bgBytes;
        final String bgColorFinal = backgroundColor;
        final String modeFinal = modeNorm;

        // 4) 异步跑批
        try {
            batchExecutor.execute(() -> {
                try {
                    batchTaskService.markRunning(batchId);
                    int success = 0;
                    int fail = 0;
                    for (BatchFilePayload p : payloads) {
                        try {
                            // 3 模式合一：交由 service 内部按 mode 分发
                            SegmentResult r = segmentationService.processImage(
                                    p.getContent(), modeFinal, bgColorFinal, bgFinal);
                            // 产物落库（批量任务来源：写 batchId 让 completed 端点能反查）
                            // fileIndex 用循环下标区分同批次多张产物
                            toolOutputService.saveBatchFileOutput(
                                    batchId,
                                    TOOL_CODE_BG_REMOVER,
                                    success + fail,
                                    ToolOutput.TYPE_IMAGE,
                                    r.getImageBytes(),
                                    OUTPUT_FILENAME_BG_REMOVER,
                                    r.getMimeType(),
                                    r.getWidth(),
                                    r.getHeight(),
                                    null);
                            success++;
                        } catch (Exception e) {
                            log.error("[image-bg-remover] 单文件失败 batchId={} file={} err={}",
                                    batchId, p.getOriginalFilename(), e.getMessage());
                            fail++;
                        }
                    }
                    batchTaskService.completeBatch(batchId, success, fail, "[]");
                    log.info("[image-bg-remover] 批量任务完成 batchId={} success={} fail={}",
                            batchId, success, fail);
                } catch (Exception e) {
                    log.error("[image-bg-remover] 批量任务异常 batchId={}", batchId, e);
                    try {
                        batchTaskService.completeBatch(batchId, 0, files.size(), "[]");
                    } catch (Exception ignore) {
                        log.error("[image-bg-remover] 兜底 completeBatch 失败 batchId={}", batchId, ignore);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("[image-bg-remover] 批量任务被拒（线程池饱和）batchId={}", batchId, e);
            safeFailBatch(batchId, files.size());
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE.getCode(),
                    ResultCode.SERVICE_UNAVAILABLE.getMessage());
        }

        BatchUploadResponse resp = new BatchUploadResponse();
        resp.setBatchId(batchId);
        resp.setFileCount(files.size());
        return Result.success("任务已创建", resp);
    }

    /**
     * 兜底：刚建的批量任务若启动失败，必须置为完成态（success=0/fail=count），
     * 否则永久停留在 PENDING，前端已拿到的 batchId 会一直轮询等不到终态。
     */
    private void safeFailBatch(String batchId, int fileCount) {
        try {
            batchTaskService.completeBatch(batchId, 0, fileCount, "[]");
        } catch (Exception e) {
            log.error("[image-bg-remover] safeFailBatch 失败 batchId={}", batchId, e);
        }
    }
}