package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档转文本响应。
 * <p>
 * 本次仅实现「文本层解析」（PDFBox / POI 抽取文字层）。
 * {@code method} 字段为后续扩展预留：当文本层解析拿不到内容（如扫描型 PDF）时，
 * 依次降级为 ocr（腾讯云 OCR）→ multimodal（多模态大模型），前端据 method 展示实际解析方式。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocToTextResponse {

    /** 解析出的纯文本 */
    private String text;

    /**
     * 解析方式：
     * <ul>
     *   <li>{@code text-layer} 文本层解析（本次唯一实现，纯解析不调 AI）</li>
     *   <li>{@code ocr}        腾讯云 OCR（后续扩展）</li>
     *   <li>{@code multimodal} 多模态大模型兜底（后续扩展）</li>
     * </ul>
     */
    private String method;

    /** 原文件名 */
    private String fileName;

    /** 字符数 */
    private Integer charCount;
}
