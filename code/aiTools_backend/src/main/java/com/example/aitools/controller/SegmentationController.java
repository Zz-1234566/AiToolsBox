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
import com.example.aitools.mapper.AiToolMapper;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.service.FileStorageService;
import com.example.aitools.service.HistoryService;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 人像分割（图片去背景）Controller。
 * <p>
 * 提供图片去/换背景端点：上传人像照片 → 本地 ONNX 抠图 → 合成透明底 / 纯色底 / 背景图
 * → 存 COS + 产物落库 + 写调用历史。
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

    /**
     * 图片去/换背景图 tool_code（与人像分割共用实现，产品上只保留这一个图片工具）
     */
    private static final String TOOL_CODE_BG_REMOVER = "image-bg-remover";

    /** image-bg-remover 产物文件名：transparent/color 模式输出 PNG，image 模式输出 JPG。
     *  真实后缀由 mimeType 决定（storage 层会按 mime 取后缀），此处仅作语义占位 */
    private static final String OUTPUT_FILENAME_BG_REMOVER = "image-bg-remover.png";

    /** image-bg-remover 允许的 MIME 白名单（与 SegmentationService 一致）。
     *  前端 image-bg-remover.vue 的 ALLOWED_EXTS 必须与此表保持同步。 */
    private static final List<String> ALLOWED_IMAGE_TYPES =
            Arrays.asList("image/jpeg", "image/png", "image/bmp", "image/gif");

    /** image-bg-remover 允许的文件扩展名白名单（兜底：客户端未传 contentType 时使用）。 */
    private static final Set<String> ALLOWED_IMAGE_EXTS =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "bmp", "gif"));

    private final SegmentationService segmentationService;
    private final ToolOutputService toolOutputService;
    private final BatchTaskService batchTaskService;
    private final HistoryService historyService;
    private final AiToolMapper aiToolMapper;
    private final AuthUtil authUtil;

    /** 批量任务线程池（核心 4 / 最大 16 / 队列 50）—— 与 OCR 批处理、文件解读同池 */
    @Qualifier(ExecutorConfig.BATCH_EXECUTOR)
    private final ExecutorService batchExecutor;

    /**
     * 图片去/换背景（image-bg-remover，1-10 个文件，批量），3 模式合一：
     * <ul>
     *   <li>{@code mode=transparent} —— 单纯去背景，输出透明 PNG</li>
     *   <li>{@code mode=color} —— 换底色（需传 {@code backgroundColor=red/blue/white}），输出 PNG</li>
     *   <li>{@code mode=image} —— 换背景图（需传 {@code background} 文件），输出 JPG</li>
     * </ul>
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
        // 1.1) 文件类型白名单（contentType / 扩展名 任一通过即放行；
        //      webp 等不在白名单内的格式直接拒，避免走到 service 才抛异常）
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            String filename = file.getOriginalFilename();
            String ext = (filename != null && filename.contains("."))
                    ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                    : "";

            boolean typeOk = contentType != null && ALLOWED_IMAGE_TYPES.contains(contentType);
            boolean extOk = !ext.isEmpty() && ALLOWED_IMAGE_EXTS.contains(ext);

            if (!typeOk && !extOk) {
                String ctx = (contentType != null ? contentType : "未知")
                        + (filename != null ? "（" + filename + "）" : "");
                // 错误码 10001：与前端 image-bg-remover.vue 的 _showErrorToast 映射一致
                // （文件格式不支持）。10001 已被 USER_NOT_FOUND 占用，按 task 要求
                // 直接用字面量，不引入新的 ResultCode 枚举项。
                throw new BusinessException(10001,
                        "不支持的图片格式：" + ctx + "，仅支持 JPG / PNG / BMP / GIF");
            }
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
                    // 工具 id 查一次即可（N 个文件共用），查不到不阻断（历史允许 tool_id 为空）
                    Long bgRemoverToolId = findToolId(TOOL_CODE_BG_REMOVER);
                    for (BatchFilePayload p : payloads) {
                        String fileName = p.getOriginalFilename();
                        // 先建「处理中」历史，保证产物一产出就有记录（与 OCR / doc-to-text 同口径）
                        Long historyId = historyService.createPendingHistory(
                                userId, bgRemoverToolId, null, TOOL_CODE_BG_REMOVER,
                                "上传图片：" + fileName);
                        long fileStart = System.currentTimeMillis();
                        try {
                            // 3 模式合一：交由 service 内部按 mode 分发
                            SegmentResult r = segmentationService.processImage(
                                    p.getContent(), modeFinal, bgColorFinal, bgFinal);
                            // 产物落库（批量任务来源：写 batchId 让 completed 端点能反查）
                            // fileIndex 用循环下标区分同批次多张产物
                            ToolOutputVO output = toolOutputService.saveBatchFileOutput(
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
                            // 历史输出内容存产物 URL（产物本体在 sys_tool_output，可按 batchId 回查）
                            historyService.completeHistory(historyId, output.getFileUrl(),
                                    (int) (System.currentTimeMillis() - fileStart));
                            success++;
                        } catch (Exception e) {
                            log.error("[image-bg-remover] 单文件失败 batchId={} file={} err={}",
                                    batchId, fileName, e.getMessage());
                            historyService.failHistory(historyId, e.getMessage());
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
     * 按 toolCode 查 {@code sys_aitools_tool.id}，供历史记录归类用。
     * <p>
     * 查不到返回 null 而不抛异常：历史表的 {@code tool_id} 列允许为空，
     * 工具数据缺失不应阻断用户真正要的「去背景」这件事。
     * 口径与 {@code NodeExecutor#workflowToolId} 保持一致。
     */
    private Long findToolId(String toolCode) {
        try {
            com.example.aitools.entity.AiTool tool = aiToolMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                            com.example.aitools.entity.AiTool>()
                            .eq(com.example.aitools.entity.AiTool::getToolCode, toolCode)
                            .last("LIMIT 1"));
            return tool == null ? null : tool.getId();
        } catch (Exception e) {
            log.warn("[image-bg-remover] 查询工具 id 失败 toolCode={}", toolCode, e);
            return null;
        }
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