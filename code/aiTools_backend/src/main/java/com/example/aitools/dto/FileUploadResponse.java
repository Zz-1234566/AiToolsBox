package com.example.aitools.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse implements Serializable {

    /** 文件ID（UUID） */
    private String fileId;

    /** 文件访问URL */
    private String fileUrl;

    /** 原始文件名 */
    private String fileName;

    /**
     * COS 对象 key（私有文件的签名 URL 有有效期，过期后可用此 key 重新签发）。
     * <p>
     * 本地存储实现返回相对 URL，此字段为空。
     */
    private String cosKey;
}
