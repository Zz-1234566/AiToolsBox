package com.example.aitools.service;

import com.example.aitools.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务抽象。
 * 默认实现 {@link com.example.aitools.service.impl.LocalFileStorageService} 存本地，
 * 配置 cos.enabled=true 时切换为 {@link com.example.aitools.service.impl.CosFileStorageService}（腾讯云 COS）。
 * 存储按前缀（目录）区分用途：avatar/ 头像、ai-image/ AI 图片、ai-bg/ 去背景等。
 */
public interface FileStorageService {

    /**
     * 保存上传的文件
     *
     * @param file   上传的文件（类型与大小已由 Controller 校验）
     * @param prefix 存储前缀目录，如 avatar、ai-image、ai-bg；空串表示根目录
     * @return 文件ID、访问URL、原始文件名
     */
    FileUploadResponse store(MultipartFile file, String prefix);

    /**
     * 归一化前缀：去除首尾空白与斜杠，空串表示根目录
     */
    static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String p = prefix.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
