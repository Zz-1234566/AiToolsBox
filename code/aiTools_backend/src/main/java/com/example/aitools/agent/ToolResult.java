package com.example.aitools.agent;

import lombok.Data;

import java.util.List;

/**
 * 单次工具调用的结果。
 * <p>
 * <b>为什么不是纯字符串</b>：工具产物有两类——
 * 文本（进模型上下文）与图片（需要展示/下载，喂回上下文只会是一串 URL）。
 * 用一个对象把两者分开，模型侧只读 {@link #text}，前端额外拿到 {@link #imageUrls}。
 */
@Data
public class ToolResult {

    /** 给模型看的文本结果（含产物 URL 描述） */
    private String text;

    /** 图片产物 URL（可为空） */
    private List<String> imageUrls;

    public static ToolResult text(String text) {
        ToolResult r = new ToolResult();
        r.setText(text);
        r.setImageUrls(List.of());
        return r;
    }

    public static ToolResult image(String text, List<String> urls) {
        ToolResult r = new ToolResult();
        r.setText(text);
        r.setImageUrls(urls == null ? List.of() : urls);
        return r;
    }

    /** 是否带图片产物 */
    public boolean hasImages() {
        return imageUrls != null && !imageUrls.isEmpty();
    }
}
