package com.example.aitools.controller;

import com.example.aitools.common.Constants;
import com.example.aitools.common.Result;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.SegmentResult;
import com.example.aitools.entity.ToolOutput;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.FileStorageService;
import com.example.aitools.service.SegmentationService;
import com.example.aitools.service.ToolOutputService;
import com.example.aitools.vo.ToolOutputVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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

    private final SegmentationService segmentationService;
    private final ToolOutputService toolOutputService;

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
}