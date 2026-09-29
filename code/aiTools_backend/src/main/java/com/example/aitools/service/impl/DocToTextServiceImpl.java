package com.example.aitools.service.impl;

import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.DocToTextResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.DocToTextService;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.HistoryService;
import com.example.aitools.service.document.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档转文本实现（纯解析，不调用 AI）。
 * <p>
 * 复用 {@link DocumentParser}（PDFBox 抽 PDF 文字层 / POI 抽 docx / 编码自适应的 txt），
 * 与「文档重点提取」工具共用同一套解析能力，不重复实现。
 * <p>
 * 后续扩展位（本次不实现）：解析结果为空时依次尝试 OCR → 多模态兜底，
 * 并把实际方式写入 {@link DocToTextResponse#getMethod()}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocToTextServiceImpl implements DocToTextService {

    /** 解析方式：文本层（与 DTO 注释保持一致） */
    private static final String METHOD_TEXT_LAYER = "text-layer";

    /** 工具编码（与 sys_aitools_tool.tool_code 一致，用于写历史） */
    private static final String TOOL_CODE_DOC_TO_TEXT = "doc-to-text";

    private final DocumentParser documentParser;
    private final HistoryService historyService;
    private final AiPromptTemplateService aiPromptTemplateService;

    @Override
    public DocToTextResponse toText(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择要转换的文档");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        long start = System.currentTimeMillis();

        Long toolId = aiPromptTemplateService.findToolIdByCode(TOOL_CODE_DOC_TO_TEXT);
        Long historyId = historyService.createPendingHistory(
                userId, toolId, null, TOOL_CODE_DOC_TO_TEXT, "上传文档：" + fileName);

        try {
            String text = documentParser.parse(file);
            if (text == null || text.isBlank()) {
                // 后续扩展位：此处应降级 OCR → 多模态兜底；本次直接报错
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "未能从文档中提取到文字（可能是扫描件）。请改用【智能识别】工具上传图片");
            }
            int duration = (int) (System.currentTimeMillis() - start);
            historyService.completeHistory(historyId, text, duration);
            log.info("[doc-to-text] userId={} file={} chars={} cost={}ms",
                    userId, fileName, text.length(), duration);
            return new DocToTextResponse(text, METHOD_TEXT_LAYER, fileName, text.length());
        } catch (Exception e) {
            historyService.failHistory(historyId, e.getMessage());
            throw e;
        }
    }
}
