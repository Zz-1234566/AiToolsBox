package com.example.aitools.service.impl;

import com.example.aitools.ai.MinimaxClient;
import com.example.aitools.common.Constants;
import com.example.aitools.common.ResultCode;
import com.example.aitools.dto.BatchFilePayload;
import com.example.aitools.exception.BusinessException;
import com.example.aitools.service.AiFileReaderService;
import com.example.aitools.service.document.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * AI 文件解读服务实现（MiniMax M3 多模态）
 * <p>图片 → base64 发 M3；PDF → 转图片逐页发 M3；Word/TXT → 抽文本发 M3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFileReaderServiceImpl implements AiFileReaderService {

    private static final Set<String> IMAGE_EXTS = Constants.AI_FILE_READER_IMAGE_EXTS;
    private static final int PDF_DPI = 150; // PDF 转图片 DPI（平衡速度与清晰度）

    /**
     * PDF 最多渲染多少页（内存安全上限）。
     * <p>单页 150 DPI 的 A4 → BufferedImage 约 8.7MB，转 PNG 后约 0.3~2.5MB，
     * 且所有页的 PNG 会同时堆在内存里传给下游。
     * 不设上限时，一份几百页的 PDF 单请求就能吃掉几百 MB。
     * 与 {@code DocToTextServiceImpl.MAX_OCR_PAGES} 保持一致。
     */
    private static final int MAX_PDF_PAGES = 20;

    private final MinimaxClient minimaxClient;
    private final DocumentParser documentParser;

    @Override
    public String readFile(BatchFilePayload payload, String prompt) {
        String filename = payload.getOriginalFilename();
        String ext = extractExt(filename);
        String actualPrompt = (prompt == null || prompt.isBlank()) ? "请解读这个文件的内容" : prompt;

        return switch (ext) {
            case "pdf" -> readPdfAsImages(payload, actualPrompt);
            case "docx", "txt" -> readDocument(payload, actualPrompt);
            default -> {
                if (IMAGE_EXTS.contains(ext)) {
                    yield readImage(payload, actualPrompt);
                }
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                        "暂不支持该文件类型，仅支持 jpg/png/pdf/docx/txt");
            }
        };
    }

    private String readImage(BatchFilePayload payload, String prompt) {
        return minimaxClient.chatImage(prompt, payload.getContent());
    }

    private String readPdfAsImages(BatchFilePayload payload, String prompt) {
        List<byte[]> pageImages = renderPdfToImages(payload.getContent());
        if (pageImages.isEmpty()) {
            throw new BusinessException(ResultCode.DOC_PARSE_FAILED.getCode(), "PDF 解析失败，未能提取到有效页面");
        }
        return minimaxClient.chatImages(prompt, pageImages);
    }

    private String readDocument(BatchFilePayload payload, String prompt) {
        String text = documentParser.parse(payload.toMultipartFile());
        if (text == null || text.isBlank()) {
            throw new BusinessException(ResultCode.DOC_EMPTY.getCode(), "文档内容为空或解析失败");
        }
        String systemPrompt = "你是一个专业的文档解读助手。请根据提供的文档内容，用结构化的方式解读和总结。";
        return minimaxClient.chatText(systemPrompt, prompt + "\n\n文档内容：\n" + text);
    }

    /**
     * PDF 转图片列表（每页一张 PNG）
     *
     * <p><b>页数上限</b>：单页 150 DPI 的 A4 渲染成 BufferedImage 约 8.7MB
     * （1240×1754×4 字节），转成 PNG 后约 0.3~2.5MB，且这些 PNG 会**全部堆在 list 里**
     * 传给下游的多模态接口。没有上限时，一份几百页的 PDF 就能把堆吃掉几百 MB ——
     * 单个请求就可能把整个后端拖垮（并发几个必 OOM）。
     * <p>上限与 {@code DocToTextServiceImpl.MAX_OCR_PAGES} 保持一致（20 页），
     * 超出的部分跳过并告知用户，不静默截断。
     */
    private List<byte[]> renderPdfToImages(byte[] pdfBytes) {
        List<byte[]> pages = new ArrayList<>();
        int failedPages = 0;
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            int renderCount = Math.min(pageCount, MAX_PDF_PAGES);
            for (int i = 0; i < renderCount; i++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(i, PDF_DPI);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "PNG", baos);
                    pages.add(baos.toByteArray());
                } catch (Exception e) {
                    failedPages++;
                    // 传异常对象保留堆栈（原实现仅打页码，异常根因丢失）
                    log.warn("PDF 第 {} 页渲染失败，跳过", i + 1, e);
                }
            }
            // 超过上限：明确告知用户「只解读了前 N 页」，不能静默截断
            if (pageCount > MAX_PDF_PAGES) {
                log.warn("PDF 页数超过上限，只解读前 {} 页 total={}", MAX_PDF_PAGES, pageCount);
            }
            // 部分页失败会导致 AI 基于残缺内容作答，必须让用户知晓，不能静默忽略
            if (failedPages > 0) {
                log.warn("PDF 渲染存在失败页 failed={} total={}", failedPages, renderCount);
                if (pages.isEmpty()) {
                    throw new BusinessException(ResultCode.DOC_PARSE_FAILED.getCode(), ResultCode.DOC_PARSE_FAILED.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("PDF 加载失败", e);
            throw new BusinessException(ResultCode.DOC_PARSE_FAILED.getCode(), ResultCode.DOC_PARSE_FAILED.getMessage());
        }
        return pages;
    }

    private String extractExt(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
