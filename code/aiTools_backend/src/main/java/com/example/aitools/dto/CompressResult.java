package com.example.aitools.dto;

/**
 * 文件压缩结果。
 * <p>
 * 承载方式用 {@code byte[]} 而非临时文件路径，原因：
 * <ul>
 *   <li>压缩结果要直接交给第三方 AI 接口（腾讯云 ASR / OCR），
 *       与项目既有做法一致 —— {@code TranscribeService}、
 *       {@code AiFileReaderServiceImpl} 均以 {@code byte[]} 传 AI；</li>
 *   <li>压缩后的文件必然已降到限额内（当前最大 3.7MB），
 *       8 并发约 30MB 内存，量级可控；</li>
 *   <li>不产生临时文件，无需调用方负责清理，从根上消除泄漏点。</li>
 * </ul>
 */
public class CompressResult {

    /** 压缩后内容（未发生压缩时即原始内容） */
    private byte[] content;

    /** 处理后的文件名（沿用原名，调用方拼 MIME 时用） */
    private String filename;

    /** 原始字节数 */
    private long originalSize;

    /** 结果字节数 */
    private long resultSize;

    /** 实际迭代次数（0 = 未超限，直接原样返回） */
    private int iterations;

    /** 是否发生了压缩 */
    private boolean compressed;

    /** 文件类别：image / audio / unsupported */
    private String category;

    public CompressResult() {}

    public CompressResult(byte[] content, String filename, long originalSize,
                          int iterations, boolean compressed, String category) {
        this.content = content;
        this.filename = filename;
        this.originalSize = originalSize;
        this.resultSize = content == null ? 0 : content.length;
        this.iterations = iterations;
        this.compressed = compressed;
        this.category = category;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
        this.resultSize = content == null ? 0 : content.length;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(long originalSize) {
        this.originalSize = originalSize;
    }

    public long getResultSize() {
        return resultSize;
    }

    public void setResultSize(long resultSize) {
        this.resultSize = resultSize;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
