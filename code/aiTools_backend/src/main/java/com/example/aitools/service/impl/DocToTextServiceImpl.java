package com.example.aitools.service.impl;

import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.DocToTextResponse;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.AiPromptTemplateService;
import com.example.aitools.service.DocToTextService;
import com.example.aitools.service.HistoryService;
import com.example.aitools.service.OcrService;
import com.example.aitools.service.document.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * 文档转文本实现（纯解析，不调用 AI 大模型）。
 * <p>
 * 按文件类型分流，三条通道：
 * <ol>
 *   <li><b>图片</b>（png/jpg/jpeg/bmp/gif/webp）→ 直接走腾讯云 OCR；</li>
 *   <li><b>txt / docx</b> → 编码自适应 / POI 抽文字层；</li>
 *   <li><b>pdf</b> → 先抽文字层；<b>抽不到（扫描件）则逐页渲染成图走 OCR</b>。</li>
 * </ol>
 * 实际使用的通道写入 {@link DocToTextResponse#getMethod()}（text-layer / ocr），
 * 前端据此展示解析方式，用户能明确知道这份文件是怎么被读出来的。
 * <p>
 * <b>为什么不用多模态大模型兜底</b>：多模态识别一张图的成本远高于 OCR，
 * 且本工具定位是「便宜的文本提取层」，大模型加工交给下游节点
 * （会议纪要 / 重点提取 / 工作总结 / 周报生成）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocToTextServiceImpl implements DocToTextService {

    /** 解析方式：文本层（PDFBox / POI / 编码自适应） */
    private static final String METHOD_TEXT_LAYER = "text-layer";

    /** 解析方式：腾讯云 OCR（图片直传 / 扫描件逐页渲染） */
    private static final String METHOD_OCR = "ocr";

    /** 工具编码（与 sys_aitools_tool.tool_code 一致，用于写历史） */
    private static final String TOOL_CODE_DOC_TO_TEXT = "doc-to-text";

    /** 图片扩展名（直送 OCR，不做文字层解析） */
    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "webp");

    /** 文档扩展名（走文字层解析） */
    private static final Set<String> DOC_EXTS = Set.of("txt", "docx", "pdf");

    /** 扫描件 PDF 最多渲染页数（避免上百页 PDF 触发 OCR 费用失控） */
    private static final int MAX_OCR_PAGES = 20;

    /** PDF 渲染 DPI（与 AiFileReaderServiceImpl 保持一致） */
    private static final float PDF_RENDER_DPI = 150f;

    private final DocumentParser documentParser;
    private final OcrService ocrService;
    private final HistoryService historyService;
    private final AiPromptTemplateService aiPromptTemplateService;

    @Override
    public DocToTextResponse toText(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.DOC_EMPTY.getCode(), "请选择要转换的文件");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(fileName);
        long start = System.currentTimeMillis();

        Long toolId = aiPromptTemplateService.findToolIdByCode(TOOL_CODE_DOC_TO_TEXT);
        Long historyId = historyService.createPendingHistory(
                userId, toolId, null, TOOL_CODE_DOC_TO_TEXT, "上传文件：" + fileName);

        try {
            String text;
            String method;

            if (IMAGE_EXTS.contains(ext)) {
                // 图片：没有文字层可言，直接 OCR
                text = ocrService.recognizeText(file);
                method = METHOD_OCR;
            } else if ("pdf".equals(ext)) {
                // PDF：先抽文字层；扫描件（抽不到）再逐页渲染走 OCR
                text = parsePdfTextLayer(file);
                if (text == null || text.isBlank()) {
                    log.info("[doc-to-text] PDF 无文字层，按扫描件逐页渲染后走 OCR file={}", fileName);
                    text = ocrService.recognizePdfPages(file, MAX_OCR_PAGES, PDF_RENDER_DPI);
                    method = METHOD_OCR;
                } else {
                    method = METHOD_TEXT_LAYER;
                }
            } else if (DOC_EXTS.contains(ext)) {
                text = documentParser.parse(file);
                method = METHOD_TEXT_LAYER;
            } else {
                throw new BusinessException(ResultCode.DOC_UNSUPPORTED.getCode(),
                        "暂不支持该文件类型，支持：图片（PNG/JPG/JPEG/BMP）、PDF、Word（DOCX）、TXT");
            }

            if (text == null || text.isBlank()) {
                throw new BusinessException(ResultCode.DOC_EMPTY.getCode(),
                        "未能从该文件中提取到文字。若为扫描件请确认页面清晰，或换用更清晰的图片");
            }
            int duration = (int) (System.currentTimeMillis() - start);
            historyService.completeHistory(historyId, text, duration);
            log.info("[doc-to-text] userId={} file={} ext={} method={} chars={} cost={}ms",
                    userId, fileName, ext, method, text.length(), duration);
            return new DocToTextResponse(text, method, fileName, text.length());
        } catch (Exception e) {
            historyService.failHistory(historyId, e.getMessage());
            throw e;
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 抽取 PDF 文字层。
     * <p>
     * {@link DocumentParser#parse} 对「抽不到任何文字」会抛
     * {@code DOC_EMPTY}（其原意是防止空内容喂给 AI），但对扫描件 PDF 而言
     * 这正是「需要降级 OCR」的信号，故在此捕获并返回 {@code null}，
     * 由调用方决定是否走 OCR 兜底。
     * <p>
     * 其他异常（加密 / 文件损坏）继续向上抛，让用户知道是文件问题而非「无文字」。
     */
    private String parsePdfTextLayer(MultipartFile file) {
        try {
            return documentParser.parse(file);
        } catch (BusinessException e) {
            if (e.getCode() == ResultCode.DOC_EMPTY.getCode()) {
                log.info("[doc-to-text] PDF 文字层为空（可能为扫描件），转交 OCR 兜底判断");
                return null;
            }
            throw e;
        }
    }
}
