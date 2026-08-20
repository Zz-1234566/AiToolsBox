package com.example.aitools.service.impl;

import com.example.aitools.ai.MinimaxClient;
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

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final int PDF_DPI = 150; // PDF 转图片 DPI（平衡速度与清晰度）

    private final MinimaxClient minimaxClient;
    private final DocumentParser documentParser;

    @Override
    public String readFile(BatchFilePayload payload, String prompt) {
        String filename = payload.getOriginalFilename();
        String ext = extractExt(filename);
        String actualPrompt = (prompt == null || prompt.isBlank()) ? "请解读这个文件的内容" : prompt;

        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "webp" -> readImage(payload, actualPrompt);
            case "pdf" -> readPdfAsImages(payload, actualPrompt);
            case "docx", "txt" -> readDocument(payload, actualPrompt);
            default -> throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "暂不支持该文件类型，仅支持 jpg/png/pdf/docx/txt");
        };
    }

    private String readImage(BatchFilePayload payload, String prompt) {
        return minimaxClient.chatImage(prompt, payload.getContent());
    }

    private String readPdfAsImages(BatchFilePayload payload, String prompt) {
        List<byte[]> pageImages = renderPdfToImages(payload.getContent());
        if (pageImages.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "PDF 解析失败，未能提取到有效页面");
        }
        return minimaxClient.chatImages(prompt, pageImages);
    }

    private String readDocument(BatchFilePayload payload, String prompt) {
        String text = documentParser.parse(payload.toMultipartFile());
        if (text == null || text.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档内容为空或解析失败");
        }
        String systemPrompt = "你是一个专业的文档解读助手。请根据提供的文档内容，用结构化的方式解读和总结。";
        return minimaxClient.chatText(systemPrompt, prompt + "\n\n文档内容：\n" + text);
    }

    /**
     * PDF 转图片列表（每页一张 PNG）
     */
    private List<byte[]> renderPdfToImages(byte[] pdfBytes) {
        List<byte[]> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(i, PDF_DPI);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "PNG", baos);
                    pages.add(baos.toByteArray());
                } catch (Exception e) {
                    log.warn("PDF 第 {} 页渲染失败，跳过", i + 1);
                    // 单页失败不影响其他页
                }
            }
        } catch (IOException e) {
            log.error("PDF 加载失败", e);
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "PDF 加载失败：" + e.getMessage());
        }
        return pages;
    }

    private String extractExt(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
