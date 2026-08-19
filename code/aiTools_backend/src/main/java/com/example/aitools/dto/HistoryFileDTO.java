package com.example.aitools.dto;

import lombok.Data;

/**
 * 历史记录文件入参
 */
@Data
public class HistoryFileDTO {

    private String fileId;

    private String fileName;

    private String fileUrl;

    private String fileType;

    /** 文件角色：1输入 2输出 */
    private Integer role;
}
