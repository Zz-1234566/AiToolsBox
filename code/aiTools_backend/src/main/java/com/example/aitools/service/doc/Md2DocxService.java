package com.example.aitools.service.doc;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown → DOCX（POI 实现）。
 * <p>
 * 排版按《广州商学院本科毕业论文（设计）通用模板》实测参数：
 * 正文宋体 12pt / 西文 Times New Roman / 行距 1.5 / 首行缩进 2 字符；
 * 标题宋体加粗（一级 14pt 居中、二三级 12pt）。
 * <p>
 * 为什么不用 pandoc：部署环境不一定有 pandoc / LibreOffice，
 * 纯 Java 生成零外部依赖，样式也能在代码里精确控制。
 */
@Slf4j
@Service
public class Md2DocxService {

    private static final String FONT_CN = "宋体";
    private static final String FONT_EN = "Times New Roman";
    private static final String FONT_MONO = "Consolas";
    private static final int BODY_SIZE = 12;      // 小四
    private static final int H1_SIZE = 14;
    private static final int H2_SIZE = 12;
    private static final int LINE_15 = 360;       // 1.5 倍行距（240 = 单倍）
    private static final int INDENT_2CH = 480;    // 2 字符 @12pt = 24pt = 480 twips

    private static final Pattern H = Pattern.compile("^(#{1,4})\\s+(.*)$");
    private static final Pattern UL = Pattern.compile("^\\s*[-*]\\s+(.*)$");
    private static final Pattern OL = Pattern.compile("^\\s*(\\d+)\\.\\s+(.*)$");
    private static final Pattern TABLE_SEP = Pattern.compile("^\\s*\\|[-:\\s|]+\\|\\s*$");

    /** 渲染整篇 markdown，返回 docx 字节 */
    public byte[] render(String markdown, String title) {
        try (XWPFDocument doc = new XWPFDocument()) {
            String[] lines = markdown.split("\r?\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                // 代码块
                if (t.startsWith("```")) {
                    List<String> code = new ArrayList<>();
                    i++;
                    while (i < lines.length && !lines[i].trim().startsWith("```")) {
                        code.add(lines[i]);
                        i++;
                    }
                    addCode(doc, String.join("\n", code));
                    continue;
                }
                // 表格
                if (t.startsWith("|") && i + 1 < lines.length && TABLE_SEP.matcher(lines[i + 1]).matches()) {
                    List<String[]> rows = new ArrayList<>();
                    rows.add(splitRow(t));
                    i += 2;
                    while (i < lines.length && lines[i].trim().startsWith("|")) {
                        rows.add(splitRow(lines[i].trim()));
                        i++;
                    }
                    i--;
                    addTable(doc, rows);
                    continue;
                }
                // 标题
                Matcher mh = H.matcher(t);
                if (mh.matches()) {
                    int level = mh.group(1).length();
                    addHeading(doc, mh.group(2), level);
                    continue;
                }
                // 无序列表
                Matcher mu = UL.matcher(line);
                if (mu.matches()) {
                    addParagraph(doc, "• " + mu.group(1), 0, false, false);
                    continue;
                }
                // 有序列表
                Matcher mo = OL.matcher(line);
                if (mo.matches()) {
                    addParagraph(doc, mo.group(1) + ". " + mo.group(2), 0, false, false);
                    continue;
                }
                // 正文
                addParagraph(doc, t, INDENT_2CH, false, false);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成文档失败：" + e.getMessage(), e);
        }
    }

    private String[] splitRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.split("\\|", -1);
    }

    private void style(XWPFRun run, String font, int size, boolean bold) {
        run.setFontFamily(font);
        run.setFontFamily(font, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setBold(bold);
    }

    private void addHeading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(level == 1 ? 240 : 120);
        p.setSpacingAfter(level == 1 ? 120 : 60);
        p.setSpacingBetween(1.5);
        if (level == 1) {
            p.setAlignment(ParagraphAlignment.CENTER);
        }
        XWPFRun r = p.createRun();
        style(r, FONT_CN, level == 1 ? H1_SIZE : H2_SIZE, true);
        r.setText(text);
    }

    private void addParagraph(XWPFDocument doc, String text, int indent, boolean bold, boolean mono) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(120);
        p.setSpacingBetween(1.5);
        if (indent > 0) {
            p.setIndentationFirstLine(indent);
        }
        XWPFRun r = p.createRun();
        if (mono) {
            style(r, FONT_MONO, BODY_SIZE - 1, false);
        } else {
            style(r, FONT_CN, BODY_SIZE, bold);
        }
        r.setText(text);
    }

    private void addCode(XWPFDocument doc, String code) {
        for (String line : code.split("\n", -1)) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingAfter(0);
            p.setSpacingBetween(1.0);
            XWPFRun r = p.createRun();
            style(r, FONT_MONO, BODY_SIZE - 1, false);
            r.setText(line.isEmpty() ? " " : line);
        }
        XWPFParagraph tail = doc.createParagraph();
        tail.setSpacingAfter(60);
    }

    private void addTable(XWPFDocument doc, List<String[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.get(0).length;
        XWPFTable table = doc.createTable(rows.size(), cols);
        table.setWidth("100%");
        for (int ri = 0; ri < rows.size(); ri++) {
            String[] row = rows.get(ri);
            for (int ci = 0; ci < cols; ci++) {
                XWPFTableCell cell = table.getRow(ri).getCell(ci);
                if (cell == null) {
                    continue;
                }
                cell.removeParagraph(0);
                XWPFParagraph p = cell.addParagraph();
                p.setSpacingAfter(40);
                p.setSpacingBefore(40);
                XWPFRun r = p.createRun();
                String v = ci < row.length ? row[ci].trim() : "";
                style(r, FONT_CN, BODY_SIZE - 1, ri == 0);
                r.setText(v.replace("**", ""));
                if (ri == 0) {
                    cell.setColor("EFEFEF");
                    p.setAlignment(ParagraphAlignment.CENTER);
                }
            }
        }
        XWPFParagraph after = doc.createParagraph();
        after.setSpacingAfter(60);
    }
}
