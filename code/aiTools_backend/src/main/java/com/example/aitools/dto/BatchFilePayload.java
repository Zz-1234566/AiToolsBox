package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 批量任务文件载体（用于把 MultipartFile 在 HTTP 同步返回前固化到内存，避开 Tomcat 临时文件被清理问题）
 * - 在 controller 层用 {@link #from(MultipartFile)} 把每个文件读成 byte[]
 * - 传到异步线程 / service 后，service 用 {@link #getContent()} / {@link #getOriginalFilename()} 即可
 * - 单文件路径（非批量）不涉及，不影响
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchFilePayload {

    /** 文件字节（已读到内存，临时文件被 Tomcat 删也无所谓） */
    private byte[] content;

    /** 原始文件名（保留扩展名用于后缀判断、显示） */
    private String originalFilename;

    /**
     * 工厂方法：把 MultipartFile 转成内存载体（这里同步读 bytes，必须在 controller 同步返回前完成）
     */
    public static BatchFilePayload from(MultipartFile file) throws IOException {
        if (file == null) {
            return new BatchFilePayload(new byte[0], "未命名");
        }
        byte[] bytes = (file.isEmpty() && file.getSize() == 0) ? new byte[0] : file.getBytes();
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = "未命名";
        }
        return new BatchFilePayload(bytes, name);
    }

    /**
     * 把自己包成内存版 MultipartFile（基于 ByteArrayInputStream），供 DocumentParser 等仍接收 MultipartFile 的 API 使用
     * 避免引入 spring-test（test scope）里的 MockMultipartFile
     */
    public MultipartFile toMultipartFile() {
        final byte[] bytes = (content == null) ? new byte[0] : content;
        final String name = (originalFilename == null || originalFilename.isBlank()) ? "未命名" : originalFilename;
        return new MultipartFile() {
            @Override public String getName() { return name; }
            @Override public String getOriginalFilename() { return name; }
            @Override public String getContentType() { return "application/octet-stream"; }
            @Override public boolean isEmpty() { return bytes.length == 0; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() throws IOException { return bytes; }
            @Override public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(bytes); }
            @Override public void transferTo(File dest) throws IOException, IllegalStateException {
                throw new UnsupportedOperationException("InMemoryMultipartFile 不支持 transferTo");
            }
        };
    }
}
