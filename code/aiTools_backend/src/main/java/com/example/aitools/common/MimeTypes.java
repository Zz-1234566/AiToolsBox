package com.example.aitools.common;

/**
 * MIME 类型常量。
 *
 * <p><b>为什么单独成类</b>：MIME 字符串在「上传校验 / 魔数嗅探 / 扩展名映射 / 产物落库」
 * 四条链路上都要用。此前散落成裸字面量（跨 5 个文件 17 处），
 * 改一个类型名要全局搜索替换，漏一处就会出现「校验放行的格式和实际存的不一致」。
 *
 * <p>本类只放**确实被复用**的类型；一次性的、语义唯一的 MIME 仍留在使用处。
 */
public final class MimeTypes {

    private MimeTypes() {
        // 常量类，禁止实例化
    }

    // ==================== 图片 ====================

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String BMP = "image/bmp";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";

    // ==================== 文档 ====================

    public static final String PDF = "application/pdf";
    public static final String PLAIN_TEXT = "text/plain";

    // ==================== 通用二进制 ====================

    /**
     * 二进制流兜底类型。
     * <p>用于「不知道具体类型」的场景：魔数嗅探失败、扩展名无法识别、默认 contentType。
     */
    public static final String OCTET_STREAM = "application/octet-stream";
}
