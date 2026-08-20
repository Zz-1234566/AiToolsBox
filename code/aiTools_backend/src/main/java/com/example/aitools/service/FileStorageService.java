package com.example.aitools.service;

import com.example.aitools.common.Constants;
import com.example.aitools.dto.FileUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

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

    /**
     * 提取文件扩展名（含点，如 ".jpg"），统一小写。找不到点返回空串。
     * default 方法：3 个调用方共用，避免在 Controller / Local / Cos 各自重复。
     */
    static String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 把 service 返回的 pathOrUrl 转成前端可用的完整 URL。
     * - 已包含 scheme(http/https) → 原样返回（如 COS 已返回完整地址）
     * - 否则按 request 拼 baseUrl（本地存储返回的 /uploads/xxx 走这里）
     */
    static String resolveFullUrl(String pathOrUrl, HttpServletRequest request) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return pathOrUrl;
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        if (request == null) return pathOrUrl; // 兜底
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                    ? "" : ":" + request.getServerPort());
        return baseUrl + pathOrUrl;
    }

    /**
     * 通用用户文件区路由：file/{userId}/ 强制登录后拼 userId 子目录。
     * 其他前缀保持不变。
     */
    static String resolveUserFilePrefix(String normalizedPrefix, Long userId) {
        if (Constants.FILE_PREFIX_USER_FILE.equals(normalizedPrefix)) {
            return Constants.FILE_PREFIX_USER_FILE + "/" + userId;
        }
        return normalizedPrefix;
    }
}
