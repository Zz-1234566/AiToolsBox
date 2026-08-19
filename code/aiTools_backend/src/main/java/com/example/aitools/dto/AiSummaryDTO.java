package com.example.aitools.dto;

import lombok.Data;

/**
 * 文档重点提取请求参数（multipart 表单）
 * 上传文件（MultipartFile）不入 DTO，由 Controller 用 @RequestParam("file") 单独接收；
 * 表单的 promptId/promptFormat/promptGenerate 由 Spring MVC 从表单字段绑定到本 DTO。
 */
@Data
public class AiSummaryDTO {

    /** 系统提示词ID（可选） */
    private Long promptId;

    /** 用户自定义格式提示词（可选，format） */
    private String promptFormat;

    /** 用户自定义生成内容提示词（可选，generate） */
    private String promptGenerate;
}
