package com.example.aitools.service;

import com.example.aitools.dto.DocToTextResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档转文本服务（工作流节点复用，非 AI 工具）。
 * <p>
 * 当前实现：复用 {@link com.example.aitools.service.document.DocumentParser} 抽取文档文字层。
 * 后续扩展：文字层为空 / 解析失败 → OCR → 多模态大模型兜底（本次不实现）。
 */
public interface DocToTextService {

    /**
     * 把文档解析为纯文本
     *
     * @param userId 当前用户 ID（用于写历史）
     * @param file   上传的文档（txt / pdf / docx）
     * @return 纯文本 + 解析方式
     */
    DocToTextResponse toText(Long userId, MultipartFile file);
}
