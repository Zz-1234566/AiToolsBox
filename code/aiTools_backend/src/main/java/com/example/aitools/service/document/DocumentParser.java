package com.example.aitools.service.document;

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

    /** 提取文本上限（字符数），防止超长文档撑爆 prompt。取值统一由 Constants.AI_INPUT_MAX_LENGTH 管理 */
    private static final int MAX_TEXT_LENGTH = com.example.aitools.common.Constants.AI_INPUT_MAX_LENGTH;

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
                    throw new BusinessException(ResultCode.DOC_UNSUPPORTED.getCode(), "暂不支持该文件类型，仅支持 txt/pdf/docx");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Document parse failed: {}", originalFilename, e);
            throw new BusinessException(ResultCode.DOC_PARSE_FAILED.getCode(), ResultCode.DOC_PARSE_FAILED.getMessage());
        }
        // 截断：按 codepoint 切，避免辅助平面字符（emoji / 罕用汉字）被切到一半。
        // 注意 String.length() 是 UTF-16 长度（emoji 占 2 个 char），与 codepoint 计数口径不同，
        // 必须先用 codePointCount 判断是否超限，再用 offsetByCodePoints 求 char 下标，
        // 否则含 emoji 的文本会越界抛 IndexOutOfBoundsException。
        if (text.codePointCount(0, text.length()) > MAX_TEXT_LENGTH) {
            int end = text.offsetByCodePoints(0, MAX_TEXT_LENGTH);
            text = text.substring(0, end);
        }
        // 未提取到任何文字（如无文字层 PDF）：直接拒绝，避免把空内容喂给 AI 产生误导性输出
        if (text.isBlank()) {
            throw new BusinessException(ResultCode.DOC_EMPTY.getCode(), ResultCode.DOC_EMPTY.getMessage());
        }
        return text;
    }

    private String parseTxt(MultipartFile file) throws IOException {
        // P2-B3: 自动检测编码，避免 GBK/GB18030 用户上传后整段乱码
        // 实现策略：先按 UTF-8 读，发现替换字符 U+FFFD 比例超过阈值（>5%）则回退到 GBK
        // 不引入 Tika 等新依赖（项目规范：未经允许不引入新依赖）
        byte[] bytes = file.getBytes();
        String utf8 = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        int replacementCount = countReplacementChars(utf8);
        if (replacementCount > utf8.length() * 0.05) {
            // UTF-8 解析失败率高，回退 GBK
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        }
        return utf8;
    }

    /** 统计 U+FFFD 替换字符数（UTF-8 解码失败时会产生） */
    private int countReplacementChars(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\uFFFD') count++;
        }
        return count;
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
