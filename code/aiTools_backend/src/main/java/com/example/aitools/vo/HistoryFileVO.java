package com.example.aitools.vo;

import lombok.Data;

/**
 * 历史记录文件出参
 */
@Data
public class HistoryFileVO {

    private Long id;

    private String fileId;

    private String fileName;

    private String fileUrl;

    private String fileType;

    /** 文件角色：1输入 2输出 */
    private Integer role;
}
