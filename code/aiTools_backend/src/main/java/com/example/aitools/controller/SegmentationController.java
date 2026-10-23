package com.example.aitools.controller;

import com.example.aitools.common.Constants;
import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.config.ExecutorConfig;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.dto.BatchUploadResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.BatchTaskService;
import com.example.aitools.service.BatchUploadService;
import com.example.aitools.service.SegmentationBatchService;
import com.example.aitools.utils.AuthUtil;
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
import com.example.aitools.common.ToolCodes;
import com.example.aitools.common.MimeTypes;

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

    /** image-bg-remover 允许的 MIME 白名单（与 SegmentationService 一致）。
     *  前端 image-bg-remover.vue 的 ALLOWED_EXTS 必须与此表保持同步。 */
    private static final List<String> ALLOWED_IMAGE_TYPES =
            Arrays.asList(MimeTypes.JPEG, MimeTypes.PNG, MimeTypes.BMP, MimeTypes.GIF);

    /**
     * image-bg-remover 允许的文件扩展名白名单（兜底：客户端未传 contentType 时使用）。
     * <p>取自 {@link Constants#SEGMENTATION_IMAGE_EXTS} —— 前端 image-bg-remover.vue 的
     * ALLOWED_EXTS 需与之保持同步。
     */
    private static final Set<String> ALLOWED_IMAGE_EXTS = Constants.SEGMENTATION_IMAGE_EXTS;

    private final BatchTaskService batchTaskService;
    private final BatchUploadService batchUploadService;
    private final AuthUtil authUtil;

    /** 批量编排（逐文件推理 / 落产物 / 记历史 / 汇总计数）—— 业务逻辑全在 Service */
    private final SegmentationBatchService segmentationBatchService;

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
                // 错误码用 ResultCode.IMAGE_UNSUPPORTED(30002) ——「图片格式不支持」，
                // 语义唯一且与前端 errorCode.js 的 ERROR_MESSAGE 一致。
                // ⚠️ 不要再用字面量 10001：那是 USER_NOT_FOUND（「用户不存在」），
                //    前后端 errorCode.js / ResultCode 里含义冲突，兜底文案会显示成「用户不存在」。
                throw new BusinessException(ResultCode.IMAGE_UNSUPPORTED.getCode(),
                        "不支持的图片格式：" + ctx + "，仅支持 JPG / PNG / BMP / GIF");
            }
        }
        if (files.size() > Constants.BATCH_MAX_FILE_COUNT) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "单次最多上传 " + Constants.BATCH_MAX_FILE_COUNT + " 张图片");
        }
        // 批量总大小校验（与 multipart 的 max-request-size 一致，见 Constants.BATCH_MAX_TOTAL_SIZE）
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > Constants.BATCH_MAX_TOTAL_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(),
                    Constants.fileSizeLimitMessage("批量文件总大小", Constants.BATCH_MAX_TOTAL_SIZE));
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
            throw new BusinessException(ResultCode.FILE_TOO_LARGE.getCode(),
                    Constants.fileSizeLimitMessage("背景图", Constants.MAX_FILE_SIZE));
        }

        // 2) 同步建任务（HTTP 必须立即返回 batchId）
        String batchId = batchTaskService.createTask(userId, ToolCodes.IMAGE_BG_REMOVER, files.size());
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
            // 流式落盘，**不把整批字节读进堆**（批量总大小上限 200MB，
            // 全量进内存的话并发几个请求就能打满堆，把整个后端拖垮）
            payloads = batchUploadService.toPayloads(files);
        } catch (BusinessException e) {
            log.error("[image-bg-remover] 读取上传文件失败，任务置为失败 batchId={}", batchId, e);
            segmentationBatchService.failBatch(batchId, files.size());
            throw e;
        }

        final byte[] bgFinal = bgBytes;
        final String bgColorFinal = backgroundColor;
        final String modeFinal = modeNorm;

        // 4) 异步跑批（逐文件推理 / 落产物 / 记历史 / 汇总计数 全部在 Service 里，Controller 不写业务）
        try {
            batchExecutor.execute(() -> {
                try {
                    segmentationBatchService.processImageBatch(
                            userId, payloads, modeFinal, bgColorFinal, bgFinal, batchId);
                } finally {
                    // 清理批量临时文件：必须在 finally 里，异常时也要清
                    batchUploadService.cleanup(payloads);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("[image-bg-remover] 批量任务被拒（线程池饱和）batchId={}", batchId, e);
            segmentationBatchService.failBatch(batchId, files.size());
            // 任务没跑起来 → lambda 里的 finally 不会执行，临时文件要在这里清
            batchUploadService.cleanup(payloads);
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
    /**
     * 兜底：刚建的批量任务若启动失败，必须置为完成态（success=0/fail=count），
     * 否则永久停留在 PENDING，前端已拿到的 batchId 会一直轮询等不到终态。
     */}