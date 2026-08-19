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
}
