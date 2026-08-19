package com.example.aitools.service.document;

import cn.hutool.core.io.IoUtil;
import com.example.aitools.common.ResultCode;
import com.example.aitools.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

/**
 * 文档解析器：将上传的 txt/pdf/docx 文档解析为纯文本，供 AI 提炼重点
 */
@Slf4j
@Component
public class DocumentParser {

    /** 提取文本上限（字符数），防止超长文档撑爆 prompt */
    private static final int MAX_TEXT_LENGTH = 20000;

    /**
     * 解析上传文档为纯文本
     * @param file 上传的文档
     * @return 提取的文本（超长截断）
     * @throws BusinessException 不支持的类型抛"暂不支持该文件类型"
     */
    public String parse(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        String text;
        try {
            switch (ext) {
                case "txt":
                    text = parseTxt(file);
                    break;
                case "pdf":
                    text = parsePdf(file);
                    break;
                case "docx":
                    text = parseDocx(file);
                    break;
                default:
                    throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "暂不支持该文件类型，仅支持 txt/pdf/docx");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Document parse failed: {}", originalFilename, e);
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "文档解析失败，请检查文件是否损坏");
        }
        // 截断
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        return text;
    }

    private String parseTxt(MultipartFile file) throws IOException {
        return IoUtil.readUtf8(file.getInputStream());
    }

    private String parsePdf(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseDocx(MultipartFile file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) sb.append(t).append("\n");
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String t = cell.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append("\t");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String extractExtension(String originalFilename) {
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
